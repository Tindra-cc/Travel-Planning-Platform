package com.travel.modules.guidebook.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class SerpApiProvidersTest {

    @Mock
    private SerpApiClient serpApiClient;
    @Mock
    private HttpPriceRangeClient httpClient;
    @Mock
    private WebClient.Builder webClientBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TravelDataProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TravelDataProperties();
        properties.setSerpApiKey("test-key");
    }

    @Test
    void flightParsesMinMaxFromGoogleFlights() throws Exception {
        when(serpApiClient.configured()).thenReturn(true);
        when(serpApiClient.search(eq("google_flights"), anyMap())).thenReturn(Optional.of(json(
                "{\"best_flights\":[{\"price\":1800,\"flights\":[{\"airline\":\"Air China\"}]}],"
                + "\"other_flights\":[{\"price\":1200,\"flights\":[{\"airline\":\"ANA\"}]},"
                + "{\"price\":2100,\"flights\":[{\"airline\":\"JAL\"}]}]}")));

        Optional<DataBlock> block = new FlightPriceDataProvider(httpClient, properties,
                serpApiClient).fetch(intent("北京", "东京"));

        assertThat(block).isPresent();
        assertThat(block.get().available()).isTrue();
        assertThat(block.get().summary()).contains("1200–2100", "PEK→TYO");
        assertThat(block.get().data()).containsEntry("min", 1200)
                .containsEntry("max", 2100)
                .containsEntry("cheapestAirline", "ANA");
    }

    @Test
    void flightFallsBackToTemplateWhenSerpApiUnavailable() {
        when(serpApiClient.configured()).thenReturn(false);
        when(httpClient.fetch(eq("flight_price"), any(), any(), any()))
                .thenReturn(Optional.empty());

        new FlightPriceDataProvider(httpClient, properties, serpApiClient)
                .fetch(intent("北京", "东京"));

        verify(serpApiClient, never()).search(any(), anyMap());
        verify(httpClient).fetch(eq("flight_price"), any(), any(), any());
    }

    @Test
    void flightSkipsSerpApiWhenOriginMissing() {
        when(serpApiClient.configured()).thenReturn(true);
        when(httpClient.fetch(eq("flight_price"), any(), any(), any()))
                .thenReturn(Optional.empty());

        new FlightPriceDataProvider(httpClient, properties, serpApiClient)
                .fetch(intent(null, "东京"));

        verify(serpApiClient, never()).search(any(), anyMap());
    }

    @Test
    void hotelParsesNightlyRange() throws Exception {
        when(serpApiClient.configured()).thenReturn(true);
        when(serpApiClient.search(eq("google_hotels"), anyMap())).thenReturn(Optional.of(json(
                "{\"properties\":["
                + "{\"name\":\"酒店A\",\"rate_per_night\":{\"extracted_lowest\":450.0}},"
                + "{\"name\":\"酒店B\",\"rate_per_night\":{\"extracted_lowest\":\"¥680\"}}]}")));

        Optional<DataBlock> block = new HotelPriceDataProvider(httpClient, properties,
                serpApiClient).fetch(intent("北京", "成都"));

        assertThat(block).isPresent();
        assertThat(block.get().summary()).contains("450–680");
        assertThat(block.get().data()).containsEntry("min", 450L)
                .containsEntry("max", 680L)
                .containsEntry("sampleHotels", java.util.List.of("酒店A", "酒店B"));
    }

    @Test
    void newsParsesSerpApiResults() throws Exception {
        when(serpApiClient.configured()).thenReturn(true);
        when(serpApiClient.search(eq("google_news"), anyMap())).thenReturn(Optional.of(json(
                "{\"news_results\":["
                + "{\"title\":\"成都地铁新线开通\",\"link\":\"https://a.example/1\","
                + "\"source\":{\"name\":\"四川日报\"},\"iso_date\":\"2026-10-20\"},"
                + "{\"title\":\"成都秋日游玩指南\",\"link\":\"https://a.example/2\","
                + "\"source\":{\"name\":\"文旅成都\"},\"iso_date\":\"2026-10-18\"}]}")));

        Optional<DataBlock> block = new LocalNewsProvider(objectMapper, webClientBuilder,
                properties, serpApiClient).fetch(intent("北京", "成都"));

        assertThat(block).isPresent();
        assertThat(block.get().summary()).contains("成都地铁新线开通", "需核实");
        assertThat(block.get().data()).containsEntry("provider", "serpapi");
    }

    private IntentDto intent(String origin, String destination) {
        IntentDto intent = TestFixtures.intent();
        intent.setOrigin(origin);
        intent.setDestination(destination);
        return intent;
    }

    private JsonNode json(String raw) throws Exception {
        return objectMapper.readTree(raw);
    }
}
