package com.travel.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.travel.common.BizException;
import com.travel.common.ResultCode;

@ExtendWith(MockitoExtension.class)
class LoginAttemptGuardTest {

    private static final String ACCOUNT = "a@b.com";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    private LoginAttemptGuard guard;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(values);
        guard = new LoginAttemptGuard(redis);
    }

    @Test
    void locksAccountOnThirdFailureAndClearsFailureCounter() {
        when(values.increment("travel:auth:login:fail:" + ACCOUNT)).thenReturn(3L);

        long fails = guard.recordFailure(ACCOUNT);

        assertThat(fails).isEqualTo(3);
        verify(values).set("travel:auth:login:lock:" + ACCOUNT, "3",
                Duration.ofMinutes(LoginAttemptGuard.LOCK_MINUTES));
        verify(redis).delete("travel:auth:login:fail:" + ACCOUNT);
    }

    @Test
    void doesNotLockBeforeThreshold() {
        when(values.increment("travel:auth:login:fail:" + ACCOUNT)).thenReturn(2L);

        guard.recordFailure(ACCOUNT);

        verify(values, never()).set(eq("travel:auth:login:lock:" + ACCOUNT), any(),
                any(Duration.class));
        verify(redis).expire("travel:auth:login:fail:" + ACCOUNT,
                Duration.ofMinutes(LoginAttemptGuard.LOCK_MINUTES));
    }

    @Test
    void reportsLockStateAndRemainingSeconds() {
        when(redis.hasKey("travel:auth:login:lock:" + ACCOUNT)).thenReturn(true);
        when(redis.getExpire("travel:auth:login:lock:" + ACCOUNT)).thenReturn(125L);

        assertThat(guard.isLocked(ACCOUNT)).isTrue();
        assertThat(guard.remainingLockSeconds(ACCOUNT)).isEqualTo(125);
    }

    @Test
    void missingLockTtlIsZero() {
        when(redis.getExpire("travel:auth:login:lock:" + ACCOUNT)).thenReturn(-1L);
        assertThat(guard.remainingLockSeconds(ACCOUNT)).isZero();
    }

    @Test
    void clearsFailures() {
        guard.clearFailures(ACCOUNT);
        verify(redis).delete("travel:auth:login:fail:" + ACCOUNT);
    }

    @Test
    void throttlesRepeatedSendWithinSixtySeconds() {
        when(redis.hasKey("travel:auth:code:send:register:" + ACCOUNT)).thenReturn(true);

        assertBizCode(() -> guard.checkSendAllowed(ACCOUNT, "register"),
                ResultCode.TOO_MANY_REQUEST);
    }

    @Test
    void rejectsWhenDailySendLimitReached() {
        when(redis.hasKey("travel:auth:code:send:register:" + ACCOUNT)).thenReturn(false);
        when(values.get(dayKey())).thenReturn("10");

        assertBizCode(() -> guard.checkSendAllowed(ACCOUNT, "register"),
                ResultCode.TOO_MANY_REQUEST);
    }

    @Test
    void malformedDailyCounterIsCleanedInsteadOfThrowing() {
        when(redis.hasKey("travel:auth:code:send:register:" + ACCOUNT)).thenReturn(false);
        when(values.get(dayKey())).thenReturn("not-a-number");

        assertThatCode(() -> guard.checkSendAllowed(ACCOUNT, "register"))
                .doesNotThrowAnyException();
        verify(redis).delete(dayKey());
    }

    @Test
    void markSentWritesThrottleAndDailyCounter() {
        when(values.increment(dayKey())).thenReturn(1L);

        guard.markSent(ACCOUNT, "register");

        verify(values).set("travel:auth:code:send:register:" + ACCOUNT, "1",
                Duration.ofSeconds(60));
        verify(redis).expire(eq(dayKey()), any(Duration.class));
    }

    private String dayKey() {
        return "travel:auth:code:day:" + ACCOUNT + ":"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private void assertBizCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
