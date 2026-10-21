package com.travel.modules.expert.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EarningItem {

    private String earningId;
    private String publicationId;
    private String guideTitle;
    private Integer amountCents;
    private String status;
    private LocalDateTime createdAt;
}
