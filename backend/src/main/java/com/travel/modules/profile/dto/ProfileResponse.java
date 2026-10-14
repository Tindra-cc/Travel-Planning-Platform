package com.travel.modules.profile.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ProfileResponse {
    private Long profileId;
    private Long userId;
    private BasicProfile basic;
    private AdvancedProfile advanced;
    private Integer version;
    private LocalDateTime updatedAt;
}
