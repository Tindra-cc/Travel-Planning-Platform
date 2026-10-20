package com.travel.modules.guidebook.knowledge;

import java.util.List;

/**
 * 知识召回的可替换入口：本期内置 pg_trgm 关键词实现，将来接向量检索时新增实现即可，
 * 生成流水线只依赖这个接口。
 */
public interface KnowledgeRetriever {

    List<KnowledgeSnippet> retrieve(String destination, List<String> keywords, int limit);
}
