package com.travel.modules.guidebook.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProfileIterationRequest {

    /** true = 接受建议并写入画像；false = 拒绝，仅记录决策 */
    @NotNull(message = "accept 不能为空")
    private Boolean accept;
}
