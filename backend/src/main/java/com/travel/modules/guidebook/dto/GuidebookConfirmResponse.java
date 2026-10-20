package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * §7.7 确认攻略响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuidebookConfirmResponse {

    private Long guidebookId;
    private String status;
}
