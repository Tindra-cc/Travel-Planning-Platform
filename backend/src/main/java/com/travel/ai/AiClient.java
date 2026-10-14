package com.travel.ai;

import java.util.List;

import reactor.core.publisher.Flux;

/**
 * AI 大模型调用抽象，便于替换供应商与测试打桩。
 */
public interface AiClient {

    /** 一次性返回完整回复 */
    String chat(List<AiMessage> messages);

    /** 流式返回增量片段 */
    Flux<String> chatStream(List<AiMessage> messages);
}
