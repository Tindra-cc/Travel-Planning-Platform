package com.travel.modules.guidebook.knowledge;

/**
 * 检索命中的知识片段，code（K1、K2…）用于让模型在节点 source 中标注出处。
 */
public record KnowledgeSnippet(String code, String title, String content,
        String sourceName, String sourceUrl) {
}
