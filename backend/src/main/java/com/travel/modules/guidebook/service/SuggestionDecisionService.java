package com.travel.modules.guidebook.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.dto.SuggestionDecisionResponse;
import com.travel.modules.guidebook.dto.SuggestionOperation;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.entity.TripSuggestion;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.TripSuggestionMapper;

import lombok.RequiredArgsConstructor;

/**
 * FR-10 阶段 C：建议决策。
 * 接受 = 深拷贝 days → 应用操作 → 冲突复检 → 乐观锁更新 → companion 快照；
 * 拒绝只改建议状态。两者都用条件更新保证幂等。
 */
@Service
@RequiredArgsConstructor
public class SuggestionDecisionService {

    private final GuidebookMapper guidebookMapper;
    private final TripSuggestionMapper suggestionMapper;
    private final GuidebookVersionService versionService;
    private final GuidebookConflictService conflictService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SuggestionDecisionResponse accept(Long guidebookId, Long suggestionId) {
        TripSuggestion suggestion = requirePending(guidebookId, suggestionId);
        Guidebook guidebook = requireExecuting(guidebookId);

        boolean applied = suggestion.getOperations() != null
                && !suggestion.getOperations().isEmpty();
        if (!applied) {
            decide(suggestion, TripSuggestion.STATUS_ACCEPTED);
            return new SuggestionDecisionResponse(suggestionId, TripSuggestion.STATUS_ACCEPTED,
                    false, guidebook.getVersion(), suggestion.getAction());
        }

        List<GuidebookDayDto> copy = deepCopy(guidebook.getDays());
        for (SuggestionOperation operation : suggestion.getOperations()) {
            CompanionNodeEditor.apply(copy, operation);
        }
        guidebook.setDays(copy);

        List<NodeConflictDto> conflicts = conflictService.detect(guidebook);
        if (!conflicts.isEmpty()) {
            throw new BizException(ResultCode.UNPROCESSABLE,
                    "调整后存在 " + conflicts.size() + " 处冲突，请先处理");
        }

        if (guidebookMapper.updateById(guidebook) != 1) {
            throw new BizException(ResultCode.CONFLICT, "攻略已被修改，请刷新后重试");
        }
        versionService.append(guidebook, GuidebookVersion.CHANGE_TYPE_COMPANION,
                suggestion.getAction(), UserContext.getUserId());
        decide(suggestion, TripSuggestion.STATUS_ACCEPTED);
        return new SuggestionDecisionResponse(suggestionId, TripSuggestion.STATUS_ACCEPTED,
                true, guidebook.getVersion(), suggestion.getAction());
    }

    @Transactional
    public SuggestionDecisionResponse reject(Long guidebookId, Long suggestionId) {
        requirePending(guidebookId, suggestionId);
        requireOwned(guidebookId);
        int updated = suggestionMapper.update(null, new UpdateWrapper<TripSuggestion>()
                .eq("id", suggestionId)
                .eq("status", TripSuggestion.STATUS_PENDING)
                .set("status", TripSuggestion.STATUS_REJECTED)
                .set("decided_at", LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException(ResultCode.CONFLICT, "建议已被处理");
        }
        return new SuggestionDecisionResponse(suggestionId, TripSuggestion.STATUS_REJECTED,
                false, null, null);
    }

    private void decide(TripSuggestion suggestion, String status) {
        int updated = suggestionMapper.update(null, new UpdateWrapper<TripSuggestion>()
                .eq("id", suggestion.getId())
                .eq("status", TripSuggestion.STATUS_PENDING)
                .set("status", status)
                .set("decided_at", LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException(ResultCode.CONFLICT, "建议已被处理");
        }
    }

    private TripSuggestion requirePending(Long guidebookId, Long suggestionId) {
        TripSuggestion suggestion = suggestionMapper.selectById(suggestionId);
        if (suggestion == null
                || !Objects.equals(suggestion.getGuidebookId(), guidebookId)) {
            throw new BizException(ResultCode.NOT_FOUND, "建议不存在");
        }
        if (!TripSuggestion.STATUS_PENDING.equals(suggestion.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "建议已被处理");
        }
        LocalDateTime now = LocalDateTime.now();
        if (suggestion.getExpiresAt() != null && suggestion.getExpiresAt().isBefore(now)) {
            suggestionMapper.update(null, new UpdateWrapper<TripSuggestion>()
                    .eq("id", suggestion.getId())
                    .eq("status", TripSuggestion.STATUS_PENDING)
                    .set("status", TripSuggestion.STATUS_EXPIRED)
                    .set("decided_at", now));
            throw new BizException(ResultCode.CONFLICT, "建议已过期");
        }
        return suggestion;
    }

    private Guidebook requireExecuting(Long guidebookId) {
        Guidebook guidebook = requireOwned(guidebookId);
        if (!Guidebook.STATUS_EXECUTING.equals(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "当前状态不能接受出行建议");
        }
        return guidebook;
    }

    private Guidebook requireOwned(Long guidebookId) {
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null) {
            throw new BizException(ResultCode.NOT_FOUND, "攻略不存在");
        }
        if (!Objects.equals(guidebook.getUserId(), UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该攻略");
        }
        return guidebook;
    }

    private List<GuidebookDayDto> deepCopy(List<GuidebookDayDto> days) {
        if (days == null) {
            return new java.util.ArrayList<>();
        }
        return objectMapper.convertValue(days,
                new TypeReference<List<GuidebookDayDto>>() { });
    }
}
