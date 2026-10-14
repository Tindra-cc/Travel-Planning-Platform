package com.travel.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import lombok.Data;

/**
 * DeepSeek（OpenAI 兼容）接入配置。api-key 通过环境变量 DEEPSEEK_API_KEY 注入。
 */
@Data
@ConfigurationProperties(prefix = "ai.deepseek")
public class AiProperties {

    private String baseUrl = "https://api.deepseek.com";
    private String apiKey;
    private String model = "deepseek-chat";
    private Duration timeout = Duration.ofSeconds(60);
    private Double temperature = 0.7;

    /** 是否已配置可用的 key，未配置时调用方应走降级逻辑 */
    public boolean configured() {
        return StringUtils.hasText(apiKey);
    }
}
