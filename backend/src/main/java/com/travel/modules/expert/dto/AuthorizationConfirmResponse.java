package com.travel.modules.expert.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class AuthorizationConfirmResponse {

    private String authorizationId;
    private String clientId;
    private String status;
    private List<String> scope;
    private LocalDateTime grantedAt;
}
