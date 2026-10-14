package com.travel.common;

import java.util.Optional;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 捕获业务自定义异常
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.warn("业务异常:{}", e.getMessage());
        return Result.fail(e.getResultCode());
    }

    // @RequestBody 参数异常校验
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidException(MethodArgumentNotValidException e) {
        String msg = Optional.ofNullable(e.getBindingResult().getFieldError())
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        log.warn("参数异常:{}", msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    // @RequestParam 参数校验异常
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("参数校验异常:{}", e.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    // 兜底捕获所有未知异常
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统未知错误", e);
        return Result.fail(ResultCode.FAIL);
    }

    // 捕获404接口不存在异常
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<?> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("404接口不存在:{}", e.getRequestURL());
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /**
     * SpringBoot3 静态资源/路径找不到 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("404资源不存在:{}", e.getResourcePath());
        return Result.fail(ResultCode.NOT_FOUND);
    }
}
