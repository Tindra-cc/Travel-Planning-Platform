package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 节点溯源引用；code 为 prompt 中的 [K1] 编号，通用知识引用 code 为空。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CitationDto {
    private String code;
    /** encyclopedia / travelogue / route / expert / guide / general */
    private String type;
    private String title;
    private String sourceName;
    private String sourceUrl;
    private String publishedAt;
    private String summary;
}
