package com.travel.modules.auth.service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.BizException;
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

    private static final String CODE_KEY_PREFIX = "verify:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_QUOTA = 3;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final Environment environment;

    /** 发送验证码（本地联调：dev 环境回显 code） */
    public VerifyCodeResponse sendVerifyCode(VerifyCodeRequest req) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        redis.opsForValue().set(codeKey(req.getScene(), req.getAccount()), code, CODE_TTL);
        log.info("验证码 [{}] {} -> {}", req.getScene(), req.getAccount(), code);
        boolean dev = environment.matchesProfiles("dev");
        return new VerifyCodeResponse(true, dev ? code : null);
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
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getAccount, req.getAccount()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已禁用");
        }
        StpUtil.login(user.getId());
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
                user.getProfileCompleted(), user.getDailyGenerateQuota(), user.getDailyGenerateUsed());
    }

    private String codeKey(String scene, String account) {
        return CODE_KEY_PREFIX + scene + ":" + account;
    }
}
