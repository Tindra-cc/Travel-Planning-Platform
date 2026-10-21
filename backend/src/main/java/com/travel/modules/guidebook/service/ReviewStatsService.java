package com.travel.modules.guidebook.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.ReviewStatsDto;
import com.travel.modules.guidebook.edit.GuidebookNodeStatus;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookNodeStatusMapper;

import lombok.RequiredArgsConstructor;

/**
 * FR-11：计划（当前 days）× 现实（节点状态行）的只读聚合。
 * 只统计当前 days 里仍存在的节点，孤儿状态行直接丢弃。
 */
@Service
@RequiredArgsConstructor
public class ReviewStatsService {

    private final GuidebookNodeStatusMapper statusMapper;

    public ReviewStatsDto calculate(Guidebook guidebook) {
        Map<String, NodeRef> current = indexCurrentNodes(guidebook);

        ReviewStatsDto stats = new ReviewStatsDto();
        List<GuidebookNodeStatus> rows = statusMapper.selectList(
                Wrappers.<GuidebookNodeStatus>lambdaQuery()
                        .eq(GuidebookNodeStatus::getGuidebookId, guidebook.getId()));

        Set<String> counted = new HashSet<>();
        List<String> skippedNames = new ArrayList<>();
        Map<Integer, Integer> skippedByDay = new TreeMap<>();

        for (GuidebookNodeStatus row : rows) {
            NodeRef ref = current.get(row.getNodeId());
            if (ref == null || !counted.add(row.getNodeId()) || row.getStatus() == null) {
                continue;
            }
            switch (row.getStatus()) {
                case GuidebookNodeStatus.EXECUTED ->
                        stats.setExecutedCount(stats.getExecutedCount() + 1);
                case GuidebookNodeStatus.SKIPPED -> {
                    stats.setSkippedCount(stats.getSkippedCount() + 1);
                    if (ref.name() != null) {
                        skippedNames.add(ref.name());
                    }
                    if (ref.day() != null) {
                        skippedByDay.merge(ref.day(), 1, Integer::sum);
                    }
                }
                case GuidebookNodeStatus.RESCHEDULED ->
                        stats.setRescheduledCount(stats.getRescheduledCount() + 1);
                default -> {
                    // 未知状态忽略，不让脏数据污染统计
                }
            }
        }

        int total = current.size();
        int terminal = stats.getExecutedCount() + stats.getSkippedCount()
                + stats.getRescheduledCount();
        stats.setTotalCount(total);
        stats.setUntouchedCount(Math.max(0, total - terminal));
        if (total > 0) {
            stats.setSkipRate(Math.round(stats.getSkippedCount() * 10000.0 / total) / 10000.0);
        }
        stats.setSkippedNodeNames(List.copyOf(skippedNames));
        stats.setSkippedByDay(Map.copyOf(skippedByDay));
        return stats;
    }

    private Map<String, NodeRef> indexCurrentNodes(Guidebook guidebook) {
        Map<String, NodeRef> result = new HashMap<>();
        if (guidebook.getDays() == null) {
            return result;
        }
        for (GuidebookDayDto day : guidebook.getDays()) {
            if (day == null || day.getNodes() == null) {
                continue;
            }
            for (GuidebookNodeDto node : day.getNodes()) {
                if (node == null || node.getNodeId() == null) {
                    continue;
                }
                String name = node.getPlace() == null ? null : node.getPlace().getName();
                result.putIfAbsent(node.getNodeId(), new NodeRef(day.getDay(), name));
            }
        }
        return result;
    }

    private record NodeRef(Integer day, String name) {
    }
}
