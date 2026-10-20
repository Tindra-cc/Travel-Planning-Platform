package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkValidateResponse {
    private List<Result> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private String resourceSlotId;
        private boolean valid;
        private String reason;
        private String fallbackSupplier;
    }
}
