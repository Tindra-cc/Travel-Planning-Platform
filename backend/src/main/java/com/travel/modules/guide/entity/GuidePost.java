package com.travel.modules.guide.entity;

import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;
import com.travel.modules.guide.dto.GuidePlanSection;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_guide_post", autoResultMap = true)
public class GuidePost extends BaseEntity {

    public static final String STATUS_PUBLISHED = "published";
    public static final String REVIEW_APPROVED = "approved";
    public static final String REVIEW_REJECTED = "rejected";

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
    private String reviewStatus;

    // ---- 详情字段（V5 新增；列表接口不返回，避免把重内容塞进信息流） ----

    private String sourceUrl;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /**
     * 注意：JacksonTypeHandler 按字段的 raw type 反序列化，List&lt;GuidePlanSection&gt; 的 raw type 是
     * List，可能退化成 List&lt;LinkedHashMap&gt;。输出 JSON 不受影响，但 Java 侧取不到类型安全——
     * GuideService 里用 ObjectMapper.convertValue 兜一层。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<GuidePlanSection> planSections;
}
