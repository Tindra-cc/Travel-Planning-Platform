package com.travel.modules.guidebook.generation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.travel.modules.guidebook.dto.CitationDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.knowledge.KnowledgeSnippet;

/**
 * Step 8：把节点 source 里的 [K1] 映射为结构化引用，并补充来源兜底与采集时间。
 * 无引用（或引用了不存在的编号）时补一条 general 引用，明确标注「通用知识」。
 */
@Order(8)
@Component
public class TraceabilityStep implements GenerationStep {

    private static final Pattern CITATION = Pattern.compile("\\[K(\\d+)]");
    private static final int SUMMARY_LIMIT = 80;

    @Override
    public String name() {
        return "traceability";
    }

    @Override
    public int progress() {
        return 90;
    }

    @Override
    public void execute(GenerationContext ctx) {
        String freshness = ctx.getTravelData() == null ? null : ctx.getTravelData().fetchedAt();
        if (ctx.getDays() == null) {
            return;
        }
        Map<String, KnowledgeSnippet> knowledge = new HashMap<>();
        for (KnowledgeSnippet snippet : ctx.getKnowledge()) {
            knowledge.put(snippet.code(), snippet);
        }
        for (GuidebookDayDto day : ctx.getDays()) {
            if (day == null || day.getNodes() == null) {
                continue;
            }
            for (GuidebookNodeDto node : day.getNodes()) {
                if (node == null) {
                    continue;
                }
                if (node.getSource() == null || node.getSource().isBlank()) {
                    node.setSource("通用知识");
                }
                node.setCitations(resolveCitations(node.getSource(), knowledge));
                if (freshness != null && node.getDataFreshness() == null) {
                    node.setDataFreshness(freshness);
                }
            }
        }
    }

    private List<CitationDto> resolveCitations(String source, Map<String, KnowledgeSnippet> knowledge) {
        List<CitationDto> citations = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Matcher matcher = CITATION.matcher(source == null ? "" : source);
        while (matcher.find()) {
            String code = "K" + matcher.group(1);
            KnowledgeSnippet snippet = knowledge.get(code);
            if (snippet != null && seen.add(code)) {
                citations.add(new CitationDto(snippet.code(), snippet.type(), snippet.title(),
                        snippet.sourceName(), snippet.sourceUrl(), snippet.publishedAt(),
                        summarize(snippet.content())));
            }
        }
        if (citations.isEmpty()) {
            citations.add(new CitationDto(null, "general", "通用知识", null, null, null, null));
        }
        return citations;
    }

    private String summarize(String content) {
        if (content == null) {
            return null;
        }
        String text = content.strip();
        return text.length() <= SUMMARY_LIMIT ? text : text.substring(0, SUMMARY_LIMIT) + "…";
    }
}
