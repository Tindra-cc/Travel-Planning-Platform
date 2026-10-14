package com.travel.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "账号类型不能为空")
    @Pattern(regexp = "mobile|email", message = "账号类型仅支持 mobile/email")
    private String accountType;

    @NotBlank(message = "验证码不能为空")
    private String verifyCode;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码长度至少 8 位")
    private String password;
}
