package com.travel.modules.guidebook.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShareCreateRequest {

    /** link / qrcode；market 本期不支持 */
    @NotBlank(message = "channel 不能为空")
    private String channel;
}
