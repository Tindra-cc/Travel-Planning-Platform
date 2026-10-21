package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.Data;

/** FR-12：分享再生成用的参考骨架，只进提示词，不落库。 */
@Data
public class ReferenceGuidebook {
    private String title;
    private List<ReferenceDay> days;
    private String budgetSummary;
    private List<String> culturalTips;
    private List<String> pitfallTips;
}
