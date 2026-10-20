package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MixRequest {
    private List<Pick> picks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pick {
        private Long guidebookId;
        private String nodeId;
    }
}
