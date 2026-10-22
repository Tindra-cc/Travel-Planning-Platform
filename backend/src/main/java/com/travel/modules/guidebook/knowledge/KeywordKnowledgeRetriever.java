package com.travel.modules.guidebook.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.travel.modules.guidebook.entity.KnowledgeDoc;
import com.travel.modules.guidebook.mapper.KnowledgeDocMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PG + pg_trgm 子串召回，按目的地/关键词排序；只召回已审核条目。
 * 检索失败不阻断生成，返回空列表由模型按通用知识继续生成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordKnowledgeRetriever implements KnowledgeRetriever {

    private static final int MAX_KEYWORDS = 8;

    private final KnowledgeDocMapper knowledgeDocMapper;

    @Override
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
            snippets.add(toSnippet("K" + index++, doc));
        }
        return snippets;
    }

    private KnowledgeSnippet toSnippet(String code, KnowledgeDoc doc) {
        return new KnowledgeSnippet(code, citationType(doc.getDocType()), doc.getTitle(),
                doc.getContent(), doc.getSourceName(), doc.getSourceUrl(),
                doc.getCreateTime() == null ? null : doc.getCreateTime().toLocalDate().toString());
    }

    static String citationType(String docType) {
        if ("poi".equals(docType)) {
            return "encyclopedia";
        }
        if ("tip".equals(docType)) {
            return "general";
        }
        return "guide";
    }

    private void collect(Map<Long, KnowledgeDoc> byId, List<KnowledgeDoc> docs) {
        for (KnowledgeDoc doc : docs) {
            byId.putIfAbsent(doc.getId(), doc);
        }
    }
}
