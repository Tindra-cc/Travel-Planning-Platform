package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompareRequest {
    private Long baseGuidebookId;
    /** budget / balanced / luxury；为空时默认三套 */
    private List<String> variants;
}
