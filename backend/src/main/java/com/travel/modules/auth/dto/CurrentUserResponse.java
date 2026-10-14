package com.travel.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CurrentUserResponse {

    private Long userId;
    private String nickname;
    private String userLevel;
    private Boolean profileCompleted;
    private Integer dailyGenerateQuota;
    private Integer dailyGenerateUsed;
}
