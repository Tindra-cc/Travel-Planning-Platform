package com.travel.modules.guidebook.compare;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookComparison;
import com.travel.modules.guidebook.generation.GenerationContext;
import com.travel.modules.guidebook.generation.GuidebookGenerationRunner;
import com.travel.modules.guidebook.mapper.GuidebookComparisonMapper;
import com.travel.modules.guidebook.mapper.GuidebookMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * FR-4：在生成池中用一个任务顺序跑完各变体。
 * 并行会挤占普通生成，因此这里只占用一个线程；每套方案独立失败隔离。
 */
@Slf4j
@Component
public class ComparisonRunner {

    private static final String LOCK_PREFIX = "travel:compare:lock:";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ThreadPoolTaskExecutor executor;
    private final GuidebookGenerationRunner generationRunner;
    private final GuidebookMapper guidebookMapper;
    private final GuidebookComparisonMapper comparisonMapper;
    private final StringRedisTemplate redisTemplate;

    public ComparisonRunner(@Qualifier("guidebookExecutor") ThreadPoolTaskExecutor executor,
            GuidebookGenerationRunner generationRunner, GuidebookMapper guidebookMapper,
            GuidebookComparisonMapper comparisonMapper, StringRedisTemplate redisTemplate) {
        this.executor = executor;
        this.generationRunner = generationRunner;
        this.guidebookMapper = guidebookMapper;
        this.comparisonMapper = comparisonMapper;
        this.redisTemplate = redisTemplate;
    }

    public void submit(GuidebookComparison comparison, List<Guidebook> variants, String lockToken) {
        executor.execute(() -> run(comparison, variants, lockToken));
    }

    public void run(GuidebookComparison comparison, List<Guidebook> variants, String lockToken) {
        try {
            for (Guidebook guidebook : variants) {
                GenerationContext ctx = new GenerationContext(guidebook.getId(),
                        comparison.getUserId(), null, guidebook.getIntent(),
                        guidebook.getProfileSnapshot() == null ? Map.of()
                                : guidebook.getProfileSnapshot());
                ctx.setVariant(guidebook.getVariant());
                ctx.setHoldsGenerateLock(false);
                generationRunner.run(ctx);
            }
        } catch (RuntimeException e) {
            log.error("对比方案执行异常 comparisonId={}: {}", comparison.getId(), e.getMessage(), e);
            markRemainingFailed(comparison.getId());
        } finally {
            finish(comparison.getId());
            releaseLock(comparison.getUserId(), lockToken);
        }
    }

    private void markRemainingFailed(Long comparisonId) {
        try {
            guidebookMapper.update(null, new UpdateWrapper<Guidebook>()
                    .eq("comparison_id", comparisonId)
                    .eq("status", Guidebook.STATUS_GENERATING)
                    .set("status", Guidebook.STATUS_FAILED)
                    .set("current_step", "failed")
                    .set("error_message", "对比任务中断，请重试该方案"));
        } catch (RuntimeException e) {
            log.warn("标记对比方案失败异常 comparisonId={}: {}", comparisonId, e.getMessage());
        }
    }

    private void finish(Long comparisonId) {
        try {
            List<Guidebook> items = guidebookMapper.selectList(new LambdaQueryWrapper<Guidebook>()
                    .eq(Guidebook::getComparisonId, comparisonId));
            if (items.isEmpty()) {
                return;
            }
            long failed = items.stream()
                    .filter(g -> Guidebook.STATUS_FAILED.equals(g.getStatus()))
                    .count();
            String status;
            if (failed == items.size()) {
                status = GuidebookComparison.STATUS_FAILED;
            } else if (failed > 0) {
                status = GuidebookComparison.STATUS_PARTIAL;
            } else {
                status = GuidebookComparison.STATUS_COMPLETED;
            }
            GuidebookComparison update = new GuidebookComparison();
            update.setId(comparisonId);
            update.setStatus(status);
            update.setErrorMessage(failed > 0 ? "部分方案生成失败，可单独重试失败的方案" : null);
            comparisonMapper.updateById(update);
        } catch (RuntimeException e) {
            log.error("汇总对比状态失败 comparisonId={}: {}", comparisonId, e.getMessage());
        }
    }

    private void releaseLock(Long userId, String token) {
        if (token == null) {
            return;
        }
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(LOCK_PREFIX + userId), token);
        } catch (RuntimeException e) {
            log.warn("释放对比锁失败 userId={}: {}", userId, e.getMessage());
        }
    }
}
