package com.travel.modules.guidebook.companion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.travel.modules.guidebook.dto.SuggestionOperation;

public record SuggestionDraft(
        String type,
        String dedupeKey,
        String reason,
        String action,
        List<SuggestionOperation> operations,
        List<Map<String, Object>> alternatives,
        Map<String, Object> evidence,
        LocalDate triggerDate) {
}
