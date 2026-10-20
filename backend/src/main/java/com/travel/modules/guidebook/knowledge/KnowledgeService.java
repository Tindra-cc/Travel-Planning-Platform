package com.travel.modules.guidebook.knowledge;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 生成流水线使用的知识检索入口，只做异常隔离；具体召回策略由 KnowledgeRetriever 决定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeRetriever retriever;

    public List<KnowledgeSnippet> retrieve(String destination, List<String> keywords, int limit) {
        try {
            return retriever.retrieve(destination, keywords, limit);
        } catch (Exception e) {
            log.warn("知识检索失败，降级为空资料: {}", e.getMessage());
            return List.of();
        }
    }
}
