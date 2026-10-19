package com.travel.ai;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * DeepSeek 客户端，走 OpenAI 兼容的 /chat/completions 接口。
 */
@Slf4j
@Component("deepSeekAiClient")
public class DeepSeekAiClient extends OpenAiCompatibleClient {

    private final AiProperties props;

    public DeepSeekAiClient(AiProperties props, ObjectMapper objectMapper) {
        super(props.getBaseUrl(), objectMapper);
        this.props = props;
    }

    @Override
    protected String providerName() {
        return "DeepSeek";
    }

    @Override
    protected String apiKey() {
        return props.getApiKey();
    }

    @Override
    protected String model() {
        return props.getModel();
    }

    @Override
    protected Duration timeout() {
        return props.getTimeout();
    }

    @Override
    protected double temperature() {
        return props.getTemperature();
    }

    @Override
    protected boolean configured() {
        return props.configured();
    }
}
