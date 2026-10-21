package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.travel.modules.guidebook.dto.CitationDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.SharedGuidebookSnapshot;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.support.TestFixtures;

class ShareSnapshotBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ShareSnapshotBuilder builder = new ShareSnapshotBuilder(objectMapper);

    @Test
    void copiesDisplayFields() {
        Guidebook guidebook = TestFixtures.guidebook(5L, 7L, "confirmed");
        guidebook.setDegradedMode(true);
        guidebook.setTravelDataAt(LocalDateTime.of(2026, 10, 1, 9, 30));

        SharedGuidebookSnapshot snapshot = builder.build(guidebook);

        assertThat(snapshot.getDays()).hasSize(2);
        assertThat(snapshot.getDays().get(0).getNodes()).hasSize(2);
        assertThat(snapshot.getBudgetBreakdown().getCurrency()).isEqualTo("CNY");
        assertThat(snapshot.getDisclaimer()).isEqualTo(GuidebookService.DISCLAIMER);
        assertThat(snapshot.getDegradedMode()).isTrue();
        assertThat(snapshot.getTravelDataAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 30));
        assertThat(snapshot.getSharedAt()).isNotNull();
    }

    @Test
    void snapshotIsDetachedFromOriginalGuidebook() {
        Guidebook guidebook = TestFixtures.guidebook(5L, 7L, "confirmed");
        SharedGuidebookSnapshot snapshot = builder.build(guidebook);

        guidebook.getDays().get(0).getNodes().get(0).setNote("后来改过的备注");
        guidebook.getDays().get(0).getNodes().clear();
        guidebook.getBudgetBreakdown().setCurrency("USD");

        assertThat(snapshot.getDays().get(0).getNodes()).hasSize(2);
        assertThat(snapshot.getDays().get(0).getNodes().get(0).getNote()).isEqualTo("测试节点");
        assertThat(snapshot.getBudgetBreakdown().getCurrency()).isEqualTo("CNY");
    }

    @Test
    void keepsNodeContentFields() throws Exception {
        Guidebook guidebook = TestFixtures.guidebook(5L, 7L, "confirmed");
        GuidebookNodeDto node = guidebook.getDays().get(0).getNodes().get(0);
        node.setResourceSlotId("r12");
        node.setDataFreshness("2026-10-01");
        node.setOpeningHours("Mo-Su 09:00-17:00");
        node.setCitations(List.of(new CitationDto("K1", "encyclopedia", "宽窄巷子",
                "百科", "https://example.com/k1", "2026-01-01", "简介")));

        SharedGuidebookSnapshot snapshot = builder.build(guidebook);
        GuidebookNodeDto copied = snapshot.getDays().get(0).getNodes().get(0);

        assertThat(copied.getResourceSlotId()).isEqualTo("r12");
        assertThat(copied.getDataFreshness()).isEqualTo("2026-10-01");
        assertThat(copied.getOpeningHours()).isEqualTo("Mo-Su 09:00-17:00");
        assertThat(copied.getCitations()).hasSize(1);
        assertThat(copied.getCitations().get(0).getCode()).isEqualTo("K1");
    }

    @Test
    void excludesInternalFields() throws Exception {
        Guidebook guidebook = TestFixtures.guidebook(5L, 7L, "confirmed");
        guidebook.setSessionId(9L);
        guidebook.setComparisonId(3L);
        guidebook.setVariant("balanced");
        guidebook.setErrorMessage("内部错误");
        guidebook.setSourceShareId(88L);

        String json = objectMapper.writeValueAsString(builder.build(guidebook));

        assertThat(json).doesNotContain("profileSnapshot", "userId", "sessionId",
                "comparisonId", "variant", "errorMessage", "sourceShareId", "progress");
    }

    @Test
    void handlesNullCollections() {
        Guidebook guidebook = new Guidebook();

        SharedGuidebookSnapshot snapshot = builder.build(guidebook);

        assertThat(snapshot.getDays()).isNull();
        assertThat(snapshot.getBudgetBreakdown()).isNull();
        assertThat(snapshot.getPackingList()).isNull();
        assertThat(snapshot.getCulturalTips()).isNull();
        assertThat(snapshot.getPitfallTips()).isNull();
    }
}
