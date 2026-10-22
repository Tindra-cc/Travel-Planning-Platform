package com.travel.modules.guidebook.data;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * SerpAPI 统一入口（google_news / google_flights / google_hotels）。
 * 未配置 key 或不返回 200 时返回 empty，由聚合层标记 unavailable；key 不写日志。
 */
@Slf4j
@Component
public class SerpApiClient {

    private final ObjectMapper objectMapper;
    private final TravelDataProperties properties;

    public SerpApiClient(ObjectMapper objectMapper, TravelDataProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public boolean configured() {
        return StringUtils.hasText(properties.getSerpApiKey())
                && StringUtils.hasText(properties.getSerpApiUrl());
    }

    public Optional<JsonNode> search(String engine, Map<String, String> params) {
        if (!configured()) {
            return Optional.empty();
        }
        String url = properties.getSerpApiUrl()
                + (properties.getSerpApiUrl().contains("?") ? "&" : "?")
                + "engine=" + encode(engine)
                + "&api_key=" + encode(properties.getSerpApiKey())
                + query(params);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !StringUtils.hasText(response.body())) {
                log.info("SerpAPI 返回异常 engine={} status={}", engine, response.statusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.hasNonNull("error")) {
                log.info("SerpAPI 返回错误 engine={}: {}", engine, root.path("error").asText());
                return Optional.empty();
            }
            return Optional.of(root);
        } catch (Exception e) {
            log.info("SerpAPI 请求失败 engine={}: {}", engine, e.getMessage());
            return Optional.empty();
        }
    }

    private String query(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!StringUtils.hasText(entry.getValue())) {
                continue;
            }
            sb.append('&').append(encode(entry.getKey())).append('=')
                    .append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
