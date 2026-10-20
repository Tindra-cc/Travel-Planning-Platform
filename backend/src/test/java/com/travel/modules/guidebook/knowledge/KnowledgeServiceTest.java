package com.travel.modules.guidebook.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.modules.guidebook.entity.KnowledgeDoc;
import com.travel.modules.guidebook.mapper.KnowledgeDocMapper;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    private KnowledgeDocMapper knowledgeDocMapper;

    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeService(new KeywordKnowledgeRetriever(knowledgeDocMapper));
    }

    @Test
    void primaryQueryReturnsNumberedSnippets() {
        when(knowledgeDocMapper.search(eq("成都"), anyList(), eq(5)))
                .thenReturn(List.of(doc(1L, "宽窄巷子"), doc(2L, "火锅")));

        List<KnowledgeSnippet> snippets = service.retrieve("成都", List.of("美食"), 5);

        assertThat(snippets).extracting(KnowledgeSnippet::code).containsExactly("K1", "K2");
        assertThat(snippets.get(0).title()).isEqualTo("宽窄巷子");
    }

    @Test
    void keywordsAreTrimmedDedupedAndCapped() {
        when(knowledgeDocMapper.search(eq("成都"), anyList(), anyInt()))
                .thenReturn(List.of(doc(1L, "宽窄巷子")));
        List<String> keywords = new ArrayList<>(List.of("美食", " 美食 ", "", "人文",
                "k1", "k2", "k3", "k4", "k5", "k6", "k7", "k8"));
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);

        service.retrieve("成都", keywords, 5);

        verify(knowledgeDocMapper).search(eq("成都"), captor.capture(), eq(5));
        assertThat(captor.getValue()).doesNotContain("").doesNotHaveDuplicates().hasSize(8);
        assertThat(captor.getValue()).startsWith("美食", "人文");
    }

    @Test
    void combinationDestinationIsSplitWhenPrimaryMisses() {
        when(knowledgeDocMapper.search(any(), anyList(), anyInt())).thenReturn(List.of());
        when(knowledgeDocMapper.search(eq("丽江"), anyList(), anyInt()))
                .thenReturn(List.of(doc(3L, "丽江古城")));

        List<KnowledgeSnippet> snippets = service.retrieve("丽江+香格里拉", List.of("古镇"), 5);

        assertThat(snippets).extracting(KnowledgeSnippet::title).containsExactly("丽江古城");
        verify(knowledgeDocMapper).search(eq("香格里拉"), anyList(), anyInt());
    }

    @Test
    void keywordSearchIsUsedWhenDestinationBlank() {
        when(knowledgeDocMapper.search(eq(null), anyList(), eq(5)))
                .thenReturn(List.of(doc(1L, "美食指南")));

        List<KnowledgeSnippet> snippets = service.retrieve(" ", List.of("美食"), 5);

        assertThat(snippets).extracting(KnowledgeSnippet::title).containsExactly("美食指南");
    }

    @Test
    void mapperFailureDegradesToEmptyList() {
        when(knowledgeDocMapper.search(any(), anyList(), anyInt()))
                .thenThrow(new RuntimeException("pg down"));

        List<KnowledgeSnippet> snippets = service.retrieve("成都", List.of("美食"), 5);

        assertThat(snippets).isEmpty();
    }

    @Test
    void duplicateIdsAreCollapsed() {
        when(knowledgeDocMapper.search(any(), anyList(), anyInt()))
                .thenReturn(List.of(doc(1L, "宽窄巷子"), doc(1L, "宽窄巷子副本")));

        List<KnowledgeSnippet> snippets = service.retrieve("成都", List.of(), 5);

        assertThat(snippets).hasSize(1);
        assertThat(snippets.get(0).title()).isEqualTo("宽窄巷子");
    }

    @Test
    void snippetsCarryCitationTypeAndPublishedDate() {
        KnowledgeDoc poi = doc(1L, "宽窄巷子");
        poi.setDocType("poi");
        poi.setCreateTime(java.time.LocalDateTime.of(2026, 10, 1, 9, 30));
        when(knowledgeDocMapper.search(any(), anyList(), anyInt())).thenReturn(List.of(poi));

        List<KnowledgeSnippet> snippets = service.retrieve("成都", List.of(), 5);

        assertThat(snippets.get(0).type()).isEqualTo("encyclopedia");
        assertThat(snippets.get(0).publishedAt()).isEqualTo("2026-10-01");
    }

    @Test
    void resultIsCappedAtLimit() {
        when(knowledgeDocMapper.search(any(), anyList(), anyInt()))
                .thenReturn(List.of(doc(1L, "a"), doc(2L, "b"), doc(3L, "c")));

        List<KnowledgeSnippet> snippets = service.retrieve("成都", List.of(), 2);

        assertThat(snippets).extracting(KnowledgeSnippet::code).containsExactly("K1", "K2");
    }

    private KnowledgeDoc doc(long id, String title) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(id);
        doc.setTitle(title);
        doc.setContent(title + "内容");
        doc.setSourceName("内置知识");
        doc.setSourceUrl("https://example.com/" + id);
        return doc;
    }
}
