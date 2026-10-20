package com.travel.modules.guidebook.data;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/** 外部数据源配置；未配置 URL 的价格源会直接标为 unavailable，而不是猜一个价格。 */
@Data
@ConfigurationProperties(prefix = "app.travel-data")
public class TravelDataProperties {

    private String gdeltUrl = "https://api.gdeltproject.org/api/v2/doc/doc";
    private String nominatimUrl = "https://nominatim.openstreetmap.org/search";
    private String overpassUrl = "https://overpass-api.de/api/interpreter";
    private String flightPriceUrl;
    private String hotelPriceUrl;
    private String priceToken;
    private int timeoutSeconds = 4;
}
