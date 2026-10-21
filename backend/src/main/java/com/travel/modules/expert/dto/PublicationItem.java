package com.travel.modules.expert.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PublicationItem {

    private String publicationId;
    private String guidebookId;
    private String title;
    private String status;
    private Integer priceCents;
    private BigDecimal commissionRate;
    private String rejectReason;
    private LocalDateTime createdAt;
}
