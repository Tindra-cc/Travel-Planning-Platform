package com.travel.modules.guidebook.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.NodeStatusResponse;
import com.travel.modules.guidebook.dto.TodayNodeDto;
import com.travel.modules.guidebook.dto.TripCompanionResponse;
import com.travel.modules.guidebook.edit.GuidebookNodeStatus;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.GuidebookNodeStatusMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripCompanionService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Set<String> NODE_STATUSES = Set.of(
            GuidebookNodeStatus.EXECUTED,
            GuidebookNodeStatus.SKIPPED,
            GuidebookNodeStatus.RESCHEDULED);

    private final GuidebookMapper guidebookMapper;
    private final GuidebookNodeStatusMapper nodeStatusMapper;

    public TripCompanionResponse get(Long guidebookId) {
        Guidebook guidebook = requireOwned(guidebookId);
        advanceStatus(guidebook);
        return buildResponse(guidebook);
    }

    public NodeStatusResponse markNode(Long guidebookId, String nodeId, String status) {
        if (!NODE_STATUSES.contains(status)) {
            throw new BizException(ResultCode.PARAM_ERROR, "status 只能是 executed/skipped/rescheduled");
        }
        Guidebook guidebook = requireOwned(guidebookId);
        advanceStatus(guidebook);
        if (Guidebook.STATUS_COMPLETED.equals(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "攻略已完成，不能再修改执行状态");
        }
        NodeLocation location = locateNode(guidebook, nodeId);
        markStatus(guidebookId, nodeId, status, location);
        completeIfAllTerminal(guidebook);
        return new NodeStatusResponse(guidebookId, nodeId, status,
                LocalDateTime.now().toString(), progress(guidebook));
    }

    private void markStatus(Long guidebookId, String nodeId, String status, NodeLocation location) {
        GuidebookNodeStatus existing = nodeStatusMapper.selectOne(
                Wrappers.<GuidebookNodeStatus>lambdaQuery()
                        .eq(GuidebookNodeStatus::getGuidebookId, guidebookId)
                        .eq(GuidebookNodeStatus::getNodeId, nodeId));
        if (existing != null) {
            updateExisting(guidebookId, nodeId, status);
            return;
        }
        GuidebookNodeStatus record = new GuidebookNodeStatus();
        record.setGuidebookId(guidebookId);
        record.setNodeId(nodeId);
        record.setNodeDay(location.day());
        record.setNodeName(location.name());
        record.setStatus(status);
        record.setMarkedAt(LocalDateTime.now());
        try {
            nodeStatusMapper.insert(record);
        } catch (DuplicateKeyException race) {
            updateExisting(guidebookId, nodeId, status);
        }
    }

    private void updateExisting(Long guidebookId, String nodeId, String status) {
        nodeStatusMapper.update(null, new UpdateWrapper<GuidebookNodeStatus>()
                .eq("guidebook_id", guidebookId)
                .eq("node_id", nodeId)
                .set("status", status)
                .set("marked_at", LocalDateTime.now()));
    }

    private void advanceStatus(Guidebook guidebook) {
        LocalDate today = LocalDate.now();
        LocalDate start = parse(guidebook, true);
        LocalDate end = parse(guidebook, false);
        if (start == null || end == null) {
            return;
        }
        if (Guidebook.STATUS_CONFIRMED.equals(guidebook.getStatus())
                && !today.isBefore(start) && !today.isAfter(end)) {
            guidebookMapper.update(null, new UpdateWrapper<Guidebook>()
                    .eq("id", guidebook.getId())
                    .eq("status", Guidebook.STATUS_CONFIRMED)
                    .set("status", Guidebook.STATUS_EXECUTING));
            guidebook.setStatus(Guidebook.STATUS_EXECUTING);
        }
        if (Guidebook.STATUS_EXECUTING.equals(guidebook.getStatus()) && today.isAfter(end)) {
            guidebookMapper.update(null, new UpdateWrapper<Guidebook>()
                    .eq("id", guidebook.getId())
                    .eq("status", Guidebook.STATUS_EXECUTING)
                    .set("status", Guidebook.STATUS_COMPLETED));
            guidebook.setStatus(Guidebook.STATUS_COMPLETED);
        }
    }

    private void completeIfAllTerminal(Guidebook guidebook) {
        if (!Guidebook.STATUS_EXECUTING.equals(guidebook.getStatus())) {
            return;
        }
        int total = totalNodes(guidebook);
        if (total == 0) {
            return;
        }
        Long terminal = nodeStatusMapper.selectCount(Wrappers.<GuidebookNodeStatus>lambdaQuery()
                .eq(GuidebookNodeStatus::getGuidebookId, guidebook.getId())
                .in(GuidebookNodeStatus::getStatus,
                        GuidebookNodeStatus.EXECUTED, GuidebookNodeStatus.SKIPPED));
        if (terminal != null && terminal == total) {
            guidebookMapper.update(null, new UpdateWrapper<Guidebook>()
                    .eq("id", guidebook.getId())
                    .eq("status", Guidebook.STATUS_EXECUTING)
                    .set("status", Guidebook.STATUS_COMPLETED));
            guidebook.setStatus(Guidebook.STATUS_COMPLETED);
        }
    }

    private TripCompanionResponse buildResponse(Guidebook guidebook) {
        TripCompanionResponse resp = new TripCompanionResponse();
        resp.setGuidebookId(guidebook.getId());
        resp.setStatus(guidebook.getStatus());
        resp.setDestination(guidebook.getIntent() == null ? null
                : guidebook.getIntent().getDestination());

        LocalDate start = parse(guidebook, true);
        LocalDate end = parse(guidebook, false);
        resp.setStartDate(start == null ? null : start.toString());
        resp.setEndDate(end == null ? null : end.toString());
        resp.setSuggestions(List.of());

        int total = totalNodes(guidebook);
        List<GuidebookNodeStatus> marks = nodeStatusMapper.selectList(
                Wrappers.<GuidebookNodeStatus>lambdaQuery()
                        .eq(GuidebookNodeStatus::getGuidebookId, guidebook.getId()));
        resp.setTotalCount(total);
        resp.setExecutedCount(count(marks, GuidebookNodeStatus.EXECUTED));
        resp.setSkippedCount(count(marks, GuidebookNodeStatus.SKIPPED));
        resp.setRescheduledCount(count(marks, GuidebookNodeStatus.RESCHEDULED));
        resp.setProgress(progress(guidebook));

        if (start != null && end != null) {
            LocalDate today = LocalDate.now();
            if (!today.isBefore(start) && !today.isAfter(end)) {
                int currentDay = (int) ChronoUnit.DAYS.between(start, today) + 1;
                resp.setCurrentDay(currentDay);
                resp.setTodayNodes(todayNodes(guidebook, currentDay, marks));
            }
        }
        return resp;
    }

    private List<TodayNodeDto> todayNodes(Guidebook guidebook, int currentDay,
            List<GuidebookNodeStatus> marks) {
        Map<String, GuidebookNodeStatus> byNodeId = new HashMap<>();
        for (GuidebookNodeStatus mark : marks) {
            byNodeId.put(mark.getNodeId(), mark);
        }
        List<TodayNodeDto> result = new ArrayList<>();
        if (guidebook.getDays() == null) {
            return result;
        }
        for (GuidebookDayDto day : guidebook.getDays()) {
            if (day.getDay() == null || day.getDay() != currentDay || day.getNodes() == null) {
                continue;
            }
            for (GuidebookNodeDto node : day.getNodes()) {
                GuidebookNodeStatus mark = byNodeId.get(node.getNodeId());
                TodayNodeDto dto = new TodayNodeDto();
                dto.setNodeId(node.getNodeId());
                dto.setDay(day.getDay());
                if (node.getTimeSlot() != null) {
                    dto.setTimeSlotStart(node.getTimeSlot().getStart());
                    dto.setTimeSlotEnd(node.getTimeSlot().getEnd());
                }
                dto.setType(node.getType());
                dto.setPlaceName(node.getPlace() == null ? null : node.getPlace().getName());
                dto.setNote(node.getNote());
                if (mark != null) {
                    dto.setExecutionStatus(mark.getStatus());
                    dto.setMarkedAt(mark.getMarkedAt() == null ? null : mark.getMarkedAt().toString());
                }
                result.add(dto);
            }
        }
        return result;
    }

    private NodeLocation locateNode(Guidebook guidebook, String nodeId) {
        if (guidebook.getDays() != null) {
            for (GuidebookDayDto day : guidebook.getDays()) {
                if (day.getNodes() == null) {
                    continue;
                }
                for (GuidebookNodeDto node : day.getNodes()) {
                    if (nodeId.equals(node.getNodeId())) {
                        String name = node.getPlace() == null ? null : node.getPlace().getName();
                        return new NodeLocation(day.getDay(), name);
                    }
                }
            }
        }
        throw new BizException(ResultCode.NOT_FOUND, "节点不存在");
    }

    private int totalNodes(Guidebook guidebook) {
        int total = 0;
        if (guidebook.getDays() != null) {
            for (GuidebookDayDto day : guidebook.getDays()) {
                if (day.getNodes() != null) {
                    total += day.getNodes().size();
                }
            }
        }
        return total;
    }

    private int count(List<GuidebookNodeStatus> marks, String status) {
        int count = 0;
        for (GuidebookNodeStatus mark : marks) {
            if (status.equals(mark.getStatus())) {
                count++;
            }
        }
        return count;
    }

    /** 百分比 0-100；改期不计入完成度。 */
    private Double progress(Guidebook guidebook) {
        int total = totalNodes(guidebook);
        if (total == 0) {
            return 0.0;
        }
        Long handled = nodeStatusMapper.selectCount(Wrappers.<GuidebookNodeStatus>lambdaQuery()
                .eq(GuidebookNodeStatus::getGuidebookId, guidebook.getId())
                .in(GuidebookNodeStatus::getStatus,
                        GuidebookNodeStatus.EXECUTED, GuidebookNodeStatus.SKIPPED));
        long done = handled == null ? 0 : handled;
        return Math.round(done * 10000.0 / total) / 100.0;
    }

    private Guidebook requireOwned(Long guidebookId) {
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null) {
            throw new BizException(ResultCode.NOT_FOUND, "攻略不存在");
        }
        if (!guidebook.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该攻略");
        }
        return guidebook;
    }

    private LocalDate parse(Guidebook guidebook, boolean start) {
        if (guidebook.getIntent() == null || guidebook.getIntent().getDateWindow() == null) {
            return null;
        }
        String text = start
                ? guidebook.getIntent().getDateWindow().getStart()
                : guidebook.getIntent().getDateWindow().getEnd();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text, DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private record NodeLocation(Integer day, String name) {
    }
}
