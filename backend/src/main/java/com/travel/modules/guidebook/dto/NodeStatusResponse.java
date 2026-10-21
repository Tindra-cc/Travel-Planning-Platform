package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeStatusResponse {

    private Long guidebookId;
    private String nodeId;
    private String status;
    private String markedAt;
    private Double progress;
}
