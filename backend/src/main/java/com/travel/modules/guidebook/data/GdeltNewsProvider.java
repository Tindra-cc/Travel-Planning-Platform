package com.travel.modules.guidebook.data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.chat.dto.IntentDto;

import lombok.extern.slf4j.Slf4j;

/**
 * 当地动态：GDELT DOC 2.0（免费、无 key）。标题与链接原样展示并标注「需核实」，
 * 不做摘要改写，避免把新闻标题当成事实写进行程。
 */
@Slf4j
@Component
public class GdeltNewsProvider implements TravelDataProvider {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_ARTICLES = 5;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final TravelDataProperties properties;

    public GdeltNewsProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder,
            TravelDataProperties properties) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
    }

    @Override
    public String source() {
        return "local_news";
    }

    @Override
    public Optional<DataBlock> fetch(IntentDto intent) {
        String destination = intent == null ? null : intent.getDestination();
        if (!StringUtils.hasText(destination) || !StringUtils.hasText(properties.getGdeltUrl())) {
            return Optional.empty();
        }
        try {
            String url = properties.getGdeltUrl() + "?query="
                    + java.net.URLEncoder.encode(destination, java.nio.charset.StandardCharsets.UTF_8)
                    + "&mode=artlist&maxrecords=" + MAX_ARTICLES + "&format=json&timespan=14d";
            String body = webClientBuilder.build().get()
                    .uri(url)
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
            JsonNode articles = objectMapper.readTree(body == null ? "{}" : body).path("articles");
            if (!articles.isArray() || articles.isEmpty()) {
                return Optional.empty();
            }
            List<Map<String, Object>> items = new ArrayList<>();
            StringBuilder summary = new StringBuilder();
            for (JsonNode article : articles) {
                String title = article.path("title").asText("").trim();
                if (title.isEmpty()) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", title);
                item.put("url", article.path("url").asText(""));
                item.put("source", article.path("domain").asText(""));
                item.put("seenAt", article.path("seendate").asText(""));
                items.add(item);
                if (summary.length() > 0) {
                    summary.append("；");
                }
                summary.append(title);
                if (items.size() >= MAX_ARTICLES) {
                    break;
                }
            }
            if (items.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("destination", destination);
            data.put("articles", items);
            data.put("note", "新闻标题仅供参考，出行前请核实");
            return Optional.of(DataBlock.ok(source(),
                    summary + "（需核实）", data,
                    LocalDateTime.now().format(TS)));
        } catch (Exception e) {
            log.info("当地动态获取失败 destination={}: {}", destination, e.getMessage());
            return Optional.empty();
        }
    }
}
