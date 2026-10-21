package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.ReviewStatsDto;
import com.travel.modules.guidebook.edit.GuidebookNodeStatus;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookNodeStatusMapper;

@ExtendWith(MockitoExtension.class)
class ReviewStatsServiceTest {

    @Mock
    private GuidebookNodeStatusMapper statusMapper;

    private ReviewStatsService service;

    @BeforeEach
    void setUp() {
        service = new ReviewStatsService(statusMapper);
    }

    @Test
    void countsTerminalStatusesAndUntouchedNodes() {
        Guidebook guidebook = guidebook(
                day(1, "n1", "宽窄巷子"), day(1, "n2", "奎星楼街"),
                day(2, "n3", "杜甫草堂"), day(2, "n4", "锦里"), day(2, "n5", "武侯祠"));
        when(statusMapper.selectList(any())).thenReturn(List.of(
                status("n1", GuidebookNodeStatus.EXECUTED),
                status("n2", GuidebookNodeStatus.SKIPPED),
                status("n3", GuidebookNodeStatus.EXECUTED),
                status("n4", GuidebookNodeStatus.RESCHEDULED)));

        ReviewStatsDto stats = service.calculate(guidebook);

        assertThat(stats.getTotalCount()).isEqualTo(5);
        assertThat(stats.getExecutedCount()).isEqualTo(2);
        assertThat(stats.getSkippedCount()).isEqualTo(1);
        assertThat(stats.getRescheduledCount()).isEqualTo(1);
        assertThat(stats.getUntouchedCount()).isEqualTo(1);
        assertThat(stats.getSkipRate()).isEqualTo(0.2);
        assertThat(stats.getSkippedNodeNames()).containsExactly("奎星楼街");
        assertThat(stats.getSkippedByDay()).containsEntry(1, 1);
    }

    @Test
    void ignoresStatusRowsForDeletedNodes() {
        Guidebook guidebook = guidebook(day(1, "n1", "宽窄巷子"), day(1, "n2", "锦里"));
        when(statusMapper.selectList(any())).thenReturn(List.of(
                status("n1", GuidebookNodeStatus.EXECUTED),
                status("removed", GuidebookNodeStatus.SKIPPED)));

        ReviewStatsDto stats = service.calculate(guidebook);

        assertThat(stats.getTotalCount()).isEqualTo(2);
        assertThat(stats.getSkippedCount()).isZero();
        assertThat(stats.getSkippedNodeNames()).isEmpty();
        assertThat(stats.getUntouchedCount()).isEqualTo(1);
        assertThat(stats.getSkipRate()).isZero();
    }

    @Test
    void countsEachNodeOnlyOnce() {
        Guidebook guidebook = guidebook(day(1, "n1", "宽窄巷子"));
        when(statusMapper.selectList(any())).thenReturn(List.of(
                status("n1", GuidebookNodeStatus.EXECUTED),
                status("n1", GuidebookNodeStatus.SKIPPED)));

        ReviewStatsDto stats = service.calculate(guidebook);

        assertThat(stats.getExecutedCount()).isEqualTo(1);
        assertThat(stats.getSkippedCount()).isZero();
        assertThat(stats.getUntouchedCount()).isZero();
    }

    @Test
    void roundsSkipRateToFourDecimals() {
        Guidebook guidebook = guidebook(day(1, "n1", "A"), day(1, "n2", "B"), day(1, "n3", "C"));
        when(statusMapper.selectList(any()))
                .thenReturn(List.of(status("n1", GuidebookNodeStatus.SKIPPED)));

        ReviewStatsDto stats = service.calculate(guidebook);

        assertThat(stats.getSkipRate()).isEqualTo(0.3333);
    }

    @Test
    void treatsUnknownStatusAsUntouched() {
        Guidebook guidebook = guidebook(day(1, "n1", "A"));
        when(statusMapper.selectList(any()))
                .thenReturn(List.of(status("n1", "cancelled")));

        ReviewStatsDto stats = service.calculate(guidebook);

        assertThat(stats.getUntouchedCount()).isEqualTo(1);
        assertThat(stats.getExecutedCount()).isZero();
    }

    @Test
    void returnsZerosWhenDaysAreNull() {
        Guidebook guidebook = guidebook();
        guidebook.setDays(null);
        when(statusMapper.selectList(any())).thenReturn(List.of());

        ReviewStatsDto stats = service.calculate(guidebook);

        assertThat(stats.getTotalCount()).isZero();
        assertThat(stats.getUntouchedCount()).isZero();
        assertThat(stats.getSkipRate()).isZero();
        assertThat(stats.getSkippedByDay()).isEmpty();
    }

    private Guidebook guidebook(GuidebookDayDto... days) {
        Guidebook guidebook = new Guidebook();
        guidebook.setId(5L);
        guidebook.setDays(new ArrayList<>(List.of(days)));
        return guidebook;
    }

    private GuidebookDayDto day(int day, String nodeId, String name) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId(nodeId);
        node.setPlace(new PlaceDto(name, null, null));
        return new GuidebookDayDto(day, new ArrayList<>(List.of(node)));
    }

    private GuidebookNodeStatus status(String nodeId, String status) {
        GuidebookNodeStatus row = new GuidebookNodeStatus();
        row.setGuidebookId(5L);
        row.setNodeId(nodeId);
        row.setStatus(status);
        return row;
    }
}
