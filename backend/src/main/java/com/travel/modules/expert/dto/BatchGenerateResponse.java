package com.travel.modules.expert.dto;

import java.util.List;

import lombok.Data;

@Data
public class BatchGenerateResponse {

    private List<BatchTaskResponse> tasks;
}
