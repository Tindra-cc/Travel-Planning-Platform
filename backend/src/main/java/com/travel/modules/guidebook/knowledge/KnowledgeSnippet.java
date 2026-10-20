package com.travel.modules.guidebook.knowledge;

/**
 * 检索命中的知识片段，code（K1、K2…）用于让模型在节点 source 中标注出处。
 * type 取值为 encyclopedia/travelogue/route/expert/guide/general。
 */
public record KnowledgeSnippet(String code, String type, String title, String content,
        String sourceName, String sourceUrl, String publishedAt) {
}
