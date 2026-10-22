package com.travel.modules.guidebook.data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.entity.DataSourceConfig;
import com.travel.modules.guidebook.mapper.DataSourceConfigMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 实时数据聚合与 Redis 缓存。所有 key 带 travel: 前缀（dev 与其它应用共用 db0）。
 * 每个源独立失败，只把对应 DataBlock 标为 unavailable，不阻断生成。
 */
@Slf4j
@Service
public class TravelDataService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int DEFAULT_PRIORITY = 100;
    private static final int DEFAULT_TTL_SECONDS = 1800;
    /** 数据层总预算；超时后剩余源直接标 unavailable，避免拖垮生成。 */
    private static final int BUDGET_SECONDS = 15;
    /** 失败结果短缓存：避免同一目的地反复打外部接口（SerpAPI 免费额度有限）。 */
    private static final Duration MISS_TTL = Duration.ofSeconds(300);

    private final List<TravelDataProvider> providers;
    private final DataSourceConfigMapper configMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TravelDataService(List<TravelDataProvider> providers,
            DataSourceConfigMapper configMapper, StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.providers = providers;
        this.configMapper = configMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public TravelDataSnapshot fetch(IntentDto intent) {
        String fetchedAt = LocalDateTime.now().format(TS);
        Map<String, DataSourceConfig> configs = loadConfigs();
        List<TravelDataProvider> ordered = new ArrayList<>(providers);
        ordered.sort(Comparator.comparingInt(p -> priority(configs, p.source())));

        LocalDateTime deadline = LocalDateTime.now().plusSeconds(BUDGET_SECONDS);
        List<DataBlock> blocks = new ArrayList<>();
        for (TravelDataProvider provider : ordered) {
            DataSourceConfig config = configs.get(provider.source());
            if (config != null && Boolean.FALSE.equals(config.getEnabled())) {
                continue;
            }
            if (LocalDateTime.now().isAfter(deadline)) {
                blocks.add(DataBlock.unavailable(provider.source(), fetchedAt));
                continue;
            }
            Optional<DataBlock> block = fetchWithCache(provider, config, intent, fetchedAt);
            blocks.add(block.orElseGet(() -> DataBlock.unavailable(provider.source(), fetchedAt)));
        }
        return new TravelDataSnapshot(fetchedAt, blocks);
    }

    private Optional<DataBlock> fetchWithCache(TravelDataProvider provider,
            DataSourceConfig config, IntentDto intent, String fetchedAt) {
        String key = cacheKey(provider.source(), intent);
        Optional<DataBlock> cached = read(key);
        if (cached.isPresent()) {
            return cached;
        }
        if (failedRecently(key)) {
            return Optional.empty();
        }
        Optional<DataBlock> fetched;
        try {
            fetched = provider.fetch(intent);
        } catch (Exception e) {
            log.info("实时数据源异常 source={}: {}", provider.source(), e.getMessage());
            markFailed(key);
            return Optional.empty();
        }
        if (fetched.isPresent()) {
            write(key, fetched.get(), ttl(config));
        } else {
            markFailed(key);
        }
        return fetched;
    }

    private boolean failedRecently(String key) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(key + ":miss"));
        } catch (Exception e) {
            log.debug("读取失败缓存失败 key={}: {}", key, e.getMessage());
            return false;
        }
    }

    private void markFailed(String key) {
        try {
            redisTemplate.opsForValue().set(key + ":miss", "1", MISS_TTL);
        } catch (Exception e) {
            log.debug("写入失败缓存失败 key={}: {}", key, e.getMessage());
        }
    }

    private Map<String, DataSourceConfig> loadConfigs() {
        try {
            Map<String, DataSourceConfig> result = new HashMap<>();
            for (DataSourceConfig config : configMapper.selectList(null)) {
                result.put(config.getSourceId(), config);
            }
            return result;
        } catch (Exception e) {
            log.warn("读取数据源配置失败，使用默认值: {}", e.getMessage());
            return Map.of();
        }
    }

    private int priority(Map<String, DataSourceConfig> configs, String source) {
        DataSourceConfig config = configs.get(source);
        return config == null || config.getPriority() == null
                ? DEFAULT_PRIORITY : config.getPriority();
    }

    private Duration ttl(DataSourceConfig config) {
        int seconds = config == null || config.getCacheTtlSeconds() == null
                ? DEFAULT_TTL_SECONDS : Math.max(0, config.getCacheTtlSeconds());
        return Duration.ofSeconds(seconds);
    }

    private String cacheKey(String source, IntentDto intent) {
        String destination = intent == null ? null : intent.getDestination();
        String start = intent == null || intent.getDateWindow() == null
                ? null : intent.getDateWindow().getStart();
        String end = intent == null || intent.getDateWindow() == null
                ? null : intent.getDateWindow().getEnd();
        return "travel:data:" + source + ":"
                + (StringUtils.hasText(destination) ? destination : "-") + ":"
                + (StringUtils.hasText(start) ? start : "-") + ":"
                + (StringUtils.hasText(end) ? end : "-");
    }

    private Optional<DataBlock> read(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, DataBlock.class));
        } catch (Exception e) {
            log.debug("读取实时数据缓存失败 key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void write(String key, DataBlock block, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(block), ttl);
        } catch (Exception e) {
            log.debug("写入实时数据缓存失败 key={}: {}", key, e.getMessage());
        }
    }
}
