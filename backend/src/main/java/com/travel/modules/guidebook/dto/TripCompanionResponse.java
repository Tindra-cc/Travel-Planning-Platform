package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.Data;

@Data
public class TripCompanionResponse {

    private Long guidebookId;
    private String status;
    private String destination;
    private String startDate;
    private String endDate;
    //今天对应行程的第几天 非出行期为null
    private Integer currentDay;
    private Integer executedCount;
    private Integer skippedCount;
    private Integer rescheduledCount;
    private Integer totalCount;
    private Double progress;
    private List<TodayNodeDto> todayNodes;

    private List<TripSuggestionDto> suggestions;
}
