package com.travel.modules.guidebook.data;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.chat.dto.IntentDto;

import lombok.extern.slf4j.Slf4j;

/**
 * 可配置的价格区间适配器。响应约定 {min,max,currency,freshness}；
 * 未配置 URL、超时或字段缺失一律返回 empty，绝不估算价格。
 */
@Slf4j
@Component
public class HttpPriceRangeClient {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final TravelDataProperties properties;

    public HttpPriceRangeClient(ObjectMapper objectMapper, TravelDataProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<DataBlock> fetch(String source, String label, String template,
            IntentDto intent) {
        if (!StringUtils.hasText(template) || intent == null) {
            return Optional.empty();
        }
        String url = buildUrl(template, intent);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Accept", "application/json");
            if (StringUtils.hasText(properties.getPriceToken())) {
                builder.header("Authorization", "Bearer " + properties.getPriceToken());
            }
            HttpResponse<String> response = client.send(builder.GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !StringUtils.hasText(response.body())) {
                log.info("{}价格源返回异常 status={}", label, response.statusCode());
                return Optional.empty();
            }
            Map<String, Object> body = objectMapper.readValue(response.body(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Object min = body.get("min");
            Object max = body.get("max");
            Object currency = body.get("currency");
            if (min == null || max == null || currency == null) {
                return Optional.empty();
            }
            Map<String, Object> data = new LinkedHashMap<>(body);
            data.put("destination", intent.getDestination());
            if (intent.getDateWindow() != null) {
                data.put("start", intent.getDateWindow().getStart());
                data.put("end", intent.getDateWindow().getEnd());
            }
            String summary = label + "参考价 " + min + "–" + max + " " + currency
                    + "（以下单页为准）";
            return Optional.of(DataBlock.ok(source, summary, data,
                    LocalDateTime.now().format(TS)));
        } catch (Exception e) {
            log.info("{}价格源获取失败: {}", label, e.getMessage());
            return Optional.empty();
        }
    }

    private String buildUrl(String template, IntentDto intent) {
        StringBuilder url = new StringBuilder(template);
        char separator = template.contains("?") ? '&' : '?';
        url.append(separator).append("destination=").append(encode(intent.getDestination()));
        if (intent.getDateWindow() != null) {
            url.append("&start=").append(encode(intent.getDateWindow().getStart()))
                    .append("&end=").append(encode(intent.getDateWindow().getEnd()));
        }
        return url.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
