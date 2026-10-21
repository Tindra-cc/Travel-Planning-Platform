package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionDecisionResponse {
    private Long suggestionId;
    private String status;
    /** false 表示提示类建议，未修改行程 */
    private Boolean applied;
    private Integer version;
    private String summary;
}
