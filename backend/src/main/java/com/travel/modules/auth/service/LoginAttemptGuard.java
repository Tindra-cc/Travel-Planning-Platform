package com.travel.modules.auth.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.travel.common.BizException;
import com.travel.common.ResultCode;

import lombok.RequiredArgsConstructor;

/**
 * 登录失败锁定与验证码发送限流。全部依赖 Redis 自动过期，无需落库。
 * 计数按账号字符串进行（不论账号是否存在），使「被锁定」不成为账号存在的信号。
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

    /** 连续失败达到该次数即锁定。 */
    public static final int MAX_FAILED = 3;
    /** 锁定时长（分钟），计数窗口同长。 */
    public static final int LOCK_MINUTES = 30;

    private static final Duration LOCK_DURATION = Duration.ofMinutes(LOCK_MINUTES);
    private static final Duration CODE_MIN_INTERVAL = Duration.ofSeconds(60);
    private static final int CODE_DAILY_LIMIT = 10;

    private static final String LOGIN_LOCK = "travel:auth:login:lock:";
    private static final String LOGIN_FAIL = "travel:auth:login:fail:";
    private static final String CODE_SEND = "travel:auth:code:send:";
    private static final String CODE_DAY = "travel:auth:code:day:";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;

    public boolean isLocked(String account) {
        return Boolean.TRUE.equals(redis.hasKey(LOGIN_LOCK + account));
    }

    /** 剩余锁定秒数，未锁定时为 0。 */
    public long remainingLockSeconds(String account) {
        Long ttl = redis.getExpire(LOGIN_LOCK + account);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    /** 记一次失败尝试，返回累计次数；达到阈值时上锁并清空计数。 */
    public long recordFailure(String account) {
        String failKey = LOGIN_FAIL + account;
        Long fails = redis.opsForValue().increment(failKey);
        redis.expire(failKey, LOCK_DURATION);
        long count = fails == null ? 1 : fails;
        if (count >= MAX_FAILED) {
            redis.opsForValue().set(LOGIN_LOCK + account, String.valueOf(count), LOCK_DURATION);
            redis.delete(failKey);
        }
        return count;
    }

    public void clearFailures(String account) {
        redis.delete(LOGIN_FAIL + account);
    }

    /** 验证码发送限流：60 秒节流 + 每日上限，不通过抛 42900。 */
    public void checkSendAllowed(String account, String scene) {
        if (Boolean.TRUE.equals(redis.hasKey(CODE_SEND + scene + ":" + account))) {
            throw new BizException(ResultCode.TOO_MANY_REQUEST, "发送过于频繁，请 60 秒后再试");
        }
        String sent = redis.opsForValue().get(dayKey(account));
        int sentToday;
        try {
            sentToday = sent == null ? 0 : Integer.parseInt(sent);
        } catch (NumberFormatException e) {
            // db0 可能与其它应用共用，遇到异形值直接清理，避免一次脏数据导致接口 500。
            redis.delete(dayKey(account));
            sentToday = 0;
        }
        if (sentToday >= CODE_DAILY_LIMIT) {
            throw new BizException(ResultCode.TOO_MANY_REQUEST,
                    "今日验证码发送次数已达上限（" + CODE_DAILY_LIMIT + " 次），请明天再试");
        }
    }

    public void markSent(String account, String scene) {
        redis.opsForValue().set(CODE_SEND + scene + ":" + account, "1", CODE_MIN_INTERVAL);
        String dayKey = dayKey(account);
        Long count = redis.opsForValue().increment(dayKey);
        if (count != null && count == 1L) {
            redis.expire(dayKey, untilMidnight());
        }
    }

    private String dayKey(String account) {
        return CODE_DAY + account + ":" + LocalDate.now().format(DAY);
    }

    private Duration untilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay());
    }
}
