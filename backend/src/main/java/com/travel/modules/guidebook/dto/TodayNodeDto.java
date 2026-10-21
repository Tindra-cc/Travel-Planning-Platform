package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodayNodeDto {

    private String nodeId;
    private Integer day;
    private String timeSlotStart;
    private String timeSlotEnd;
    private String type;
    private String placeName;
    private String note;
    private String executionStatus;
    private String markedAt;
}
