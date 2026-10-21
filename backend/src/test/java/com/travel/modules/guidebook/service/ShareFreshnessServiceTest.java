package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.travel.modules.guidebook.dto.FreshnessDiffItem;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.SharedGuidebookSnapshot;

class ShareFreshnessServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);

    private final ShareFreshnessService service = new ShareFreshnessService();

    @Test
    void freshDataProducesNoDiff() {
        SharedGuidebookSnapshot snapshot = snapshot(
                node("n1", NOW.minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                node("n2", NOW.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

        List<FreshnessDiffItem> diff = service.diff(snapshot, NOW);

        assertThat(diff).isEmpty();
    }

    @Test
    void exactlyFortyEightHoursIsStillFresh() {
        SharedGuidebookSnapshot snapshot = snapshot(
                node("n1", NOW.minusHours(48).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

        assertThat(service.diff(snapshot, NOW)).isEmpty();
    }

    @Test
    void olderThanFortyEightHoursIsStale() {
        SharedGuidebookSnapshot snapshot = snapshot(
                node("n1", NOW.minusHours(48).minusSeconds(1)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

        List<FreshnessDiffItem> diff = service.diff(snapshot, NOW);

        assertThat(diff).hasSize(1);
        assertThat(diff.get(0).nodeId()).isEqualTo("n1");
        assertThat(diff.get(0).newValue()).isEqualTo(ShareFreshnessService.PENDING_REFRESH);
    }

    @Test
    void missingAndUnparseableValuesAreStale() {
        SharedGuidebookSnapshot snapshot = snapshot(
                node("n1", null),
                node("n2", "not-a-date"),
                node("n3", "   "));

        List<FreshnessDiffItem> diff = service.diff(snapshot, NOW);

        assertThat(diff).hasSize(3);
        assertThat(diff).extracting(FreshnessDiffItem::nodeId)
                .containsExactly("n1", "n2", "n3");
        assertThat(diff).allSatisfy(item -> {
            assertThat(item.oldValue()).isIn("not-a-date", "   ", null);
            assertThat(item.newValue()).isEqualTo(ShareFreshnessService.PENDING_REFRESH);
        });
    }

    @Test
    void acceptsDateOnlyFormat() {
        SharedGuidebookSnapshot snapshot = snapshot(
                node("n1", "2026-10-21"),
                node("n2", "2026-10-01"));

        List<FreshnessDiffItem> diff = service.diff(snapshot, NOW);

        assertThat(diff).hasSize(1);
        assertThat(diff.get(0).nodeId()).isEqualTo("n2");
    }

    @Test
    void fallsBackToDayIndexWhenNodeIdMissing() {
        SharedGuidebookSnapshot snapshot = snapshot(node(null, null), node("n2", null));

        List<FreshnessDiffItem> diff = service.diff(snapshot, NOW);

        assertThat(diff).extracting(FreshnessDiffItem::nodeId)
                .containsExactly("d1-1", "n2");
    }

    @Test
    void handlesEmptySnapshot() {
        assertThat(service.diff(null, NOW)).isEmpty();
        assertThat(service.diff(new SharedGuidebookSnapshot(), NOW)).isEmpty();
    }

    private SharedGuidebookSnapshot snapshot(GuidebookNodeDto... nodes) {
        SharedGuidebookSnapshot snapshot = new SharedGuidebookSnapshot();
        snapshot.setDays(List.of(new GuidebookDayDto(1, List.of(nodes))));
        return snapshot;
    }

    private GuidebookNodeDto node(String id, String dataFreshness) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId(id);
        node.setDataFreshness(dataFreshness);
        return node;
    }
}
