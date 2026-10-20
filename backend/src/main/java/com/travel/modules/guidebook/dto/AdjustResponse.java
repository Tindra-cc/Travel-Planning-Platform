package com.travel.modules.guidebook.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * §7.5 对话式调整响应。
 * changes 来自模型，仅用于展示；冲突与版本以服务端结果为准。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdjustResponse {

    private Long guidebookId;
    private Integer version;
    private String changeSummary;
    private List<Map<String, Object>> changes;
    private List<NodeConflictDto> conflicts;
}
