package com.travel.common;

import java.util.Optional;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理，HTTP 状态按 {@link ResultCode} 映射（接口文档 §1.6）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static ResponseEntity<Result<?>> build(ResultCode code) {
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code));
    }

    private static ResponseEntity<Result<?>> build(ResultCode code, String message) {
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code.getCode(), message));
    }

    // 业务自定义异常
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<?>> handleBizException(BizException e) {
        log.warn("业务异常:{}", e.getMessage());
        return build(e.getResultCode(), e.getMessage());
    }

    // Sa-Token 未登录 / token 失效
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<?>> handleNotLogin(NotLoginException e) {
        log.warn("未登录:{}", e.getMessage());
        ResultCode code = NotLoginException.TOKEN_TIMEOUT.equals(e.getType())
                ? ResultCode.TOKEN_EXPIRED
                : ResultCode.UNAUTHORIZED;
        return build(code);
    }

    // Sa-Token 权限 / 角色不足
    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public ResponseEntity<Result<?>> handleNoAuth(Exception e) {
        log.warn("无权限:{}", e.getMessage());
        return build(ResultCode.FORBIDDEN);
    }

    // @RequestBody 参数校验
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleValidException(MethodArgumentNotValidException e) {
        String msg = Optional.ofNullable(e.getBindingResult().getFieldError())
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("参数异常:{}", msg);
        return build(ResultCode.PARAM_ERROR, msg);
    }

    // @RequestParam / @PathVariable 参数校验
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<?>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("参数校验异常:{}", e.getMessage());
        return build(ResultCode.PARAM_ERROR, e.getMessage());
    }

    // 404 接口不存在
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<?>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("404接口不存在:{}", e.getRequestURL());
        return build(ResultCode.NOT_FOUND);
    }

    // 404 静态资源/路径不存在（Spring Boot 3）
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("404资源不存在:{}", e.getResourcePath());
        return build(ResultCode.NOT_FOUND);
    }

    // 兜底
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        log.error("系统未知错误", e);
        return build(ResultCode.FAIL);
    }
}
