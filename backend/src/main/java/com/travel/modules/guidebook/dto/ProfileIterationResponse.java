package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileIterationResponse {

    /** 决策后的画像版本；拒绝时版本不变 */
    private Integer profileVersion;
}
