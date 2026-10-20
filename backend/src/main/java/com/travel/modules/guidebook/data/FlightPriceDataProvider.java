package com.travel.modules.guidebook.data;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.travel.modules.chat.dto.IntentDto;

import lombok.RequiredArgsConstructor;

/** flight_price 源：由 app.travel-data.flight-price-url 配置。 */
@Component
@RequiredArgsConstructor
public class FlightPriceDataProvider implements TravelDataProvider {

    private final HttpPriceRangeClient client;
    private final TravelDataProperties properties;

    @Override
    public String source() {
        return "flight_price";
    }

    @Override
    public Optional<DataBlock> fetch(IntentDto intent) {
        return client.fetch(source(), "机票", properties.getFlightPriceUrl(), intent);
    }
}
