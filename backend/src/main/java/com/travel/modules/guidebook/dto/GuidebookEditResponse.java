package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuidebookEditResponse {

    private Long guidebookId;
    private Integer version;
    private String changeSummary;
    private List<NodeConflictDto> conflicts;
}
