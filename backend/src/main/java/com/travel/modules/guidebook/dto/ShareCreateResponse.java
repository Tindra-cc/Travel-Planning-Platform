package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareCreateResponse {
    private String shareId;
    private String shareUrl;
    private String qrcodeUrl;
}
