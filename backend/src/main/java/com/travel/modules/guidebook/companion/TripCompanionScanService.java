package com.travel.modules.guidebook.companion;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.data.TravelDataSnapshot;
import com.travel.modules.guidebook.data.TravelDataService;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.TripSuggestion;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.TripSuggestionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FR-10 阶段 B：扫描编排。取数、加锁、落库；规则本身在 {@link SuggestionRuleEngine}。
 * 单攻略异常必须隔离，不能拖垮整批。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripCompanionScanService {

    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final GuidebookMapper guidebookMapper;
    private final TripSuggestionMapper suggestionMapper;
    private final TravelDataService travelDataService;
    private final SuggestionRuleEngine ruleEngine;
    private final StringRedisTemplate redisTemplate;

    public int scanAll() {
        List<Guidebook> candidates = guidebookMapper.selectList(
                Wrappers.<Guidebook>lambdaQuery()
                        .in(Guidebook::getStatus,
                                Guidebook.STATUS_CONFIRMED, Guidebook.STATUS_EXECUTING));
        LocalDate today = LocalDate.now();
        int created = 0;
        for (Guidebook guidebook : candidates) {
            if (!inWindow(guidebook, today)) {
                continue;
            }
            created += scanOne(guidebook, today);
        }
        return created;
    }

    public int refresh(Long guidebookId, Long userId) {
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null) {
            throw new BizException(ResultCode.NOT_FOUND, "攻略不存在");
        }
        if (!Objects.equals(guidebook.getUserId(), userId)) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该攻略");
        }
        LocalDate today = LocalDate.now();
        if (!isScannable(guidebook.getStatus()) || !inWindow(guidebook, today)) {
            return 0;
        }
        return scanOne(guidebook, today);
    }

    private int scanOne(Guidebook guidebook, LocalDate today) {
        String lockKey = "travel:companion:job:" + guidebook.getId() + ":" + today;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return 0;
        }
        try {
            TravelDataSnapshot data = travelDataService.fetch(guidebook.getIntent());
            List<SuggestionDraft> drafts = ruleEngine.evaluate(guidebook, data, today);
            return persist(guidebook, drafts);
        } catch (Exception e) {
            log.warn("行程伴侣扫描失败 guidebookId={}: {}", guidebook.getId(), e.getMessage());
            return 0;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private int persist(Guidebook guidebook, List<SuggestionDraft> drafts) {
        int inserted = 0;
        for (SuggestionDraft draft : drafts) {
            TripSuggestion suggestion = new TripSuggestion();
            suggestion.setGuidebookId(guidebook.getId());
            suggestion.setUserId(guidebook.getUserId());
            suggestion.setSuggestionType(draft.type());
            suggestion.setDedupeKey(draft.dedupeKey());
            suggestion.setReason(draft.reason());
            suggestion.setAction(draft.action());
            suggestion.setOperations(draft.operations());
            suggestion.setAlternatives(draft.alternatives());
            suggestion.setEvidence(draft.evidence());
            suggestion.setStatus(TripSuggestion.STATUS_PENDING);
            suggestion.setExpiresAt(draft.triggerDate().atTime(23, 59, 59));
            try {
                suggestionMapper.insert(suggestion);
                inserted++;
            } catch (DuplicateKeyException duplicate) {
                // 唯一索引兜底：同一影响已存在，属正常并发结果
            }
        }
        return inserted;
    }

    private boolean isScannable(String status) {
        return Guidebook.STATUS_CONFIRMED.equals(status)
                || Guidebook.STATUS_EXECUTING.equals(status);
    }

    private boolean inWindow(Guidebook guidebook, LocalDate today) {
        if (guidebook.getIntent() == null || guidebook.getIntent().getDateWindow() == null) {
            return false;
        }
        LocalDate start = parse(guidebook.getIntent().getDateWindow().getStart());
        LocalDate end = parse(guidebook.getIntent().getDateWindow().getEnd());
        return start != null && end != null
                && !today.isBefore(start) && !today.isAfter(end);
    }

    private LocalDate parse(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
