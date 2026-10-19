package com.travel.ai;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.travel.common.BizException;
import com.travel.common.ResultCode;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 模型降级链：DeepSeek（L0）→ 通义千问（L1）。
 * 两者都不可用时抛 DEPENDENCY_UNAVAILABLE，由生成流水线降级到模板。
 */
@Slf4j
@Primary
@Component
public class AiModelRouter implements AiClient {

    private final AiClient primary;
    private final AiClient fallback;

    public AiModelRouter(@Qualifier("deepSeekAiClient") AiClient primary,
            @Qualifier("dashScopeAiClient") AiClient fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public String chat(List<AiMessage> messages) {
        try {
            return primary.chat(messages);
        } catch (Exception primaryError) {
            log.warn("主模型调用失败，尝试兜底模型: {}", primaryError.getMessage());
        }
        try {
            return fallback.chat(messages);
        } catch (Exception fallbackError) {
            log.warn("兜底模型调用失败: {}", fallbackError.getMessage());
        }
        throw new BizException(ResultCode.DEPENDENCY_UNAVAILABLE, "AI 服务暂不可用");
    }

    @Override
    public Flux<String> chatStream(List<AiMessage> messages) {
        return Flux.defer(() -> primary.chatStream(messages))
                .onErrorResume(primaryError -> {
                    log.warn("主流式调用失败，尝试兜底模型: {}", primaryError.getMessage());
                    return fallback.chatStream(messages);
                });
    }
}
