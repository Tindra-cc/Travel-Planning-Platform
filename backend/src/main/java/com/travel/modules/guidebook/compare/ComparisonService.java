package com.travel.modules.guidebook.compare;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserLevelPolicy;
import com.travel.modules.guidebook.dto.CompareRequest;
import com.travel.modules.guidebook.dto.CompareResponse;
import com.travel.modules.guidebook.dto.ComparisonDetailResponse;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookComparison;
import com.travel.modules.guidebook.generation.GuidebookGenerationRunner;
import com.travel.modules.guidebook.mapper.GuidebookComparisonMapper;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.service.GuidebookService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** FR-4：创建对比任务、查询对比详情。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {

    private static final List<String> DEFAULT_VARIANTS = List.of("budget", "balanced", "luxury");
    private static final List<String> ALLOWED_VARIANTS = List.of("budget", "balanced", "luxury");
    private static final Set<String> BASE_STATUSES = Set.of(
            Guidebook.STATUS_DRAFT, Guidebook.STATUS_EDITING, Guidebook.STATUS_CONFIRMED);
    private static final String LOCK_PREFIX = "travel:compare:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(600);

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final GuidebookService guidebookService;
    private final GuidebookMapper guidebookMapper;
    private final GuidebookComparisonMapper comparisonMapper;
    private final GuidebookGenerationRunner generationRunner;
    private final ComparisonRunner comparisonRunner;
    private final UserLevelPolicy userLevelPolicy;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;

    /** §6.1 一次对比视为一次生成：配额只扣一次，三套方案顺序生成。 */
    public CompareResponse start(CompareRequest req) {
        Long userId = UserContext.getUserId();
        if (req == null || req.getBaseGuidebookId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "baseGuidebookId 不能为空");
        }
        Guidebook base = guidebookService.requireOwned(req.getBaseGuidebookId());
        if (!BASE_STATUSES.contains(base.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "当前攻略状态不支持生成对比方案");
        }
        if (!userLevelPolicy.allowMultiPlan(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "当前账号暂不支持多方案对比");
        }
        List<String> variants = normalizeVariants(req.getVariants());
        if (!generationRunner.hasCapacity()) {
            throw new BizException(ResultCode.TOO_MANY_REQUEST, "生成任务繁忙，请稍后再试");
        }

        String lockKey = LOCK_PREFIX + userId;
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCode.TOO_MANY_REQUEST, "已有对比任务进行中，请稍后再试");
        }

        try {
            Bundle bundle = transactionTemplate.execute(status -> {
                guidebookService.consumeQuotaOnce(userId);
                GuidebookComparison comparison = new GuidebookComparison();
                comparison.setUserId(userId);
                comparison.setBaseGuidebookId(base.getId());
                comparison.setStatus(GuidebookComparison.STATUS_PROCESSING);
                comparisonMapper.insert(comparison);

                List<Guidebook> created = new ArrayList<>();
                for (String variant : variants) {
                    created.add(guidebookService.createComparisonGuidebook(
                            userId, base, variant, comparison.getId()));
                }
                return new Bundle(comparison, created);
            });
            if (bundle == null) {
                throw new BizException(ResultCode.FAIL, "对比任务创建失败");
            }
            try {
                comparisonRunner.submit(bundle.comparison(), bundle.guidebooks(), token);
            } catch (RuntimeException e) {
                log.warn("对比任务提交失败 comparisonId={}: {}",
                        bundle.comparison().getId(), e.getMessage());
                markSubmitFailed(bundle.comparison().getId());
                throw new BizException(ResultCode.TOO_MANY_REQUEST, "生成任务繁忙，请稍后再试");
            }
            List<CompareResponse.CompareGuidebookItem> items = bundle.guidebooks().stream()
                    .map(g -> new CompareResponse.CompareGuidebookItem(
                            g.getId(), g.getVariant(), null))
                    .toList();
            return new CompareResponse(bundle.comparison().getId(), items);
        } catch (RuntimeException e) {
            releaseLock(lockKey, token);
            throw e;
        }
    }

    /** §6.2 对比详情；前端轮询此接口，不推测各方案状态。 */
    public ComparisonDetailResponse detail(Long comparisonId) {
        GuidebookComparison comparison = requireOwned(comparisonId);
        List<Guidebook> variants = guidebookMapper.selectList(
                new LambdaQueryWrapper<Guidebook>()
                        .eq(Guidebook::getComparisonId, comparisonId)
                        .orderByAsc(Guidebook::getId));
        List<ComparisonDetailResponse.VariantDetail> items = variants.stream()
                .map(this::toDetail)
                .toList();
        return new ComparisonDetailResponse(comparison.getId(), comparison.getBaseGuidebookId(),
                comparison.getStatus(), comparison.getErrorMessage(), items);
    }

    /**
     * 最近一次对比任务。退出对比页后再点「生成对比方案」时直接恢复上一次结果，
     * 避免重复扣配额、重复等待生成。
     *
     * @param baseGuidebookId 基准攻略 ID
     * @return 最近一次对比详情；从未生成过时返回 null
     */
    public ComparisonDetailResponse latest(Long baseGuidebookId) {
        if (baseGuidebookId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "baseGuidebookId 不能为空");
        }
        Guidebook base = guidebookService.requireOwned(baseGuidebookId);
        Long userId = UserContext.getUserId();
        GuidebookComparison comparison = comparisonMapper.selectOne(
                new LambdaQueryWrapper<GuidebookComparison>()
                        .eq(GuidebookComparison::getUserId, userId)
                        .eq(GuidebookComparison::getBaseGuidebookId, base.getId())
                        .orderByDesc(GuidebookComparison::getId)
                        .last("LIMIT 1"));
        if (comparison == null) {
            return null;
        }
        return detail(comparison.getId());
    }

    private ComparisonDetailResponse.VariantDetail toDetail(Guidebook guidebook) {
        var budget = guidebook.getBudgetBreakdown() == null
                ? null : guidebook.getBudgetBreakdown().getTotal();
        int dayCount = guidebook.getDays() == null ? 0 : guidebook.getDays().size();
        List<String> highlights = new ArrayList<>();
        if (guidebook.getDays() != null) {
            for (var day : guidebook.getDays()) {
                if (day == null || day.getNodes() == null) {
                    continue;
                }
                for (GuidebookNodeDto node : day.getNodes()) {
                    if (highlights.size() >= 3) {
                        break;
                    }
                    if (node != null && node.getPlace() != null
                            && node.getPlace().getName() != null
                            && !node.getPlace().getName().isBlank()
                            && !highlights.contains(node.getPlace().getName())) {
                        highlights.add(node.getPlace().getName());
                    }
                }
            }
        }
        return new ComparisonDetailResponse.VariantDetail(guidebook.getId(),
                guidebook.getVariant(), guidebook.getStatus(), guidebook.getProgress(),
                guidebook.getTitle(), budget, dayCount, highlights);
    }

    public GuidebookComparison requireOwned(Long comparisonId) {
        GuidebookComparison comparison = comparisonMapper.selectById(comparisonId);
        if (comparison == null) {
            throw new BizException(ResultCode.NOT_FOUND, "对比任务不存在");
        }
        if (!comparison.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该对比任务");
        }
        return comparison;
    }

    private List<String> normalizeVariants(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return DEFAULT_VARIANTS;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String variant : requested) {
            String value = variant == null ? null : variant.trim().toLowerCase();
            if (value == null || !ALLOWED_VARIANTS.contains(value)) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "variant 仅支持 budget / balanced / luxury");
            }
            unique.add(value);
        }
        if (unique.size() < 2) {
            throw new BizException(ResultCode.PARAM_ERROR, "至少选择 2 种方案进行对比");
        }
        return List.copyOf(unique);
    }

    private void markSubmitFailed(Long comparisonId) {
        try {
            GuidebookComparison update = new GuidebookComparison();
            update.setId(comparisonId);
            update.setStatus(GuidebookComparison.STATUS_FAILED);
            update.setErrorMessage("生成任务繁忙，请稍后重试");
            comparisonMapper.updateById(update);
            guidebookMapper.update(null, new UpdateWrapper<Guidebook>()
                    .eq("comparison_id", comparisonId)
                    .set("status", Guidebook.STATUS_FAILED)
                    .set("current_step", "failed")
                    .set("error_message", "生成任务繁忙，请稍后重试"));
        } catch (RuntimeException e) {
            log.error("标记对比任务失败异常 comparisonId={}: {}", comparisonId, e.getMessage());
        }
    }

    private void releaseLock(String key, String token) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (RuntimeException e) {
            log.warn("释放对比锁失败 key={}: {}", key, e.getMessage());
        }
    }

    private record Bundle(GuidebookComparison comparison, List<Guidebook> guidebooks) {
    }
}
