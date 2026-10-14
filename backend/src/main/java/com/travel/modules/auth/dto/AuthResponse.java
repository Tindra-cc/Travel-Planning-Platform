package com.travel.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {

    private Long userId;
    private String token;
    private String userLevel;
    private Boolean profileCompleted;
}
