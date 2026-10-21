package com.travel.modules.expert.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PublicationReviewResponse {

    private String publicationId;
    private String status;
    private String postId;
}
