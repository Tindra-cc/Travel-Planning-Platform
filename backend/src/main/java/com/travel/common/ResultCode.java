package com.travel.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 统一业务响应码，定义见接口文档 §1.6。
 * code 为业务码（成功 0），httpStatus 为响应使用的 HTTP 状态。
 */
@Getter
public enum ResultCode {
    SUCCESS(0, "ok", HttpStatus.OK),
    PARAM_ERROR(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "未登录或 token 失效", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40101, "token 过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "无权限（含用户层不足）", HttpStatus.FORBIDDEN),
    NOT_OWNER(40301, "非资源所有者", HttpStatus.FORBIDDEN),
    ACCOUNT_LOCKED(42300, "账号已锁定", HttpStatus.LOCKED),
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT(40900, "状态冲突", HttpStatus.CONFLICT),
    UNPROCESSABLE(42200, "业务规则不满足", HttpStatus.UNPROCESSABLE_ENTITY),
    TOO_MANY_REQUEST(42900, "触发限流", HttpStatus.TOO_MANY_REQUESTS),
    FAIL(50000, "服务端错误", HttpStatus.INTERNAL_SERVER_ERROR),
    DEPENDENCY_UNAVAILABLE(50300, "依赖服务不可用", HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
