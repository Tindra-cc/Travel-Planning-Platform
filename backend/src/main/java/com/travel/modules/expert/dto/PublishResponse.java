package com.travel.modules.expert.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PublishResponse {

    private String publishedId;
    private String status;
    private BigDecimal commissionRate;
}
