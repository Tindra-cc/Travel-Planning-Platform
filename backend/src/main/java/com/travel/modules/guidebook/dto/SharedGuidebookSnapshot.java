package com.travel.modules.guidebook.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/** FR-12：分享冻结内容；不包含画像、用户、会话与生成内部字段。 */
@Data
public class SharedGuidebookSnapshot {
    private List<GuidebookDayDto> days;
    private BudgetBreakdownDto budgetBreakdown;
    private List<String> packingList;
    private List<String> culturalTips;
    private List<String> pitfallTips;
    private String disclaimer;
    private Boolean degradedMode;
    private LocalDateTime travelDataAt;
    private LocalDateTime sharedAt;
}
