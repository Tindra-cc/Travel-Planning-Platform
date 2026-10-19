package com.travel.modules.guidebook.generation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.travel.common.BizException;
import com.travel.common.ResultCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 按 @Order 顺序执行生成步骤；非关键步骤失败记录降级原因后继续。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuidebookGenerationPipeline {

    private static final long DEADLINE_MS = 180_000L;

    private final List<GenerationStep> steps;
    private final GuidebookProgressPublisher publisher;

    public void run(GenerationContext ctx) {
        long startedAt = System.currentTimeMillis();
        for (GenerationStep step : steps) {
            if (System.currentTimeMillis() - startedAt > DEADLINE_MS) {
                throw new BizException(ResultCode.DEPENDENCY_UNAVAILABLE, "攻略生成超时");
            }
            try {
                step.execute(ctx);
            } catch (RuntimeException e) {
                if (step.critical()) {
                    throw e;
                }
                log.warn("生成步骤 {} 失败，已降级: {}", step.name(), e.getMessage());
                ctx.addReason(step.name() + "_failed");
            }
            publisher.step(ctx.getGuidebookId(), step.name(), step.progress());
        }
    }
}
