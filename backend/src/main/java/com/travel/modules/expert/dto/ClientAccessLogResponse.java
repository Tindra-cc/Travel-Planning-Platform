package com.travel.modules.expert.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ClientAccessLogResponse {

    private String logId;
    private String action;
    private String scope;
    private String resourceType;
    private String resourceId;
    private String ip;
    private LocalDateTime createdAt;
}
