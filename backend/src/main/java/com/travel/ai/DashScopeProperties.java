package com.travel.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import lombok.Data;

/**
 * 通义千问（DashScope OpenAI 兼容模式）接入配置。
 * 未配置 DASHSCOPE_API_KEY 时仅作为不可用兜底，不影响主链路。
 */
@Data
@ConfigurationProperties(prefix = "ai.dashscope")
public class DashScopeProperties {

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey;
    private String model = "qwen-plus";
    private Duration timeout = Duration.ofSeconds(60);
    private Double temperature = 0.7;

    public boolean configured() {
        return StringUtils.hasText(apiKey);
    }
}
