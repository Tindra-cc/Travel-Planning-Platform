package com.travel.config;

import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MyBatis-Plus 的 JacksonTypeHandler 默认使用裸 ObjectMapper，无法序列化
 * LocalDateTime（分享快照会因此 500）。这里统一换成 Spring 管理的 ObjectMapper。
 */
@Configuration
public class MybatisJacksonConfig {

    public MybatisJacksonConfig(ObjectMapper objectMapper) {
        JacksonTypeHandler.setObjectMapper(objectMapper);
    }
}
