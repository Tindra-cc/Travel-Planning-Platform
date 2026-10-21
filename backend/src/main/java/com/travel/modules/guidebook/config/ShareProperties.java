package com.travel.modules.guidebook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "app.share")
public class ShareProperties {

    /** 前端公开页地址，例如 http://localhost:5173 */
    private String baseUrl = "http://localhost:5173";
    /** 0 表示不过期 */
    private int expireDays = 30;
    /** 可选：二维码图片地址模板，使用 {url} 占位；为空则不返回 qrcodeUrl */
    private String qrcodeUrlTemplate;
}
