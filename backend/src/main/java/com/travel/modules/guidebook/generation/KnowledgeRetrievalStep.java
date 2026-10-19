package com.travel.modules.guidebook.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.travel.modules.guidebook.knowledge.KnowledgeService;
import com.travel.modules.guidebook.knowledge.KnowledgeSnippet;

import lombok.RequiredArgsConstructor;

/** Step 2：目的地知识召回；空结果不阻断，标记 no_knowledge。 */
@Order(2)
@Component
@RequiredArgsConstructor
public class KnowledgeRetrievalStep implements GenerationStep {

    private static final int TOP_K = 5;

    private final KnowledgeService knowledgeService;

    @Override
    public String name() {
        return "knowledge_retrieval";
    }

    @Override
    public int progress() {
        return 15;
    }

    @Override
    public void execute(GenerationContext ctx) {
        List<String> keywords = new ArrayList<>();
        if (ctx.getIntent().getPreferences() != null) {
            keywords.addAll(ctx.getIntent().getPreferences());
        }
        keywords.addAll(strings(ctx.getProfileSnapshot().get("interestTags")));
        keywords.addAll(strings(ctx.getProfileSnapshot().get("specialPreferences")));
        ctx.setKeywords(keywords);

        List<KnowledgeSnippet> snippets = knowledgeService.retrieve(
                ctx.getIntent().getDestination(), keywords, TOP_K);
        ctx.setKnowledge(snippets);
        if (snippets.isEmpty()) {
            ctx.addReason("no_knowledge");
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof Map<?, ?>) {
            return List.of();
        }
        return List.of();
    }
}
