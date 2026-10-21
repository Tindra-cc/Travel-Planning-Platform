package com.travel.modules.expert.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class AuthorizeResponse {

    private String authorizationId;
    /** 明文令牌，只在创建响应中出现一次；库中仅存哈希。 */
    private String confirmToken;
    private LocalDateTime expiresAt;
    private List<String> scope;
}
