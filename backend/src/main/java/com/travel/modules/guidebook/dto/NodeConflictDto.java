package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeConflictDto {

    //time_overlap/unreachable/budget_exceeded/closed_time
    private String type;
    private Integer day;
    private List<String> nodeIds;
    private String message;
}
