package com.travel.modules.guidebook.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.SharedGuidebookSnapshot;
import com.travel.modules.guidebook.entity.Guidebook;

import lombok.RequiredArgsConstructor;

/** 把攻略深拷贝为对外快照；只做白名单字段，内置意图/画像等一律不复制。 */
@Component
@RequiredArgsConstructor
public class ShareSnapshotBuilder {

    private final ObjectMapper objectMapper;

    public SharedGuidebookSnapshot build(Guidebook guidebook) {
        SharedGuidebookSnapshot snapshot = new SharedGuidebookSnapshot();
        snapshot.setDays(copy(guidebook.getDays(), new TypeReference<List<GuidebookDayDto>>() { }));
        snapshot.setBudgetBreakdown(copy(guidebook.getBudgetBreakdown(),
                new TypeReference<BudgetBreakdownDto>() { }));
        snapshot.setPackingList(copy(guidebook.getPackingList(), new TypeReference<List<String>>() { }));
        snapshot.setCulturalTips(copy(guidebook.getCulturalTips(), new TypeReference<List<String>>() { }));
        snapshot.setPitfallTips(copy(guidebook.getPitfallTips(), new TypeReference<List<String>>() { }));
        snapshot.setDisclaimer(GuidebookService.DISCLAIMER);
        snapshot.setDegradedMode(guidebook.getDegradedMode());
        snapshot.setTravelDataAt(guidebook.getTravelDataAt());
        snapshot.setSharedAt(LocalDateTime.now());
        return snapshot;
    }

    private <T> T copy(T value, TypeReference<T> type) {
        return value == null ? null : objectMapper.convertValue(value, type);
    }
}
