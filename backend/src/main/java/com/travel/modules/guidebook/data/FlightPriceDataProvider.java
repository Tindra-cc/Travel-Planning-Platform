package com.travel.modules.guidebook.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.travel.modules.chat.dto.IntentDto;

import lombok.extern.slf4j.Slf4j;

/**
 * flight_price 源：有 SerpAPI key 且能解析出发/到达机场时查 Google Flights；
 * 否则退回 app.travel-data.flight-price-url 配置的模板。两者都拿不到就返回 empty。
 */
@Slf4j
@Component
public class FlightPriceDataProvider implements TravelDataProvider {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final HttpPriceRangeClient client;
    private final TravelDataProperties properties;
    private final SerpApiClient serpApiClient;

    public FlightPriceDataProvider(HttpPriceRangeClient client, TravelDataProperties properties,
            SerpApiClient serpApiClient) {
        this.client = client;
        this.properties = properties;
        this.serpApiClient = serpApiClient;
    }

    @Override
    public String source() {
        return "flight_price";
    }

    @Override
    public Optional<DataBlock> fetch(IntentDto intent) {
        Optional<DataBlock> serp = fetchFromSerpApi(intent);
        if (serp.isPresent()) {
            return serp;
        }
        Optional<DataBlock> configured = client.fetch(source(), "机票",
                properties.getFlightPriceUrl(), intent);
        return configured.isPresent() ? configured : DemoTravelData.flight(intent, null);
    }

    private Optional<DataBlock> fetchFromSerpApi(IntentDto intent) {
        if (!serpApiClient.configured() || intent == null) {
            return Optional.empty();
        }
        String start = intent.getDateWindow() == null ? null : intent.getDateWindow().getStart();
        Optional<String> from = IataCodes.resolve(intent.getOrigin());
        Optional<String> to = IataCodes.resolve(intent.getDestination());
        if (!StringUtils.hasText(start) || from.isEmpty() || to.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("departure_id", from.get());
        params.put("arrival_id", to.get());
        params.put("outbound_date", start);
        String end = intent.getDateWindow().getEnd();
        if (StringUtils.hasText(end) && end.compareTo(start) > 0) {
            params.put("return_date", end);
        }
        params.put("currency", "CNY");
        params.put("hl", "zh-CN");
        params.put("gl", "cn");

        Optional<JsonNode> root = serpApiClient.search("google_flights", params);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int count = 0;
        String cheapestAirline = null;
        for (String listName : new String[] {"best_flights", "other_flights"}) {
            JsonNode list = root.get().path(listName);
            if (!list.isArray()) {
                continue;
            }
            for (JsonNode flight : list) {
                OptionalInt price = priceOf(flight);
                if (price.isEmpty()) {
                    continue;
                }
                count++;
                if (price.getAsInt() < min) {
                    min = price.getAsInt();
                    cheapestAirline = airlineOf(flight);
                }
                max = Math.max(max, price.getAsInt());
            }
        }
        if (count == 0) {
            return Optional.empty();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("origin", intent.getOrigin());
        data.put("destination", intent.getDestination());
        data.put("departureAirport", from.get());
        data.put("arrivalAirport", to.get());
        data.put("departureDate", start);
        if (params.containsKey("return_date")) {
            data.put("returnDate", params.get("return_date"));
        }
        data.put("currency", "CNY");
        data.put("min", min);
        data.put("max", max);
        data.put("sampledFlights", count);
        data.put("provider", "serpapi_google_flights");
        if (cheapestAirline != null) {
            data.put("cheapestAirline", cheapestAirline);
        }
        String summary = "机票参考价 " + min + "–" + max + " CNY（"
                + from.get() + "→" + to.get() + "，以下单页为准）";
        return Optional.of(DataBlock.ok(source(), summary, data,
                LocalDateTime.now().format(TS)));
    }

    private OptionalInt priceOf(JsonNode flight) {
        JsonNode price = flight.path("price");
        if (price.isNumber()) {
            return OptionalInt.of(price.asInt());
        }
        if (price.isTextual()) {
            try {
                return OptionalInt.of((int) Double.parseDouble(
                        price.asText().replaceAll("[^0-9.]", "")));
            } catch (NumberFormatException ignored) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }

    private String airlineOf(JsonNode flight) {
        JsonNode legs = flight.path("flights");
        if (legs.isArray() && !legs.isEmpty()) {
            String airline = legs.get(0).path("airline").asText("");
            return StringUtils.hasText(airline) ? airline : null;
        }
        return null;
    }
}
