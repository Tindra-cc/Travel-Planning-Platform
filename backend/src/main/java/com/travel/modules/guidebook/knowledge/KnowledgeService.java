package com.travel.modules.guidebook.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.travel.modules.guidebook.entity.KnowledgeDoc;
import com.travel.modules.guidebook.mapper.KnowledgeDocMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 目的地知识检索。PG + pg_trgm 子串召回，按目的地/关键词排序；
 * 检索失败不阻断生成，返回空列表由流水线标记 no_knowledge。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private static final int MAX_KEYWORDS = 8;

    private final KnowledgeDocMapper knowledgeDocMapper;

    public List<KnowledgeSnippet> retrieve(String destination, List<String> keywords, int limit) {
        List<String> safeKeywords = (keywords == null ? List.<String>of() : keywords).stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .distinct()
                .limit(MAX_KEYWORDS)
                .toList();

        Map<Long, KnowledgeDoc> byId = new LinkedHashMap<>();
        try {
            if (StringUtils.hasText(destination)) {
                collect(byId, knowledgeDocMapper.search(destination.strip(), safeKeywords, limit));
                // 「丽江+香格里拉」这类组合目的地拆开再召回一次。
                if (byId.isEmpty() && destination.contains("+")) {
                    for (String part : destination.split("\\+")) {
                        if (StringUtils.hasText(part)) {
                            collect(byId, knowledgeDocMapper.search(part.strip(), safeKeywords, limit));
                        }
                    }
                }
            }
            if (byId.isEmpty() && !safeKeywords.isEmpty()) {
                collect(byId, knowledgeDocMapper.search(null, safeKeywords, limit));
            }
        } catch (Exception e) {
            log.warn("知识检索失败，降级为空资料: {}", e.getMessage());
        }

        List<KnowledgeSnippet> snippets = new ArrayList<>();
        int index = 1;
        for (KnowledgeDoc doc : byId.values()) {
            if (snippets.size() >= limit) {
                break;
            }
            snippets.add(new KnowledgeSnippet("K" + index++, doc.getTitle(), doc.getContent(),
                    doc.getSourceName(), doc.getSourceUrl()));
        }
        return snippets;
    }

    private void collect(Map<Long, KnowledgeDoc> byId, List<KnowledgeDoc> docs) {
        for (KnowledgeDoc doc : docs) {
            byId.putIfAbsent(doc.getId(), doc);
        }
    }
}
