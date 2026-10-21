package com.travel.modules.expert.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class ExpertClientResponse {

    private String clientId;
    private String name;
    private String contact;
    private String note;
    private String linkedUserId;
    private String status;
    private LocalDateTime createdAt;

    /** active / pending / none：客户当前授权状态的派生视图，不单独落表。 */
    private String authorizationStatus;
    private List<String> authorizedScopes;
    private LocalDateTime authorizedAt;
}
