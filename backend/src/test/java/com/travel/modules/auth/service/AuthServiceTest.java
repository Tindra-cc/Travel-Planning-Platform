package com.travel.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.auth.dto.AuthResponse;
import com.travel.modules.auth.dto.CurrentUserResponse;
import com.travel.modules.auth.dto.LoginRequest;
import com.travel.modules.auth.dto.RegisterRequest;
import com.travel.modules.auth.dto.VerifyCodeRequest;
import com.travel.modules.auth.dto.VerifyCodeResponse;
import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;

import cn.dev33.satoken.stp.StpUtil;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String ACCOUNT = "a@b.com";

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private Environment environment;
    @Mock
    private MailService mailService;
    @Mock
    private LoginAttemptGuard loginGuard;

    private AuthService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(values);
        service = new AuthService(userMapper, passwordEncoder, redis, environment,
                mailService, loginGuard);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void devWithoutMailConfigEchoesCode() {
        when(environment.matchesProfiles("dev")).thenReturn(true);
        when(mailService.configured()).thenReturn(false);

        VerifyCodeResponse response = service.sendVerifyCode(verifyCode("register"));

        assertThat(response.isSent()).isTrue();
        assertThat(response.getCode()).matches("\\d{6}");
        verify(values).set(eq("travel:auth:verify:register:" + ACCOUNT), anyString(),
                eq(Duration.ofMinutes(5)));
        verify(loginGuard).markSent(ACCOUNT, "register");
    }

    @Test
    void productionWithoutMailChannelReturns503() {
        when(environment.matchesProfiles("dev")).thenReturn(false);
        when(mailService.configured()).thenReturn(false);

        assertBizCode(() -> service.sendVerifyCode(verifyCode("register")),
                ResultCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void registerRejectsWrongOrExpiredCode() {
        when(values.get("travel:auth:verify:register:" + ACCOUNT)).thenReturn(null);

        assertBizCode(() -> service.register(register("123456")), ResultCode.PARAM_ERROR);
    }

    @Test
    void registerRejectsDuplicateAccount() {
        when(values.get("travel:auth:verify:register:" + ACCOUNT)).thenReturn("123456");
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertBizCode(() -> service.register(register("123456")), ResultCode.CONFLICT);
    }

    @Test
    void registerCreatesL1UserAndReturnsToken() {
        when(values.get("travel:auth:verify:register:" + ACCOUNT)).thenReturn("123456");
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(9L);
            return 1;
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenValue).thenReturn("token-9");

            AuthResponse response = service.register(register("123456"));

            assertThat(response.getUserId()).isEqualTo(9L);
            assertThat(response.getToken()).isEqualTo("token-9");
            assertThat(response.getUserLevel()).isEqualTo("L1");
            assertThat(response.getProfileCompleted()).isFalse();
        }

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getUserLevel()).isEqualTo("L1");
        assertThat(saved.getDailyGenerateQuota()).isEqualTo(3);
        assertThat(saved.getNickname()).startsWith("旅行者");
        verify(redis).delete("travel:auth:verify:register:" + ACCOUNT);
    }

    @Test
    void loginRejectsLockedAccount() {
        when(loginGuard.isLocked(ACCOUNT)).thenReturn(true);
        when(loginGuard.remainingLockSeconds(ACCOUNT)).thenReturn(120L);

        assertThatThrownBy(() -> service.login(login("wrong")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_LOCKED);
                    assertThat(e.getMessage()).contains("2 分钟");
                });
    }

    @Test
    void loginWithWrongPasswordRecordsFailure() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user(1));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(loginGuard.recordFailure(ACCOUNT)).thenReturn(1L);

        assertBizCode(() -> service.login(login("wrong")), ResultCode.UNAUTHORIZED);
        verify(loginGuard).recordFailure(ACCOUNT);
    }

    @Test
    void thirdWrongPasswordLocksAccount() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user(1));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(loginGuard.recordFailure(ACCOUNT)).thenReturn(3L);

        assertBizCode(() -> service.login(login("wrong")), ResultCode.ACCOUNT_LOCKED);
    }

    @Test
    void disabledUserIsRejectedAfterPasswordMatch() {
        User user = user(0);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("password1", "hash")).thenReturn(true);

        assertBizCode(() -> service.login(login("password1")), ResultCode.FORBIDDEN);
        verify(loginGuard).clearFailures(ACCOUNT);
    }

    @Test
    void loginSuccessClearsFailuresAndReturnsToken() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user(1));
        when(passwordEncoder.matches("password1", "hash")).thenReturn(true);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenValue).thenReturn("token-1");

            AuthResponse response = service.login(login("password1"));

            assertThat(response.getToken()).isEqualTo("token-1");
            assertThat(response.getUserId()).isEqualTo(1L);
        }
        verify(loginGuard).clearFailures(ACCOUNT);
    }

    @Test
    void meRejectsMissingUser() {
        UserContext.set(new LoginUser(1L, ACCOUNT));
        when(userMapper.selectById(1L)).thenReturn(null);

        assertBizCode(service::me, ResultCode.NOT_FOUND);
    }

    @Test
    void meReturnsQuotaAndLevel() {
        UserContext.set(new LoginUser(1L, ACCOUNT));
        when(userMapper.selectById(1L)).thenReturn(user(1));

        CurrentUserResponse me = service.me();

        assertThat(me.getUserId()).isEqualTo(1L);
        assertThat(me.getUserLevel()).isEqualTo("L1");
        assertThat(me.getDailyGenerateQuota()).isEqualTo(3);
    }

    private User user(int status) {
        User user = new User();
        user.setId(1L);
        user.setAccount(ACCOUNT);
        user.setAccountType("email");
        user.setPasswordHash("hash");
        user.setNickname("旅行者");
        user.setUserLevel("L1");
        user.setStatus(status);
        user.setDailyGenerateQuota(3);
        user.setDailyGenerateUsed(0);
        user.setProfileCompleted(false);
        return user;
    }

    private LoginRequest login(String password) {
        LoginRequest req = new LoginRequest();
        req.setAccount(ACCOUNT);
        req.setPassword(password);
        return req;
    }

    private RegisterRequest register(String code) {
        RegisterRequest req = new RegisterRequest();
        req.setAccount(ACCOUNT);
        req.setAccountType("email");
        req.setVerifyCode(code);
        req.setPassword("password1");
        return req;
    }

    private VerifyCodeRequest verifyCode(String scene) {
        VerifyCodeRequest req = new VerifyCodeRequest();
        req.setAccount(ACCOUNT);
        req.setAccountType("email");
        req.setScene(scene);
        return req;
    }

    private void assertBizCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
