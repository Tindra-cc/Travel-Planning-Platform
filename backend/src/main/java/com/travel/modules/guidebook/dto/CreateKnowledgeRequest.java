package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.Data;

@Data
public class CreateKnowledgeRequest {
    private String destination;
    private String docType;
    private String title;
    private String content;
    private List<String> tags;
    private String sourceName;
    private String sourceUrl;
}
