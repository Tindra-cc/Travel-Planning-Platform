package com.travel.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyCodeRequest {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "账号类型不能为空")
    @Pattern(regexp = "mobile|email", message = "账号类型仅支持 mobile/email")
    private String accountType;

    @NotBlank(message = "场景不能为空")
    @Pattern(regexp = "register|login|reset-password", message = "场景仅支持 register/login/reset-password")
    private String scene;
}
