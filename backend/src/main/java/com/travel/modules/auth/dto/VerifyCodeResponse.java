package com.travel.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerifyCodeResponse {

    private boolean sent;

    /** 仅 dev 环境回显，便于联调；生产为 null */
    private String code;
}
