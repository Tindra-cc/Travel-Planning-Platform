package com.travel.modules.guidebook.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.ReferenceDay;
import com.travel.modules.guidebook.dto.ReferenceGuidebook;
import com.travel.modules.guidebook.dto.ReferenceNode;
import com.travel.modules.guidebook.dto.SharedGuidebookSnapshot;

/** 把分享快照裁剪成提示词参考骨架：保留节奏与地点组合，丢弃来源与内部字段。 */
@Component
public class ShareReferenceBuilder {

    private static final int MAX_DAYS = 8;
    private static final int MAX_NODES_PER_DAY = 8;
    private static final int MAX_TIPS = 5;

    public ReferenceGuidebook build(SharedGuidebookSnapshot snapshot) {
        ReferenceGuidebook ref = new ReferenceGuidebook();
        if (snapshot == null || snapshot.getDays() == null) {
            return ref;
        }

        List<ReferenceDay> days = new ArrayList<>();
        for (GuidebookDayDto day : snapshot.getDays()) {
            if (day == null || days.size() >= MAX_DAYS) {
                continue;
            }
            List<ReferenceNode> nodes = new ArrayList<>();
            if (day.getNodes() != null) {
                for (GuidebookNodeDto node : day.getNodes()) {
                    if (node == null || nodes.size() >= MAX_NODES_PER_DAY) {
                        continue;
                    }
                    nodes.add(new ReferenceNode(
                            node.getTimeSlot() == null ? null : node.getTimeSlot().getStart(),
                            node.getType(),
                            placeName(node)));
                }
            }
            days.add(new ReferenceDay(day.getDay(), nodes));
        }
        ref.setDays(days);
        ref.setBudgetSummary(budgetSummary(snapshot.getBudgetBreakdown()));
        ref.setCulturalTips(limit(snapshot.getCulturalTips()));
        ref.setPitfallTips(limit(snapshot.getPitfallTips()));
        return ref;
    }

    private String placeName(GuidebookNodeDto node) {
        if (node.getPlace() != null && node.getPlace().getName() != null
                && !node.getPlace().getName().isBlank()) {
            return node.getPlace().getName();
        }
        return switch (node.getType() == null ? "" : node.getType()) {
            case "meal" -> "用餐";
            case "transport" -> "交通";
            case "stay" -> "住宿";
            case "activity" -> "活动";
            default -> "自由活动";
        };
    }

    private String budgetSummary(BudgetBreakdownDto budget) {
        if (budget == null || budget.getTotal() == null) {
            return null;
        }
        return budget.getTotal().getMin() + "-" + budget.getTotal().getMax()
                + " " + (budget.getCurrency() == null ? "CNY" : budget.getCurrency());
    }

    private List<String> limit(List<String> tips) {
        if (tips == null) {
            return List.of();
        }
        return tips.stream()
                .filter(t -> t != null && !t.isBlank())
                .limit(MAX_TIPS)
                .toList();
    }
}
