package com.travel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger UI 装配：全局信息 + Bearer 鉴权按钮。
 * 受保护接口先在 /api/auth/login 拿 token，再点 Authorize 粘贴（只需粘 token 本身）。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "BearerAuth";

    @Bean
    public OpenAPI travelOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 旅行规划助理 API")
                        .version("v1")
                        .description("""
                                Sprint 1：鉴权(§2) / 用户画像(FR-1 §3) / 对话会话(FR-2 §4)。

                                鉴权方式：先调 POST /api/auth/login 拿到 token，
                                再点右上角 Authorize 粘贴 token 本身（无需带 "Bearer " 前缀）。
                                统一响应体 {code, message, requestId, data}，成功 code=0。
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("Sa-Token")
                                .description("Sa-Token 登录凭证")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
