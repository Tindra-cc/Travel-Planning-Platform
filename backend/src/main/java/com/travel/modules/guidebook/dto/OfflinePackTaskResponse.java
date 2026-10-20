package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** §9.1 生成离线包响应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflinePackTaskResponse {

    private Long taskId;
    private String status;
}
