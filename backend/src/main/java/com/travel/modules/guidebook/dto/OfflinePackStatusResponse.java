package com.travel.modules.guidebook.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * §9.2 离线包状态。errorMessage 为契约扩展字段：
 * failed 时前端需要向用户说明原因，否则只能显示「失败」。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflinePackStatusResponse {

    private Long taskId;
    private String status;
    private String downloadUrl;
    private LocalDateTime expiresAt;
    private String errorMessage;
}
