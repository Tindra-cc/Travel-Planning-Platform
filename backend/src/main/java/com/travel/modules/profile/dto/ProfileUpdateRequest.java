package com.travel.modules.profile.dto;

import lombok.Data;

/**
 * 部分更新：只处理非 null 字段。
 */
@Data
public class ProfileUpdateRequest {
    private BasicProfile basic;
    private AdvancedProfile advanced;
}
