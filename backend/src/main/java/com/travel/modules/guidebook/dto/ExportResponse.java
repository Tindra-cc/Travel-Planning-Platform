package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExportResponse {
    private String format;
    private String filename;
    private String content;
}
