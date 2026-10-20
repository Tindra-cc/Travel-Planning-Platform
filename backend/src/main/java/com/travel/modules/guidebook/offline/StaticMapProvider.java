package com.travel.modules.guidebook.offline;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 静态地图截图（尽力而为）：失败只返回 empty，由离线包降级为坐标 + 地图链接。
 * 图片以 base64 缓存在 Redis，避免重复请求第三方。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaticMapProvider {

    private static final long MAX_BYTES = 3L * 1024 * 1024;
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "travel:map:";

    private final OfflinePackProperties properties;
    private final StringRedisTemplate redisTemplate;

    public Optional<MapImage> fetch(double lat, double lng) {
        String template = properties.getMapUrlTemplate();
        if (!StringUtils.hasText(template)) {
            return Optional.empty();
        }
        String url = template.replace("{lat}", String.format("%.5f", lat))
                .replace("{lng}", String.format("%.5f", lng));
        String key = KEY_PREFIX + String.format("%.5f", lat) + ":" + String.format("%.5f", lng);

        Optional<MapImage> cached = readCache(key);
        if (cached.isPresent()) {
            return cached;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.getMapTimeoutSeconds()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(properties.getMapTimeoutSeconds()))
                    .header("User-Agent", "TravelPlanningPlatform/1.0 (offline pack)")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body() == null
                    || response.body().length == 0
                    || response.body().length > MAX_BYTES) {
                log.info("静态地图不可用 status={} url={}", response.statusCode(), url);
                return Optional.empty();
            }
            String type = detectType(response.body());
            if (type == null) {
                return Optional.empty();
            }
            MapImage image = new MapImage(response.body(), type);
            writeCache(key, image);
            return Optional.of(image);
        } catch (Exception e) {
            log.info("静态地图获取失败 url={}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    private String detectType(byte[] body) {
        if (body.length > 8 && (body[0] & 0xFF) == 0x89 && body[1] == 'P') {
            return "image/png";
        }
        if (body.length > 2 && (body[0] & 0xFF) == 0xFF && (body[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        return null;
    }

    private Optional<MapImage> readCache(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return Optional.empty();
            }
            int split = value.indexOf('|');
            if (split <= 0) {
                return Optional.empty();
            }
            return Optional.of(new MapImage(
                    Base64.getDecoder().decode(value.substring(split + 1)),
                    value.substring(0, split)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void writeCache(String key, MapImage image) {
        try {
            redisTemplate.opsForValue().set(key,
                    image.type() + "|" + Base64.getEncoder().encodeToString(image.bytes()),
                    CACHE_TTL);
        } catch (Exception e) {
            log.debug("静态地图缓存写入失败: {}", e.getMessage());
        }
    }

    public record MapImage(byte[] bytes, String type) {
    }
}
