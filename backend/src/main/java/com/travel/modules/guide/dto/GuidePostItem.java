package com.travel.modules.guide.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GuidePostItem {
    private Long postId;
    private String title;
    private String destination;
    private String coverUrl;
    private String summary;
    private String authorName;
    private List<String> tags;
    private Integer likeCount;
    private Integer viewCount;
    private Integer durationDays;
    private String budgetLevel;
}
