package com.travel.modules.guidebook.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.travel.modules.chat.dto.IntentDto;

import lombok.extern.slf4j.Slf4j;

/**
 * hotel_price 源：有 SerpAPI key 时查 Google Hotels 的每晚价格区间；
 * 否则退回 app.travel-data.hotel-price-url 配置的模板。拿不到就返回 empty。
 */
@Slf4j
@Component
public class HotelPriceDataProvider implements TravelDataProvider {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_SAMPLES = 3;

    private final HttpPriceRangeClient client;
    private final TravelDataProperties properties;
    private final SerpApiClient serpApiClient;

    public HotelPriceDataProvider(HttpPriceRangeClient client, TravelDataProperties properties,
            SerpApiClient serpApiClient) {
        this.client = client;
        this.properties = properties;
        this.serpApiClient = serpApiClient;
    }

    @Override
    public String source() {
        return "hotel_price";
    }

    @Override
    public Optional<DataBlock> fetch(IntentDto intent) {
        Optional<DataBlock> serp = fetchFromSerpApi(intent);
        if (serp.isPresent()) {
            return serp;
        }
        return client.fetch(source(), "酒店", properties.getHotelPriceUrl(), intent);
    }

    private Optional<DataBlock> fetchFromSerpApi(IntentDto intent) {
        if (!serpApiClient.configured() || intent == null
                || !StringUtils.hasText(intent.getDestination())) {
            return Optional.empty();
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", intent.getDestination());
        if (intent.getDateWindow() != null) {
            params.put("check_in_date", intent.getDateWindow().getStart());
            params.put("check_out_date", intent.getDateWindow().getEnd());
        }
        Integer adults = intent.getParty() == null ? null : intent.getParty().getAdults();
        if (adults != null && adults > 0) {
            params.put("adults", String.valueOf(adults));
        }
        params.put("currency", "CNY");
        params.put("gl", "cn");
        params.put("hl", "zh-CN");

        Optional<JsonNode> root = serpApiClient.search("google_hotels", params);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        JsonNode propertiesNode = root.get().path("properties");
        if (!propertiesNode.isArray()) {
            return Optional.empty();
        }
        double min = Double.MAX_VALUE;
        double max = 0;
        boolean found = false;
        List<String> samples = new ArrayList<>();
        for (JsonNode property : propertiesNode) {
            JsonNode nightly = property.path("rate_per_night").path("extracted_lowest");
            if (nightly.isMissingNode() || nightly.isNull()) {
                continue;
            }
            double value;
            if (nightly.isNumber()) {
                value = nightly.asDouble();
            } else {
                String digits = nightly.asText("").replaceAll("[^0-9.]", "");
                if (digits.isEmpty()) {
                    continue;
                }
                try {
                    value = Double.parseDouble(digits);
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
            if (value <= 0) {
                continue;
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
            found = true;
            if (samples.size() < MAX_SAMPLES) {
                String name = property.path("name").asText("").trim();
                if (!name.isEmpty()) {
                    samples.add(name);
                }
            }
        }
        if (!found) {
            return Optional.empty();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("destination", intent.getDestination());
        if (intent.getDateWindow() != null) {
            data.put("checkIn", intent.getDateWindow().getStart());
            data.put("checkOut", intent.getDateWindow().getEnd());
        }
        data.put("currency", "CNY");
        data.put("min", Math.round(min));
        data.put("max", Math.round(max));
        data.put("sampleHotels", samples);
        data.put("provider", "serpapi_google_hotels");
        String summary = "酒店参考价 " + Math.round(min) + "–" + Math.round(max)
                + " CNY/晚（以下单页为准）";
        return Optional.of(DataBlock.ok(source(), summary, data,
                LocalDateTime.now().format(TS)));
    }
}
