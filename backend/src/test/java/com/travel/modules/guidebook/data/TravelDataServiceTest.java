package com.travel.modules.guidebook.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.guidebook.entity.DataSourceConfig;
import com.travel.modules.guidebook.mapper.DataSourceConfigMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class TravelDataServiceTest {

    private static final String WEATHER_KEY = "travel:data:weather:成都:2026-11-01:2026-11-02";
    private static final String RATE_KEY = "travel:data:exchange:成都:2026-11-01:2026-11-02";

    @Mock
    private TravelDataProvider weatherProvider;
    @Mock
    private TravelDataProvider exchangeProvider;
    @Mock
    private DataSourceConfigMapper configMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    private ObjectMapper objectMapper;
    private TravelDataService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(values);
        lenient().when(configMapper.selectList(null)).thenReturn(List.of());
        lenient().when(weatherProvider.source()).thenReturn("weather");
        lenient().when(exchangeProvider.source()).thenReturn("exchange");
        objectMapper = new ObjectMapper();
        service = new TravelDataService(List.of(weatherProvider, exchangeProvider), configMapper,
                redis, objectMapper);
    }

    @Test
    void cacheHitDoesNotCallProviders() throws Exception {
        when(values.get(WEATHER_KEY)).thenReturn(objectMapper.writeValueAsString(
                DataBlock.ok("weather", "晴 15~22℃", Map.of(), "now")));
        when(values.get(RATE_KEY)).thenReturn(objectMapper.writeValueAsString(
                DataBlock.ok("exchange", "1 CNY ≈ 20.00 JPY", rates(), "now")));

        TravelDataSnapshot snapshot = service.fetch(TestFixtures.intent());

        assertThat(snapshot.weatherSummary()).isEqualTo("晴 15~22℃");
        assertThat(snapshot.exchangeSummary()).contains("JPY");
        assertThat(snapshot.unavailable()).isEmpty();
        verify(weatherProvider, never()).fetch(any());
        verify(exchangeProvider, never()).fetch(any());
    }

    @Test
    void providerEmptyResultsAreRecordedAsUnavailable() {
        when(values.get(anyString())).thenReturn(null);
        when(weatherProvider.fetch(any())).thenReturn(Optional.empty());
        when(exchangeProvider.fetch(any())).thenReturn(Optional.empty());

        TravelDataSnapshot snapshot = service.fetch(TestFixtures.intent());

        assertThat(snapshot.weatherSummary()).isNull();
        assertThat(snapshot.exchangeSummary()).isNull();
        assertThat(snapshot.exchangeRates()).isEmpty();
        assertThat(snapshot.unavailable())
                .containsExactly("weather_unavailable", "exchange_unavailable");
    }

    @Test
    void successfulFetchWritesCacheWithConfiguredTtl() {
        when(values.get(anyString())).thenReturn(null);
        when(configMapper.selectList(null)).thenReturn(List.of(
                config("weather", 10, 1800, true), config("exchange", 20, 21600, true)));
        when(weatherProvider.fetch(any())).thenReturn(Optional.of(
                DataBlock.ok("weather", "晴", Map.of(), "now")));
        when(exchangeProvider.fetch(any())).thenReturn(Optional.of(
                DataBlock.ok("exchange", "汇率", rates(), "now")));

        service.fetch(TestFixtures.intent());

        verify(values).set(eq(WEATHER_KEY), anyString(), eq(Duration.ofSeconds(1800)));
        verify(values).set(eq(RATE_KEY), anyString(), eq(Duration.ofSeconds(21600)));
    }

    @Test
    void disabledSourceIsSkippedEntirely() {
        when(values.get(anyString())).thenReturn(null);
        when(configMapper.selectList(null)).thenReturn(List.of(config("weather", 10, 1800, false)));
        when(exchangeProvider.fetch(any())).thenReturn(Optional.empty());

        TravelDataSnapshot snapshot = service.fetch(TestFixtures.intent());

        verify(weatherProvider, never()).fetch(any());
        assertThat(snapshot.unavailable()).containsExactly("exchange_unavailable");
    }

    @Test
    void providerExceptionIsIsolated() {
        when(values.get(anyString())).thenReturn(null);
        when(weatherProvider.fetch(any())).thenThrow(new RuntimeException("boom"));
        when(exchangeProvider.fetch(any())).thenReturn(Optional.empty());

        TravelDataSnapshot snapshot = service.fetch(TestFixtures.intent());

        assertThat(snapshot.unavailable()).contains("weather_unavailable");
    }

    @Test
    void providersRunInPriorityOrder() {
        when(values.get(anyString())).thenReturn(null);
        when(configMapper.selectList(null)).thenReturn(List.of(
                config("exchange", 10, 600, true), config("weather", 50, 600, true)));
        when(weatherProvider.fetch(any())).thenReturn(Optional.empty());
        when(exchangeProvider.fetch(any())).thenReturn(Optional.empty());

        service.fetch(TestFixtures.intent());

        InOrder order = inOrder(exchangeProvider, weatherProvider);
        order.verify(exchangeProvider).fetch(any());
        order.verify(weatherProvider).fetch(any());
    }

    @Test
    void failedSourceIsShortCachedToAvoidRefetching() {
        when(values.get(anyString())).thenReturn(null);
        when(redis.hasKey(anyString())).thenReturn(false);
        when(weatherProvider.fetch(any())).thenReturn(Optional.empty());
        when(exchangeProvider.fetch(any())).thenReturn(Optional.empty());

        service.fetch(TestFixtures.intent());

        verify(values).set(eq(WEATHER_KEY + ":miss"), eq("1"), eq(Duration.ofSeconds(300)));
    }

    @Test
    void recentFailureSkipsProvider() {
        when(values.get(anyString())).thenReturn(null);
        when(redis.hasKey(WEATHER_KEY + ":miss")).thenReturn(true);
        when(exchangeProvider.fetch(any())).thenReturn(Optional.empty());

        TravelDataSnapshot snapshot = service.fetch(TestFixtures.intent());

        verify(weatherProvider, never()).fetch(any());
        assertThat(snapshot.unavailable())
                .containsExactlyInAnyOrder("weather_unavailable", "exchange_unavailable");
    }

    @Test
    void redisReadFailureDoesNotBlockGeneration() {
        when(values.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(weatherProvider.fetch(any())).thenReturn(Optional.empty());
        when(exchangeProvider.fetch(any())).thenReturn(Optional.empty());

        assertThatCode(() -> service.fetch(TestFixtures.intent())).doesNotThrowAnyException();
    }

    @Test
    void exchangeSummaryIsFormatted() throws Exception {
        when(values.get(WEATHER_KEY)).thenReturn(objectMapper.writeValueAsString(
                DataBlock.ok("weather", "晴", Map.of(), "now")));
        when(values.get(RATE_KEY)).thenReturn(objectMapper.writeValueAsString(
                DataBlock.ok("exchange", "1 CNY ≈ 20.00 JPY，0.14 USD", rates(), "now")));

        TravelDataSnapshot snapshot = service.fetch(TestFixtures.intent());

        assertThat(snapshot.exchangeSummary()).isEqualTo("1 CNY ≈ 20.00 JPY，0.14 USD");
        assertThat(snapshot.exchangeRates()).containsEntry("JPY", 20.0);
    }

    private DataSourceConfig config(String source, int priority, int ttl, boolean enabled) {
        DataSourceConfig config = new DataSourceConfig();
        config.setSourceId(source);
        config.setPriority(priority);
        config.setCacheTtlSeconds(ttl);
        config.setEnabled(enabled);
        return config;
    }

    private Map<String, Object> rates() {
        Map<String, Object> rates = new LinkedHashMap<>();
        rates.put("JPY", 20.0);
        rates.put("USD", 0.14);
        return rates;
    }
}
