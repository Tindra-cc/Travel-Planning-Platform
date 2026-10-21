package com.travel.modules.guidebook.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.travel.modules.guidebook.dto.FreshnessDiffItem;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.SharedGuidebookSnapshot;

/** 只基于分享快照判断哪些节点的实时数据需要刷新，不阻断再生成。 */
@Service
public class ShareFreshnessService {

    private static final int STALE_HOURS = 48;
    public static final String PENDING_REFRESH = "pending_refresh";

    public List<FreshnessDiffItem> diff(SharedGuidebookSnapshot snapshot, LocalDateTime now) {
        List<FreshnessDiffItem> result = new ArrayList<>();
        if (snapshot == null || snapshot.getDays() == null) {
            return result;
        }
        for (GuidebookDayDto day : snapshot.getDays()) {
            if (day == null || day.getNodes() == null) {
                continue;
            }
            int index = 0;
            for (GuidebookNodeDto node : day.getNodes()) {
                index++;
                if (node == null) {
                    continue;
                }
                String old = node.getDataFreshness();
                if (isStale(old, now)) {
                    String nodeId = node.getNodeId() == null
                            ? "d" + day.getDay() + "-" + index
                            : node.getNodeId();
                    result.add(new FreshnessDiffItem(nodeId, old, PENDING_REFRESH));
                }
            }
        }
        return result;
    }

    private boolean isStale(String value, LocalDateTime now) {
        if (value == null || value.isBlank()) {
            return true;
        }
        LocalDateTime fetchedAt = parse(value);
        return fetchedAt == null || fetchedAt.plusHours(STALE_HOURS).isBefore(now);
    }

    private LocalDateTime parse(String value) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // 兼容只有日期的历史数据
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
