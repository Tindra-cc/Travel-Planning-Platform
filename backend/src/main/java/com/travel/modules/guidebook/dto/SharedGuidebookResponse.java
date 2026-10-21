package com.travel.modules.guidebook.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/** FR-12 §12.2 公开只读响应；不包含 ownerId 等内部字段。 */
@Data
public class SharedGuidebookResponse {
    private String shareCode;
    private String title;
    private Integer guidebookVersion;
    private LocalDateTime sharedAt;
    private LocalDateTime expiresAt;
    private List<GuidebookDayDto> days;
    private BudgetBreakdownDto budgetBreakdown;
    private List<String> packingList;
    private List<String> culturalTips;
    private List<String> pitfallTips;
    private String disclaimer;
    private Boolean degradedMode;
    private LocalDateTime travelDataAt;
}
