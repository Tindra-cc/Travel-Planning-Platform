package com.travel.common;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private  Integer code;
    private  String message;
    private  String requestId;
    private  T data;

    public static <T> Result<T> success(T data){
        return Result.<T>builder()
                .code(ResultCode.SUCCESS.getCode())
                .message(ResultCode.SUCCESS.getMessage())
                .requestId(UUID.randomUUID().toString())
                .data(data)
                .build();
    }

    public static <T> Result<T> success(){
        return  success(null);
    }

    public static <T> Result<T> fail(ResultCode code){
        return Result.<T>builder()
                .code(code.getCode())
                .message(code.getMessage())
                .requestId(UUID.randomUUID().toString())
                .data(null)
                .build();
    }

    public static <T> Result<T> fail(int code, String message){
        return Result.<T>builder()
                .code(code)
                .message(message)
                .requestId(UUID.randomUUID().toString())
                .data(null)
                .build();
    }
}
