package com.travel.modules.guidebook.entity;

import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_knowledge_doc", autoResultMap = true)
public class KnowledgeDoc extends BaseEntity {

    private String destination;
    private String docType;
    private String title;
    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private String sourceName;
    private String sourceUrl;
    private Integer sortWeight;
}
