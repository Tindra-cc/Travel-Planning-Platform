package com.travel.modules.expert.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicationReviewRequest {

    @NotNull(message = "approved 不能为空")
    private Boolean approved;

    @Size(max = 500, message = "驳回原因不能超过 500 字")
    private String reason;
}
