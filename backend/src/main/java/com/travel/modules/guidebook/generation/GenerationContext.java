package com.travel.modules.guidebook.generation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.data.TravelDataSnapshot;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.knowledge.KnowledgeSnippet;

import lombok.Data;

/** 一次生成任务的可变上下文，步骤之间只通过它传递数据。 */
@Data
public class GenerationContext {

    private final Long guidebookId;
    private final Long userId;
    private final Long sessionId;
    private final IntentDto intent;
    private final Map<String, Object> profileSnapshot;

    private List<String> keywords = new ArrayList<>();
    private List<KnowledgeSnippet> knowledge = new ArrayList<>();
    private TravelDataSnapshot travelData;
    private List<com.travel.ai.AiMessage> promptMessages = new ArrayList<>();

    private String title;
    private List<GuidebookDayDto> days;
    private BudgetBreakdownDto budgetBreakdown;
    private List<String> packingList;
    private List<String> culturalTips;
    private List<String> pitfallTips;

    private final Set<String> degradationReasons = new LinkedHashSet<>();

    public void addReason(String reason) {
        degradationReasons.add(reason);
    }

    public boolean degraded() {
        return !degradationReasons.isEmpty();
    }
}
