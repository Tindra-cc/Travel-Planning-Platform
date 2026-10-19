package com.travel.ai;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 通义千问客户端（DashScope OpenAI 兼容模式），作为 DeepSeek 失败后的 L1 兜底模型。
 */
@Component("dashScopeAiClient")
public class DashScopeAiClient extends OpenAiCompatibleClient {

    private final DashScopeProperties props;

    public DashScopeAiClient(DashScopeProperties props, ObjectMapper objectMapper) {
        super(props.getBaseUrl(), objectMapper);
        this.props = props;
    }

    @Override
    protected String providerName() {
        return "通义千问";
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
