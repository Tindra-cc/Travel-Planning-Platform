package com.travel.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * DeepSeek 客户端，走 OpenAI 兼容的 /chat/completions 接口。
 * 流式依赖 WebFlux 的 SSE 解码（text/event-stream）。
 */
@Slf4j
@Component
public class DeepSeekAiClient implements AiClient {

    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public DeepSeekAiClient(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    @Override
    public String chat(List<AiMessage> messages) {
        ensureConfigured();
        try {
            String resp = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload(messages, false))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(props.getTimeout())
                    .block();
            return extractContent(resp);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek chat 调用失败", e);
            throw new BizException(ResultCode.DEPENDENCY_UNAVAILABLE, "AI 服务暂不可用");
        }
    }

    @Override
    public Flux<String> chatStream(List<AiMessage> messages) {
        ensureConfigured();
        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + props.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload(messages, true))
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(props.getTimeout())
                .takeUntil("[DONE]"::equals)
                .filter(data -> !"[DONE]".equals(data))
                .map(this::extractDelta)
                .filter(StringUtils::hasLength)
                .onErrorMap(e -> !(e instanceof BizException),
                        e -> new BizException(ResultCode.DEPENDENCY_UNAVAILABLE, "AI 服务暂不可用"));
    }

    private Map<String, Object> payload(List<AiMessage> messages, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("temperature", props.getTemperature());
        return body;
    }

    private String extractContent(String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("解析 DeepSeek 响应失败: {}", resp, e);
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
        if (!props.configured()) {
            throw new BizException(ResultCode.DEPENDENCY_UNAVAILABLE, "未配置 DEEPSEEK_API_KEY");
        }
    }
}
