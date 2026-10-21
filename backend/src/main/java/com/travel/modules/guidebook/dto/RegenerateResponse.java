package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.Data;

@Data
public class RegenerateResponse {
    private Long guidebookId;
    private String status;
    private List<FreshnessDiffItem> dataFreshnessDiff;
}
