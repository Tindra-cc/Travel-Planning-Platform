package com.travel.modules.guidebook.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class TravelDataServiceTest {

    private static final String WEATHER_KEY = "travel:weather:成都:2026-11-01:2026-11-02";
    private static final String RATE_KEY = "travel:rate:cny";

    @Mock
    private OpenMeteoWeatherProvider weatherProvider;
    @Mock
    private ExchangeRateProvider exchangeRateProvider;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    private ObjectMapper objectMapper;
    private TravelDataService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(values);
        objectMapper = new ObjectMapper();
        service = new TravelDataService(weatherProvider, exchangeRateProvider, redis, objectMapper);
    }

    @Test
    void cacheHitDoesNotCallProviders() throws Exception {
        when(values.get(WEATHER_KEY)).thenReturn(objectMapper.writeValueAsString(
                new WeatherSnapshot("成都", "2026-11-01", "2026-11-02", "晴 15~22℃", "now")));
        when(values.get(RATE_KEY)).thenReturn(objectMapper.writeValueAsString(
                new ExchangeRateSnapshot("CNY", rates(), "now")));

        TravelDataSnapshot snapshot = service.fetch(TestFixtures.intent());

        assertThat(snapshot.weatherSummary()).isEqualTo("晴 15~22℃");
        assertThat(snapshot.exchangeSummary()).contains("JPY");
        assertThat(snapshot.unavailable()).isEmpty();
        verifyNoInteractions(weatherProvider, exchangeRateProvider);
    }

    @Test
    void providerEmptyResultsAreRecordedAsUnavailable() {
        when(values.get(anyString())).thenReturn(null);
        when(weatherProvider.fetch("成都", "2026-11-01", "2026-11-02"))
                .thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchCnyRates()).thenReturn(Optional.empty());

        TravelDataSnapshot snapshot = service.fetch(TestFixtures.intent());

        assertThat(snapshot.weatherSummary()).isNull();
        assertThat(snapshot.exchangeSummary()).isNull();
        assertThat(snapshot.exchangeRates()).isEmpty();
        assertThat(snapshot.unavailable())
                .containsExactly("weather_unavailable", "exchange_unavailable");
    }

    @Test
    void successfulFetchWritesCache() {
        when(values.get(anyString())).thenReturn(null);
        when(weatherProvider.fetch("成都", "2026-11-01", "2026-11-02"))
                .thenReturn(Optional.of(new WeatherSnapshot("成都", "2026-11-01",
                        "2026-11-02", "晴", "now")));
        when(exchangeRateProvider.fetchCnyRates())
                .thenReturn(Optional.of(new ExchangeRateSnapshot("CNY", rates(), "now")));

        service.fetch(TestFixtures.intent());

        verify(values).set(eq(WEATHER_KEY), anyString(), eq(Duration.ofMinutes(30)));
        verify(values).set(eq(RATE_KEY), anyString(), eq(Duration.ofHours(6)));
    }

    @Test
    void missingDateWindowSkipsWeatherProvider() {
        IntentDto intent = TestFixtures.intent();
        intent.setDateWindow(null);
        when(values.get(anyString())).thenReturn(null);
        when(exchangeRateProvider.fetchCnyRates()).thenReturn(Optional.empty());

        TravelDataSnapshot snapshot = service.fetch(intent);

        verify(weatherProvider, never()).fetch(anyString(), anyString(), anyString());
        assertThat(snapshot.unavailable()).contains("weather_unavailable");
    }

    @Test
    void redisReadFailureDoesNotBlockGeneration() {
        when(values.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(weatherProvider.fetch("成都", "2026-11-01", "2026-11-02"))
                .thenReturn(Optional.empty());
        when(exchangeRateProvider.fetchCnyRates()).thenReturn(Optional.empty());

        assertThatCode(() -> service.fetch(TestFixtures.intent())).doesNotThrowAnyException();
    }

    @Test
    void exchangeSummaryIsFormatted() throws Exception {
        when(values.get(WEATHER_KEY)).thenReturn(objectMapper.writeValueAsString(
                new WeatherSnapshot("成都", "2026-11-01", "2026-11-02", "晴", "now")));
        when(values.get(RATE_KEY)).thenReturn(objectMapper.writeValueAsString(
                new ExchangeRateSnapshot("CNY", rates(), "now")));

        TravelDataSnapshot snapshot = service.fetch(TestFixtures.intent());

        assertThat(snapshot.exchangeSummary()).isEqualTo("1 CNY ≈ 20.00 JPY，0.14 USD");
        assertThat(snapshot.exchangeRates()).containsEntry("JPY", 20.0);
    }

    private Map<String, Double> rates() {
        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put("JPY", 20.0);
        rates.put("USD", 0.14);
        return rates;
    }
}
