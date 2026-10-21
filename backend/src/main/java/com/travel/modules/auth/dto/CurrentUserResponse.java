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
    /** 仅用于前端展示入口，实时门禁仍由 L3AccessPolicy 在服务端判断。 */
    private Boolean expertEligible;
}
