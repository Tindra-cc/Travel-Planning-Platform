package com.travel.modules.guidebook.generation;

import java.util.List;
import java.util.Map;

import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;

import lombok.Data;

@Data
public class GeneratedContent {
    private String title;
    private List<GuidebookDayDto> days;
    private BudgetBreakdownDto budgetBreakdown;
    private List<String> packingList;
    private List<String> culturalTips;
    private List<String> pitfallTips;

    /** 仅调整场景返回：一句话变更摘要 */
    private String changeSummary;
    /** 仅调整场景返回：结构化变更列表，仅用于展示，不参与落库 */
    private List<Map<String, Object>> changes;
}
