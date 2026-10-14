package com.travel.common;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //捕获业务自定义异常
    @ExceptionHandler (BizException.class)
    public Result<?> handleBizException(BizException e){
        log.warn("业务异常:{}",e.getMessage());
        return Result.fail(e.getResultCode());
    }

    //@RequestBody 参数异常校验
    @ExceptionHandler (MethodArgumentNotValidException.class)
    public Result<?> handleValidException(MethodArgumentNotValidException e){
        String msg = e.getBindingResult().getFieldError().getDefaultMessage();
        log.warn("参数异常:{}",msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(),msg);
    }

    //@RequestParm 参数校验异常
    @ExceptionHandler (ConstraintViolationException.class)
    public Result<?> handleConstraintViolation(ConstraintViolationException e){
        log.warn("参数校验异常:{}",e.getMessage());
        return  Result.fail(ResultCode.PARAM_ERROR.getCode(),e.getMessage());
    }

    //兜底捕获所有未知异常
    @ExceptionHandler (Exception.class)
    public Result<?> handleException(Exception e){
        log.error("系统未知错误", e);
        return Result.fail(ResultCode.FAIL);
    }
}
