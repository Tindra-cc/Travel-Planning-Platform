package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MixResponse {
    /** blocked 时为 null，不落库 */
    private Long guidebookId;
    /** draft / blocked */
    private String status;
    private List<NodeConflictDto> conflicts;
}
