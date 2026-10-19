package com.travel.modules.guidebook.dto;

import lombok.Data;

@Data
public class ExportRequest {
    /** markdown / html / pdf（pdf 暂不支持，返回 42200） */
    private String format = "markdown";
}
