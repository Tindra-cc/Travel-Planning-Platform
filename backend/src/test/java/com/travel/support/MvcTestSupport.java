package com.travel.support;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.GlobalExceptionHandler;

/** 不启动 Spring 容器的 Controller 契约测试脚手架。 */
public final class MvcTestSupport {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private MvcTestSupport() {
    }

    public static MockMvc mockMvc(Object controller) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    public static byte[] json(Object body) {
        try {
            return MAPPER.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
