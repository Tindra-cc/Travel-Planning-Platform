package com.travel.modules.guidebook.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.SharedGuidebookSnapshot;
import com.travel.modules.guidebook.entity.Guidebook;

import lombok.RequiredArgsConstructor;

/** 把攻略深拷贝为对外快照；只做白名单字段，内置意图/画像等一律不复制。 */
@Component
@RequiredArgsConstructor
public class ShareSnapshotBuilder {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ObjectMapper objectMapper;

    public SharedGuidebookSnapshot build(Guidebook guidebook) {
        SharedGuidebookSnapshot snapshot = new SharedGuidebookSnapshot();
        List<GuidebookDayDto> days = copy(guidebook.getDays(),
                new TypeReference<List<GuidebookDayDto>>() { });
        sortDaysByTime(days);
        snapshot.setDays(days);
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

    /** 快照要和详情页一致地按开始时间展示，避免公开页出现时间倒序。 */
    private void sortDaysByTime(List<GuidebookDayDto> days) {
        if (days == null) {
            return;
        }
        for (GuidebookDayDto day : days) {
            if (day == null || day.getNodes() == null || day.getNodes().size() < 2) {
                continue;
            }
            List<GuidebookNodeDto> sorted = new ArrayList<>(day.getNodes());
            sorted.sort(Comparator.comparing(
                    node -> node == null || node.getTimeSlot() == null
                            ? null
                            : parseTime(node.getTimeSlot().getStart()),
                    Comparator.nullsLast(Comparator.naturalOrder())));
            day.setNodes(sorted);
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value, TIME_FORMAT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private <T> T copy(T value, TypeReference<T> type) {
        return value == null ? null : objectMapper.convertValue(value, type);
    }
}
