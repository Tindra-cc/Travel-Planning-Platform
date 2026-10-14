package com.travel.modules.guide.entity;

import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_guide_post", autoResultMap = true)
public class GuidePost extends BaseEntity {

    public static final String STATUS_PUBLISHED = "published";

    private String title;
    private String destination;
    private String coverUrl;
    private String summary;
    private String authorName;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private Integer likeCount;
    private Integer viewCount;
    private Integer durationDays;
    private String budgetLevel;
    private String status;
    private Integer sortWeight;
}
