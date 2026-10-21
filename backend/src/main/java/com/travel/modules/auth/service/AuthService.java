package com.travel.modules.auth.service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.BizException;
import com.travel.common.ClientIpResolver;
import com.travel.common.L3AccessPolicy;
import com.travel.common.Masks;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.auth.dto.AuthResponse;
import com.travel.modules.auth.dto.CurrentUserResponse;
import com.travel.modules.auth.dto.LoginRequest;
import com.travel.modules.auth.dto.RegisterRequest;
import com.travel.modules.auth.dto.VerifyCodeRequest;
import com.travel.modules.auth.dto.VerifyCodeResponse;
import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CODE_KEY_PREFIX = "travel:auth:verify:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_QUOTA = 3;

    /** 账号不存在时用于等时匹配的占位哈希，避免通过响应耗时探测账号是否存在。 */
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("timing-equalizer");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final Environment environment;
    private final MailService mailService;
    private final LoginAttemptGuard loginGuard;
    private final L3AccessPolicy l3AccessPolicy;

    /** 发送验证码：邮箱走 SMTP；未配置邮箱时仅 dev 回显（生产不回显）。 */
    public VerifyCodeResponse sendVerifyCode(VerifyCodeRequest req) {
        loginGuard.checkSendAllowed(req.getAccount(), req.getScene());
        boolean dev = environment.matchesProfiles("dev");
        boolean byMail = "email".equals(req.getAccountType()) && mailService.configured();
        if (!byMail && !dev) {
            throw new BizException(ResultCode.DEPENDENCY_UNAVAILABLE, "验证码发送渠道未配置");
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        redis.opsForValue().set(codeKey(req.getScene(), req.getAccount()), code, CODE_TTL);
        loginGuard.markSent(req.getAccount(), req.getScene());

        if (byMail) {
            mailService.sendVerifyCode(req.getAccount(), code, req.getScene());
            return new VerifyCodeResponse(true, null);
        }
        log.warn("邮箱未配置，验证码回退为 dev 回显 account={}", Masks.account(req.getAccount()));
        return new VerifyCodeResponse(true, code);
    }

    public AuthResponse register(RegisterRequest req) {
        String key = codeKey("register", req.getAccount());
        String cached = redis.opsForValue().get(key);
        if (cached == null || !cached.equals(req.getVerifyCode())) {
            throw new BizException(ResultCode.PARAM_ERROR, "验证码错误或已过期");
        }
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getAccount, req.getAccount()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.CONFLICT, "账号已注册");
        }

        User user = new User();
        user.setAccount(req.getAccount());
        user.setAccountType(req.getAccountType());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname("旅行者" + req.getAccount().substring(Math.max(0, req.getAccount().length() - 4)));
        user.setUserLevel("L1");
        user.setStatus(1);
        user.setDailyGenerateQuota(DEFAULT_QUOTA);
        user.setDailyGenerateUsed(0);
        user.setProfileCompleted(false);
        userMapper.insert(user);

        redis.delete(key);
        StpUtil.login(user.getId());
        return new AuthResponse(user.getId(), StpUtil.getTokenValue(), user.getUserLevel(), false);
    }

    public AuthResponse login(LoginRequest req) {
        String account = req.getAccount();
        if (loginGuard.isLocked(account)) {
            long minutes = Math.max(1, (loginGuard.remainingLockSeconds(account) + 59) / 60);
            throw new BizException(ResultCode.ACCOUNT_LOCKED, "账号已锁定，请 " + minutes + " 分钟后重试");
        }

        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getAccount, account));
        boolean matched = user != null && passwordEncoder.matches(req.getPassword(), user.getPasswordHash());
        if (user == null) {
            // 账号不存在时也做一次 bcrypt，抹平与「密码错误」的耗时差
            passwordEncoder.matches(req.getPassword(), DUMMY_HASH);
        }

        if (!matched) {
            long fails = loginGuard.recordFailure(account);
            if (fails >= LoginAttemptGuard.MAX_FAILED) {
                log.warn("账号锁定 account={} ip={} 连续失败 {} 次，锁定 {} 分钟",
                        Masks.account(account), ClientIpResolver.resolve(), fails, LoginAttemptGuard.LOCK_MINUTES);
                throw new BizException(ResultCode.ACCOUNT_LOCKED,
                        "连续 " + LoginAttemptGuard.MAX_FAILED + " 次登录失败，账号已锁定 "
                                + LoginAttemptGuard.LOCK_MINUTES + " 分钟");
            }
            log.warn("登录失败 account={} ip={} fails={}/{}",
                    Masks.account(account), ClientIpResolver.resolve(), fails, LoginAttemptGuard.MAX_FAILED);
            throw new BizException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }

        loginGuard.clearFailures(account);
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已禁用");
        }
        StpUtil.login(user.getId());
        log.info("登录成功 account={} ip={}", Masks.account(account), ClientIpResolver.resolve());
        return new AuthResponse(user.getId(), StpUtil.getTokenValue(), user.getUserLevel(), user.getProfileCompleted());
    }

    public void logout() {
        StpUtil.logout();
    }

    public CurrentUserResponse me() {
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return new CurrentUserResponse(user.getId(), user.getNickname(), user.getUserLevel(),
                user.getProfileCompleted(), user.getDailyGenerateQuota(), user.getDailyGenerateUsed(),
                l3AccessPolicy.hasAccess(user.getId()));
    }

    private String codeKey(String scene, String account) {
        return CODE_KEY_PREFIX + scene + ":" + account;
    }
}
