package com.travel.modules.guidebook.data;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 通过 Nominatim 地理编码 + Overpass 查询 OSM 的 opening_hours。
 * 任何一步失败都返回 empty，由调用方保持字段为空，绝不猜测营业时间。
 */
@Slf4j
@Component
public class OsmOpeningHoursProvider {

    private static final String CACHE_PREFIX = "travel:poi:hours:";
    private static final String MISS = "-";
    private static final long HIT_TTL_DAYS = 7;
    private static final long MISS_TTL_HOURS = 24;
    private static final int SEARCH_RADIUS_METERS = 300;

    private final WebClient.Builder webClientBuilder;
    private final TravelDataProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;

    public OsmOpeningHoursProvider(WebClient.Builder webClientBuilder,
            TravelDataProperties properties, ObjectMapper objectMapper,
            StringRedisTemplate redis) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redis = redis;
    }

    public Optional<String> lookup(String destination, String placeName) {
        if (!StringUtils.hasText(destination) || !StringUtils.hasText(placeName)) {
            return Optional.empty();
        }
        String key = CACHE_PREFIX + normalize(destination) + ":" + normalize(placeName);
        Optional<String> cached = readCache(key);
        if (cached.isPresent()) {
            return cached.filter(value -> !MISS.equals(value));
        }
        try {
            Optional<String> hours = fetch(destination, placeName);
            writeCache(key, hours.orElse(MISS), hours.isPresent()
                    ? Duration.ofDays(HIT_TTL_DAYS) : Duration.ofHours(MISS_TTL_HOURS));
            return hours;
        } catch (Exception e) {
            log.info("营业时间查询失败 place={} destination={}: {}", placeName, destination,
                    e.getMessage());
            return Optional.empty();
        }
    }

    /** 返回 empty 表示缓存未命中；命中否定缓存时返回内容是 MISS。 */
    private Optional<String> readCache(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? Optional.empty() : Optional.of(value);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private void writeCache(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (RuntimeException e) {
            log.debug("营业时间缓存写入失败: {}", e.getMessage());
        }
    }

    private Optional<String> fetch(String destination, String placeName) throws Exception {
        Duration timeout = Duration.ofSeconds(Math.max(2, properties.getTimeoutSeconds()));
        WebClient client = webClientBuilder.build();

        String geocodeUrl = properties.getNominatimUrl()
                + "?format=jsonv2&limit=1&q="
                + java.net.URLEncoder.encode(placeName + ", " + destination,
                        java.nio.charset.StandardCharsets.UTF_8);
        String geocodeBody = client.get().uri(geocodeUrl)
                .header("User-Agent", "TravelPlanningPlatform/1.0 (opening-hours enrichment)")
                .retrieve().bodyToMono(String.class).block(timeout);
        JsonNode places = objectMapper.readTree(geocodeBody == null ? "[]" : geocodeBody);
        if (!places.isArray() || places.isEmpty()) {
            return Optional.empty();
        }
        double lat = places.get(0).path("lat").asDouble(Double.NaN);
        double lon = places.get(0).path("lon").asDouble(Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return Optional.empty();
        }

        String query = "[out:json][timeout:" + Math.max(2, properties.getTimeoutSeconds()) + "];"
                + "nwr(around:" + SEARCH_RADIUS_METERS + "," + lat + "," + lon + ")"
                + "[\"name\"=\"" + escape(placeName) + "\"][\"opening_hours\"];"
                + "out tags 5;";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("data", query);
        String overpassBody = client.post().uri(properties.getOverpassUrl())
                .header("User-Agent", "TravelPlanningPlatform/1.0 (opening-hours enrichment)")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve().bodyToMono(String.class).block(timeout);
        JsonNode elements = objectMapper.readTree(overpassBody == null ? "{}" : overpassBody)
                .path("elements");
        if (!elements.isArray()) {
            return Optional.empty();
        }
        for (JsonNode element : elements) {
            String hours = element.path("tags").path("opening_hours").asText("").trim();
            if (!hours.isEmpty()) {
                return Optional.of(hours);
            }
        }
        return Optional.empty();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
    }
}
