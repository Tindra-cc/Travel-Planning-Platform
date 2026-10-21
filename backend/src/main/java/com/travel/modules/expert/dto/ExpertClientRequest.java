package com.travel.modules.expert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExpertClientRequest {

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 80, message = "客户名称不能超过 80 字")
    private String name;

    @Size(max = 120, message = "联系方式不能超过 120 字")
    private String contact;

    @Size(max = 500, message = "备注不能超过 500 字")
    private String note;

    /** 可空；填写时必须是真实存在的用户 ID */
    private Long linkedUserId;
}
