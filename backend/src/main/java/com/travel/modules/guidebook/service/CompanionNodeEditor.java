package com.travel.modules.guidebook.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.SuggestionOperation;
import com.travel.modules.guidebook.dto.TimeSlotDto;

/**
 * 在内存中的 days 上应用 move_node / swap_node。纯函数：
 * 不访问数据库、不读用户上下文，所有边界在这里集中处理。
 */
public final class CompanionNodeEditor {

    private CompanionNodeEditor() {
    }

    public static void apply(List<GuidebookDayDto> days, SuggestionOperation operation) {
        if (operation == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "操作不能为空");
        }
        if (SuggestionOperation.MOVE_NODE.equals(operation.getOp())) {
            move(days, operation);
            return;
        }
        if (SuggestionOperation.SWAP_NODE.equals(operation.getOp())) {
            swap(days, operation);
            return;
        }
        throw new BizException(ResultCode.PARAM_ERROR, "不支持的操作：" + operation.getOp());
    }

    private static void move(List<GuidebookDayDto> days, SuggestionOperation operation) {
        GuidebookNodeDto node = remove(days, operation.getNodeId());
        GuidebookDayDto target = day(days, operation.getToDay());
        if (operation.getTimeSlot() != null) {
            node.setTimeSlot(copy(operation.getTimeSlot()));
        }
        nodesOf(target).add(node);
        sort(nodesOf(target));
    }

    private static void swap(List<GuidebookDayDto> days, SuggestionOperation operation) {
        if (!StringUtils.hasText(operation.getNodeB())) {
            throw new BizException(ResultCode.PARAM_ERROR, "交换操作缺少 nodeB");
        }
        if (operation.getNodeId().equals(operation.getNodeB())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不能与自身交换");
        }
        GuidebookNodeDto first = find(days, operation.getNodeId());
        GuidebookNodeDto second = find(days, operation.getNodeB());
        GuidebookDayDto dayOfFirst = dayOf(days, first);
        GuidebookDayDto dayOfSecond = dayOf(days, second);

        dayOfFirst.getNodes().removeIf(item -> item == first);
        dayOfSecond.getNodes().removeIf(item -> item == second);

        TimeSlotDto firstSlot = first.getTimeSlot();
        first.setTimeSlot(second.getTimeSlot());
        second.setTimeSlot(firstSlot);

        dayOfFirst.getNodes().add(second);
        dayOfSecond.getNodes().add(first);
        sort(dayOfFirst.getNodes());
        sort(dayOfSecond.getNodes());
    }

    private static GuidebookNodeDto find(List<GuidebookDayDto> days, String nodeId) {
        if (!StringUtils.hasText(nodeId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "操作缺少 nodeId");
        }
        for (GuidebookDayDto day : days) {
            if (day.getNodes() == null) {
                continue;
            }
            for (GuidebookNodeDto node : day.getNodes()) {
                if (nodeId.equals(node.getNodeId())) {
                    return node;
                }
            }
        }
        throw new BizException(ResultCode.NOT_FOUND, "节点不存在：" + nodeId);
    }

    private static GuidebookNodeDto remove(List<GuidebookDayDto> days, String nodeId) {
        GuidebookNodeDto node = find(days, nodeId);
        dayOf(days, node).getNodes().removeIf(item -> item == node);
        return node;
    }

    private static GuidebookDayDto day(List<GuidebookDayDto> days, Integer dayNumber) {
        if (dayNumber == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "移动操作缺少目标日期");
        }
        return days.stream()
                .filter(day -> dayNumber.equals(day.getDay()))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "目标日期不存在"));
    }

    private static GuidebookDayDto dayOf(List<GuidebookDayDto> days, GuidebookNodeDto node) {
        for (GuidebookDayDto day : days) {
            if (day.getNodes() == null) {
                continue;
            }
            for (GuidebookNodeDto item : day.getNodes()) {
                if (item == node) {
                    return day;
                }
            }
        }
        throw new BizException(ResultCode.NOT_FOUND, "节点不存在：" + node.getNodeId());
    }

    private static List<GuidebookNodeDto> nodesOf(GuidebookDayDto day) {
        if (day.getNodes() == null) {
            day.setNodes(new ArrayList<>());
        }
        return day.getNodes();
    }

    private static void sort(List<GuidebookNodeDto> nodes) {
        nodes.sort((a, b) -> start(a).compareTo(start(b)));
    }

    private static String start(GuidebookNodeDto node) {
        return node.getTimeSlot() == null || node.getTimeSlot().getStart() == null
                ? "" : node.getTimeSlot().getStart();
    }

    private static TimeSlotDto copy(TimeSlotDto slot) {
        return new TimeSlotDto(slot.getStart(), slot.getEnd());
    }
}
