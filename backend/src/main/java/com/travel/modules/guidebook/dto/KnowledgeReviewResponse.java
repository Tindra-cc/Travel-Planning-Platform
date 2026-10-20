package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeReviewResponse {
    private Long postId;
    /** approved / rejected */
    private String reviewStatus;
    /** 审核通过后写入的知识条目 ID；驳回或无条目时为 null */
    private Long knowledgeDocId;
}
