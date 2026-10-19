package com.travel.modules.guidebook.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherSnapshot {
    private String destination;
    private String start;
    private String end;
    private String summary;
    private String fetchedAt;
}
