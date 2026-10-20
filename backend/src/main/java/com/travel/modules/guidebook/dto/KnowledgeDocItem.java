package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocItem {
    private Long id;
    private String destination;
    private String docType;
    private String title;
    private String content;
    private List<String> tags;
    private String sourceName;
    private String sourceUrl;
    private String status;
    private String sourceType;
    private Long sourceRefId;
    private String createdAt;
}
