package com.travel.modules.guide.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** §5.8 攻略详情：在卡片字段（{@link GuidePostItem}）基础上补图集、原文外链与模块化规划。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuidePostDetail {
    private Long postId;
    private String title;
    private String destination;
    private String coverUrl;
    /** 图集外链，首元素与 coverUrl 同源 */
    private List<String> images;
    private String summary;
    private String authorName;
    private List<String> tags;
    private Integer likeCount;
    private Integer viewCount;
    private Integer durationDays;
    private String budgetLevel;
    /** 原文外链；为空则前端不显示「查看原文」 */
    private String sourceUrl;
    private List<GuidePlanSection> planSections;
}
