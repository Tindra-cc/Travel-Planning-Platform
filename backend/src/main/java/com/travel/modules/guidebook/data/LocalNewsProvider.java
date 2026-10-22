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
 * 当地动态：优先 SerpAPI google_news（有 key 时），失败再退回 GDELT DOC 2.0。
 * 标题与链接原样展示并标注「需核实」，不做摘要改写，避免把新闻标题当成事实写进行程。
 */
@Slf4j
@Component
public class LocalNewsProvider implements TravelDataProvider {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_ARTICLES = 5;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final TravelDataProperties properties;
    private final SerpApiClient serpApiClient;

    public LocalNewsProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder,
            TravelDataProperties properties, SerpApiClient serpApiClient) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
        this.serpApiClient = serpApiClient;
    }

    @Override
    public String source() {
        return "local_news";
    }

    @Override
    public Optional<DataBlock> fetch(IntentDto intent) {
        String destination = intent == null ? null : intent.getDestination();
        if (!StringUtils.hasText(destination)) {
            return Optional.empty();
        }
        Optional<DataBlock> serp = fetchFromSerpApi(destination);
        if (serp.isPresent()) {
            return serp;
        }
        Optional<DataBlock> gdelt = fetchFromGdelt(destination);
        return gdelt.isPresent() ? gdelt : serp;
    }

    private Optional<DataBlock> fetchFromSerpApi(String destination) {
        if (!serpApiClient.configured()) {
            return Optional.empty();
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", destination + " 旅游");
        params.put("gl", "cn");
        params.put("hl", "zh-CN");
        Optional<JsonNode> root = serpApiClient.search("google_news", params);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        JsonNode results = root.get().path("news_results");
        if (!results.isArray()) {
            return Optional.empty();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode node : results) {
            // 有时 google_news 返回分组结构 {stories:[...]}，把子项展开。
            if (node.has("stories") && node.get("stories").isArray()) {
                for (JsonNode story : node.get("stories")) {
                    addItem(items, story);
                }
            } else {
                addItem(items, node);
            }
            if (items.size() >= MAX_ARTICLES) {
                break;
            }
        }
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(block(destination, items, "SerpAPI Google News", "serpapi"));
    }

    private void addItem(List<Map<String, Object>> items, JsonNode node) {
        if (items.size() >= MAX_ARTICLES) {
            return;
        }
        String title = node.path("title").asText("").trim();
        if (title.isEmpty()) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("url", node.path("link").asText(""));
        JsonNode source = node.path("source");
        item.put("source", source.isObject() ? source.path("name").asText("")
                : source.asText(""));
        item.put("seenAt", node.path("iso_date").asText(node.path("date").asText("")));
        items.add(item);
    }

    private Optional<DataBlock> fetchFromGdelt(String destination) {
        if (!StringUtils.hasText(properties.getGdeltUrl())) {
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
                if (items.size() >= MAX_ARTICLES) {
                    break;
                }
            }
            if (items.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(block(destination, items, "GDELT", "gdelt"));
        } catch (Exception e) {
            log.info("GDELT 当地动态获取失败 destination={}: {}", destination, e.getMessage());
            return Optional.empty();
        }
    }

    private DataBlock block(String destination, List<Map<String, Object>> items,
            String sourceName, String provider) {
        String summary = items.stream().limit(3)
                .map(item -> String.valueOf(item.get("title")))
                .reduce((a, b) -> a + "；" + b).orElse("");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("destination", destination);
        data.put("articles", items);
        data.put("provider", provider);
        data.put("sourceName", sourceName);
        data.put("note", "新闻标题仅供参考，出行前请核实");
        return DataBlock.ok(source(), summary + "（需核实）", data,
                LocalDateTime.now().format(TS));
    }
}
