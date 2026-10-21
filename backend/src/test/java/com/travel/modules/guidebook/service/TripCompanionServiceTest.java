package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.NodeStatusResponse;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.dto.TripCompanionResponse;
import com.travel.modules.guidebook.edit.GuidebookNodeStatus;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.GuidebookNodeStatusMapper;

@ExtendWith(MockitoExtension.class)
class TripCompanionServiceTest {

    private static final long USER_ID = 7L;
    private static final long GUIDEBOOK_ID = 5L;

    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private GuidebookNodeStatusMapper nodeStatusMapper;

    private TripCompanionService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "tester"));
        service = new TripCompanionService(guidebookMapper, nodeStatusMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void returnsTodayNodesWithExecutionStatus() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        guidebook.setDays(List.of(
                day(1, node("n1", "浅草寺")),
                day(2, node("n2", "上野公园"), node("n3", "秋叶原"))));
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(nodeStatusMapper.selectList(any())).thenReturn(List.of(
                mark("n1", GuidebookNodeStatus.SKIPPED),
                mark("n3", GuidebookNodeStatus.EXECUTED)));
        when(nodeStatusMapper.selectCount(any())).thenReturn(2L);

        TripCompanionResponse response = service.get(GUIDEBOOK_ID);

        assertThat(response.getCurrentDay()).isEqualTo(2);
        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getExecutedCount()).isEqualTo(1);
        assertThat(response.getSkippedCount()).isEqualTo(1);
        assertThat(response.getProgress()).isEqualTo(66.67);
        assertThat(response.getTodayNodes()).extracting("nodeId").containsExactly("n2", "n3");
        assertThat(response.getTodayNodes().get(1).getExecutionStatus())
                .isEqualTo(GuidebookNodeStatus.EXECUTED);
    }

    @Test
    void completesGuidebookAfterEndDate() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EXECUTING, LocalDate.now().minusDays(3), LocalDate.now().minusDays(1));
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(guidebookMapper.update(any(), any())).thenReturn(1);
        when(nodeStatusMapper.selectList(any())).thenReturn(List.of());

        TripCompanionResponse response = service.get(GUIDEBOOK_ID);

        assertThat(response.getStatus()).isEqualTo(Guidebook.STATUS_COMPLETED);
        assertThat(response.getCurrentDay()).isNull();
        verify(guidebookMapper).update(any(), any());
    }

    @Test
    void rejectsUnknownNodeStatus() {
        assertThatThrownBy(() -> service.markNode(GUIDEBOOK_ID, "n1", "visited"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
        verify(guidebookMapper, never()).selectById(any());
    }

    @Test
    void rejectsUnknownNode() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EXECUTING, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        guidebook.setDays(List.of(day(1, node("n1", "浅草寺"))));
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertThatThrownBy(() -> service.markNode(GUIDEBOOK_ID, "missing", GuidebookNodeStatus.EXECUTED))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(ResultCode.NOT_FOUND));
    }

    @Test
    void rejectsNonOwner() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EXECUTING, LocalDate.now(), LocalDate.now());
        guidebook.setUserId(USER_ID + 1);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertThatThrownBy(() -> service.get(GUIDEBOOK_ID))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(ResultCode.NOT_OWNER));
    }

    @Test
    void insertsNodeSnapshotOnFirstMark() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EXECUTING, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        guidebook.setDays(List.of(day(1, node("n1", "浅草寺"))));
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(nodeStatusMapper.selectOne(any())).thenReturn(null);
        when(nodeStatusMapper.insert(any())).thenReturn(1);
        when(nodeStatusMapper.selectCount(any())).thenReturn(1L);
        when(guidebookMapper.update(any(), any())).thenReturn(1);

        NodeStatusResponse response = service.markNode(GUIDEBOOK_ID, "n1", GuidebookNodeStatus.EXECUTED);

        ArgumentCaptor<GuidebookNodeStatus> captor = ArgumentCaptor.forClass(GuidebookNodeStatus.class);
        verify(nodeStatusMapper).insert(captor.capture());
        GuidebookNodeStatus saved = captor.getValue();
        assertThat(saved.getGuidebookId()).isEqualTo(GUIDEBOOK_ID);
        assertThat(saved.getNodeId()).isEqualTo("n1");
        assertThat(saved.getNodeDay()).isEqualTo(1);
        assertThat(saved.getNodeName()).isEqualTo("浅草寺");
        assertThat(saved.getStatus()).isEqualTo(GuidebookNodeStatus.EXECUTED);
        assertThat(response.getProgress()).isEqualTo(100.0);
        assertThat(guidebook.getStatus()).isEqualTo(Guidebook.STATUS_COMPLETED);
    }

    @Test
    void rejectsMarkOnCompletedGuidebook() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_COMPLETED, LocalDate.now().minusDays(2), LocalDate.now().minusDays(1));
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertThatThrownBy(() -> service.markNode(GUIDEBOOK_ID, "n1", GuidebookNodeStatus.EXECUTED))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(ResultCode.CONFLICT));
        verify(nodeStatusMapper, never()).insert(any());
    }

    private Guidebook guidebook(String status, LocalDate start, LocalDate end) {
        Guidebook guidebook = new Guidebook();
        guidebook.setId(GUIDEBOOK_ID);
        guidebook.setUserId(USER_ID);
        guidebook.setStatus(status);
        IntentDto intent = new IntentDto();
        intent.setDestination("东京");
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart(start.toString());
        window.setEnd(end.toString());
        intent.setDateWindow(window);
        guidebook.setIntent(intent);
        guidebook.setDays(new ArrayList<>());
        return guidebook;
    }

    private GuidebookDayDto day(int number, GuidebookNodeDto... nodes) {
        return new GuidebookDayDto(number, List.of(nodes));
    }

    private GuidebookNodeDto node(String id, String name) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId(id);
        node.setType("activity");
        node.setTimeSlot(new TimeSlotDto("09:00", "10:30"));
        node.setPlace(new PlaceDto(name, 35.0, 139.0));
        node.setNote("备注");
        return node;
    }

    private GuidebookNodeStatus mark(String nodeId, String status) {
        GuidebookNodeStatus mark = new GuidebookNodeStatus();
        mark.setGuidebookId(GUIDEBOOK_ID);
        mark.setNodeId(nodeId);
        mark.setStatus(status);
        return mark;
    }
}
