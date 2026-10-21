package com.travel.modules.guidebook.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegenerateRequest {

    @NotNull(message = "sessionId 不能为空")
    private Long sessionId;
}
