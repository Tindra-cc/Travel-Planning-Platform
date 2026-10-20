package com.travel.modules.guidebook.compare;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.MixRequest;
import com.travel.modules.guidebook.dto.MixResponse;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookComparison;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.generation.GeneratedContent;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.service.GuidebookConflictService;
import com.travel.modules.guidebook.service.GuidebookVersionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FR-4 混搭：跨方案挑选节点 → AI 重平衡 → 冲突复检。
 * 有冲突直接返回 blocked，不落库；AI 失败保留原始 picks 并标注降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MixService {

    private static final int MAX_PICKS = 30;
    private static final String STATUS_BLOCKED = "blocked";
    private static final String STATUS_DRAFT = "draft";
    private static final String VARIANT_MIXED = "mixed";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ComparisonService comparisonService;
    private final GuidebookMapper guidebookMapper;
    private final GuidebookVersionService versionService;
    private final GuidebookConflictService conflictService;
    private final MixRebalanceService rebalanceService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /** §6.3 混搭生成。 */
    public MixResponse mix(Long comparisonId, MixRequest req) {
        GuidebookComparison comparison = comparisonService.requireOwned(comparisonId);
        if (GuidebookComparison.STATUS_PROCESSING.equals(comparison.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "对比方案尚未生成完成");
        }
        if (GuidebookComparison.STATUS_FAILED.equals(comparison.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "对比方案生成失败，无法混搭");
        }
        List<MixRequest.Pick> picks = req == null ? null : req.getPicks();
        if (picks == null || picks.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "请至少选择一个节点");
        }

        Map<Long, Guidebook> variants = guidebookMapper.selectList(
                        new LambdaQueryWrapper<Guidebook>()
                                .eq(Guidebook::getComparisonId, comparisonId))
                .stream()
                .collect(Collectors.toMap(Guidebook::getId, Function.identity()));
        Guidebook base = variants.get(comparison.getBaseGuidebookId());
        if (base == null && !variants.isEmpty()) {
            base = variants.values().iterator().next();
        }
        if (base == null) {
            throw new BizException(ResultCode.CONFLICT, "对比方案已不存在");
        }
        Guidebook baseGuidebook = base;

        List<GuidebookDayDto> pickedDays = collectPicks(picks, variants);
        GeneratedContent content = null;
        boolean degraded = false;
        try {
            content = rebalanceService.rebalance(baseGuidebook.getIntent(), pickedDays);
        } catch (RuntimeException e) {
            degraded = true;
            log.warn("混搭重平衡失败，回退到所选节点 comparisonId={}: {}",
                    comparisonId, e.getMessage());
        }

        List<GuidebookDayDto> days = content == null ? pickedDays : content.getDays();
        BudgetBreakdownDto budget = content == null ? null : content.getBudgetBreakdown();

        Guidebook probe = new Guidebook();
        probe.setIntent(baseGuidebook.getIntent());
        probe.setDays(days);
        probe.setBudgetBreakdown(budget);
        List<NodeConflictDto> conflicts = conflictService.detect(probe);
        if (!conflicts.isEmpty()) {
            return new MixResponse(null, STATUS_BLOCKED, conflicts);
        }

        String title = content == null || !StringUtils.hasText(content.getTitle())
                ? fallbackTitle(baseGuidebook.getTitle()) : content.getTitle();
        Guidebook mixed = buildGuidebook(comparison, baseGuidebook, title, days, budget,
                content, degraded);
        Long guidebookId = transactionTemplate.execute(status -> {
            guidebookMapper.insert(mixed);
            versionService.append(mixed, GuidebookVersion.CHANGE_TYPE_GENERATE,
                    "混搭生成", baseGuidebook.getUserId());
            return mixed.getId();
        });
        return new MixResponse(guidebookId, STATUS_DRAFT, List.of());
    }

    private List<GuidebookDayDto> collectPicks(List<MixRequest.Pick> picks,
            Map<Long, Guidebook> variants) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> usedNodeIds = new HashSet<>();
        Map<Integer, List<GuidebookNodeDto>> byDay = new TreeMap<>();

        for (MixRequest.Pick pick : picks) {
            if (pick == null || pick.getGuidebookId() == null
                    || !StringUtils.hasText(pick.getNodeId())) {
                throw new BizException(ResultCode.PARAM_ERROR, "pick 缺少 guidebookId 或 nodeId");
            }
            Guidebook source = variants.get(pick.getGuidebookId());
            if (source == null) {
                throw new BizException(ResultCode.UNPROCESSABLE, "所选方案不属于该对比任务");
            }
            if (Guidebook.STATUS_GENERATING.equals(source.getStatus())
                    || Guidebook.STATUS_FAILED.equals(source.getStatus())) {
                throw new BizException(ResultCode.UNPROCESSABLE, "所选方案尚未生成完成");
            }
            if (!seen.add(pick.getGuidebookId() + ":" + pick.getNodeId())) {
                continue;
            }
            if (seen.size() > MAX_PICKS) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "最多选择 " + MAX_PICKS + " 个节点");
            }
            int day = findDay(source, pick.getNodeId());
            GuidebookNodeDto node = findNode(source, pick.getNodeId());
            if (node == null) {
                throw new BizException(ResultCode.UNPROCESSABLE, "所选节点不存在");
            }
            GuidebookNodeDto copy;
            try {
                copy = objectMapper.convertValue(node, GuidebookNodeDto.class);
            } catch (RuntimeException e) {
                log.warn("混搭节点复制失败 nodeId={}: {}", pick.getNodeId(), e.getMessage());
                throw new BizException(ResultCode.FAIL, "节点复制失败，请重试");
            }
            copy.setNodeId(newNodeId(usedNodeIds));
            usedNodeIds.add(copy.getNodeId());
            byDay.computeIfAbsent(day, key -> new ArrayList<>()).add(copy);
        }

        if (byDay.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "请至少选择一个节点");
        }
        List<GuidebookDayDto> days = new ArrayList<>();
        byDay.forEach((day, nodes) -> {
            nodes.sort(Comparator.comparing(this::startTime,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            days.add(new GuidebookDayDto(day, nodes));
        });
        return days;
    }

    private int findDay(Guidebook source, String nodeId) {
        if (source.getDays() == null) {
            return 1;
        }
        for (GuidebookDayDto day : source.getDays()) {
            if (day == null || day.getNodes() == null) {
                continue;
            }
            for (GuidebookNodeDto node : day.getNodes()) {
                if (node != null && nodeId.equals(node.getNodeId())) {
                    return day.getDay() == null || day.getDay() < 1 ? 1 : day.getDay();
                }
            }
        }
        return 1;
    }

    private GuidebookNodeDto findNode(Guidebook source, String nodeId) {
        if (source.getDays() == null) {
            return null;
        }
        for (GuidebookDayDto day : source.getDays()) {
            if (day == null || day.getNodes() == null) {
                continue;
            }
            for (GuidebookNodeDto node : day.getNodes()) {
                if (node != null && nodeId.equals(node.getNodeId())) {
                    return node;
                }
            }
        }
        return null;
    }

    private LocalTime startTime(GuidebookNodeDto node) {
        if (node == null || node.getTimeSlot() == null) {
            return null;
        }
        try {
            return LocalTime.parse(node.getTimeSlot().getStart(), TIME_FORMAT);
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    private String newNodeId(Set<String> used) {
        String id;
        do {
            id = "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (used.contains(id));
        return id;
    }

    private Guidebook buildGuidebook(GuidebookComparison comparison, Guidebook base, String title,
            List<GuidebookDayDto> days, BudgetBreakdownDto budget, GeneratedContent content,
            boolean degraded) {
        Guidebook mixed = new Guidebook();
        mixed.setUserId(comparison.getUserId());
        mixed.setSessionId(null);
        mixed.setStatus(Guidebook.STATUS_DRAFT);
        mixed.setVersion(1);
        mixed.setTitle(truncate(title));
        mixed.setIntent(base.getIntent());
        mixed.setProfileSnapshot(base.getProfileSnapshot());
        mixed.setDays(days);
        mixed.setBudgetBreakdown(budget);
        if (content != null) {
            mixed.setPackingList(content.getPackingList());
            mixed.setCulturalTips(content.getCulturalTips());
            mixed.setPitfallTips(content.getPitfallTips());
        }
        mixed.setComparisonId(comparison.getId());
        mixed.setVariant(VARIANT_MIXED);
        mixed.setDegradedMode(degraded);
        mixed.setDegradationReasons(degraded ? List.of("mix_rebalance_failed") : List.of());
        mixed.setCurrentStep("completed");
        mixed.setProgress(100);
        mixed.setAttemptCount(1);
        mixed.setStartedAt(LocalDateTime.now());
        mixed.setFinishedAt(LocalDateTime.now());
        return mixed;
    }

    private String fallbackTitle(String baseTitle) {
        return StringUtils.hasText(baseTitle) ? baseTitle + "（混搭）" : "混搭攻略";
    }

    private String truncate(String title) {
        if (!StringUtils.hasText(title)) {
            return "混搭攻略";
        }
        return title.length() > 128 ? title.substring(0, 128) : title;
    }
}
