package com.travel.modules.guidebook.data;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.travel.modules.chat.dto.IntentDto;

import lombok.RequiredArgsConstructor;

/** hotel_price 源：由 app.travel-data.hotel-price-url 配置。 */
@Component
@RequiredArgsConstructor
public class HotelPriceDataProvider implements TravelDataProvider {

    private final HttpPriceRangeClient client;
    private final TravelDataProperties properties;

    @Override
    public String source() {
        return "hotel_price";
    }

    @Override
    public Optional<DataBlock> fetch(IntentDto intent) {
        return client.fetch(source(), "酒店", properties.getHotelPriceUrl(), intent);
    }
}
