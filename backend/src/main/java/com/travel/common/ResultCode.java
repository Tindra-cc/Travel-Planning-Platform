package com.travel.common;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200,"操作成功"),
    FAIL(500,"系统内部错误"),
    PARAM_ERROR(400,"参数校验失败"),
    UNAUTHORIZED(401,"未登录或token失效"),
    FORBIDDEN(403,"权限不足"),
    NOT_FOUND(404,"资源不存在"),
    TOO_MANY_REQUEST(429,"请求过于频繁");

    private final int code;
    private final String message;

    ResultCode(int code, String message){
        this.code = code;
        this.message = message;
    }
}
