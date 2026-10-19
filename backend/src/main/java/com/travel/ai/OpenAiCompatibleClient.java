package com.travel.ai;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * OpenAI 兼容 /chat/completions 客户端基类。DeepSeek 与通义千问共用同一套请求/解析逻辑，
 * 差异只在 baseUrl、密钥、模型名与超时。
 */
@Slf4j
public abstract class OpenAiCompatibleClient implements AiClient {

    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    protected OpenAiCompatibleClient(String baseUrl, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    protected abstract String providerName();

    protected abstract String apiKey();

    protected abstract String model();

    protected abstract Duration timeout();

    protected abstract double temperature();

    protected abstract boolean configured();

    @Override
    public String chat(List<AiMessage> messages) {
        ensureConfigured();
        try {
            String resp = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload(messages, false))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout())
                    .block();
            return extractContent(resp);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("{} chat 调用失败", providerName(), e);
            throw new BizException(ResultCode.DEPENDENCY_UNAVAILABLE, providerName() + " 服务暂不可用");
        }
    }

    @Override
    public Flux<String> chatStream(List<AiMessage> messages) {
        ensureConfigured();
        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload(messages, true))
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(timeout())
                .takeUntil("[DONE]"::equals)
                .filter(data -> !"[DONE]".equals(data))
                .map(this::extractDelta)
                .filter(StringUtils::hasLength)
                .onErrorMap(e -> !(e instanceof BizException),
                        e -> new BizException(ResultCode.DEPENDENCY_UNAVAILABLE,
                                providerName() + " 服务暂不可用"));
    }

    private Map<String, Object> payload(List<AiMessage> messages, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("temperature", temperature());
        return body;
    }

    private String extractContent(String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("解析 {} 响应失败: {}", providerName(), resp, e);
            throw new BizException(ResultCode.DEPENDENCY_UNAVAILABLE, "AI 响应解析失败");
        }
    }

    private String extractDelta(String data) {
        try {
            JsonNode node = objectMapper.readTree(data);
            return node.path("choices").path(0).path("delta").path("content").asText("");
        } catch (Exception e) {
            log.debug("跳过无法解析的流式片段: {}", data);
            return "";
        }
    }

    private void ensureConfigured() {
        if (!configured()) {
            throw new BizException(ResultCode.DEPENDENCY_UNAVAILABLE,
                    "未配置 " + providerName() + " API Key");
        }
    }
}
