package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.guidebook.dto.AddNodeRequest;
import com.travel.modules.guidebook.dto.GuidebookEditResponse;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.MoveNodeRequest;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.dto.UpdateNodeRequest;
import com.travel.modules.guidebook.edit.ConflictDetector;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.entity.ResourceSlot;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class GuidebookEditServiceTest {

    private static final long USER_ID = 1L;
    private static final long GUIDEBOOK_ID = 5L;

    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private GuidebookVersionService versionService;
    @Mock
    private ResourceService resourceService;

    private GuidebookEditService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "user1@test.local"));
        service = new GuidebookEditService(guidebookMapper, versionService, resourceService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ---- updateNode ----

    @Test
    void updateNodeAppliesOnlyProvidedFieldsAndAppendsSnapshot() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        stubUpdateSuccess(guidebook);

        GuidebookEditResponse response = service.updateNode(GUIDEBOOK_ID, 1, "d1-1",
                new UpdateNodeRequest(null, null, "新备注"));

        GuidebookNodeDto node = guidebook.getDays().get(0).getNodes().get(0);
        assertThat(node.getNote()).isEqualTo("新备注");
        assertThat(node.getPlace().getName()).isEqualTo("宽窄巷子");
        assertThat(node.getTimeSlot().getStart()).isEqualTo("09:00");
        assertThat(guidebook.getStatus()).isEqualTo(Guidebook.STATUS_EDITING);
        assertThat(response.getGuidebookId()).isEqualTo(GUIDEBOOK_ID);
        assertThat(response.getChangeSummary()).contains("修改Day1");
        assertThat(response.getConflicts()).isEmpty();
        verify(versionService).append(eq(guidebook), eq(GuidebookVersion.CHANGE_TYPE_MANUAL),
                anyString(), eq(USER_ID));
    }

    @Test
    void updateNodeReturnsConflictsWithoutBlockingTheSave() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EDITING);
        stubUpdateSuccess(guidebook);

        GuidebookEditResponse response = service.updateNode(GUIDEBOOK_ID, 1, "d1-2",
                new UpdateNodeRequest(new TimeSlotDto("10:30", "12:00"), null, null));

        assertThat(response.getConflicts()).extracting(NodeConflictDto::getType)
                .containsExactly(ConflictDetector.TYPE_TIME_OVERLAP);
        verify(guidebookMapper).updateById(guidebook);
    }

    @Test
    void updateNodeRejectsNonEditableStatus() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(guidebook(Guidebook.STATUS_CONFIRMED));

        assertBizCode(ResultCode.CONFLICT,
                () -> service.updateNode(GUIDEBOOK_ID, 1, "d1-1", new UpdateNodeRequest()));
        verify(guidebookMapper, never()).updateById(any(Guidebook.class));
    }

    @Test
    void updateNodeRejectsForeignGuidebook() {
        Guidebook foreign = TestFixtures.guidebook(GUIDEBOOK_ID, 2L, Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(foreign);

        assertBizCode(ResultCode.NOT_OWNER,
                () -> service.updateNode(GUIDEBOOK_ID, 1, "d1-1", new UpdateNodeRequest()));
    }

    @Test
    void updateNodeRejectsMissingGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(null);

        assertBizCode(ResultCode.NOT_FOUND,
                () -> service.updateNode(GUIDEBOOK_ID, 1, "d1-1", new UpdateNodeRequest()));
    }

    @Test
    void updateNodeRejectsUnknownDayOrNode() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertBizCode(ResultCode.NOT_FOUND,
                () -> service.updateNode(GUIDEBOOK_ID, 99, "d1-1", new UpdateNodeRequest()));
        assertBizCode(ResultCode.NOT_FOUND,
                () -> service.updateNode(GUIDEBOOK_ID, 1, "missing", new UpdateNodeRequest()));
    }

    // ---- bindResource ----

    @Test
    void bindResourceSetsSlotAndAppendsSnapshot() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        stubUpdateSuccess(guidebook);
        ResourceSlot slot = new ResourceSlot();
        slot.setId("r70001");
        when(resourceService.requireBindable("r70001")).thenReturn(slot);

        GuidebookEditResponse response = service.bindResource(GUIDEBOOK_ID, "d1-1", "r70001");

        assertThat(guidebook.getDays().get(0).getNodes().get(0).getResourceSlotId())
                .isEqualTo("r70001");
        assertThat(response.getChangeSummary()).contains("绑定资源", "宽窄巷子");
        verify(versionService).append(eq(guidebook), eq(GuidebookVersion.CHANGE_TYPE_MANUAL),
                anyString(), eq(USER_ID));
    }

    @Test
    void bindResourceWithNullSlotUnbinds() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EDITING);
        guidebook.getDays().get(0).getNodes().get(0).setResourceSlotId("r70001");
        stubUpdateSuccess(guidebook);

        service.bindResource(GUIDEBOOK_ID, "d1-1", null);

        assertThat(guidebook.getDays().get(0).getNodes().get(0).getResourceSlotId()).isNull();
    }

    // ---- addNode ----

    @Test
    void addNodeAppendsToEndWhenIndexIsNull() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        stubUpdateSuccess(guidebook);

        GuidebookEditResponse response = service.addNode(GUIDEBOOK_ID, 1, newNode(null));

        java.util.List<GuidebookNodeDto> nodes = guidebook.getDays().get(0).getNodes();
        assertThat(nodes).hasSize(3);
        GuidebookNodeDto added = nodes.get(2);
        assertThat(added.getPlace().getName()).isEqualTo("火锅店");
        assertThat(added.getNodeId()).startsWith("n").hasSize(9);
        assertThat(response.getChangeSummary()).contains("新增", "火锅店");
        verify(versionService).append(eq(guidebook), eq(GuidebookVersion.CHANGE_TYPE_MANUAL),
                anyString(), eq(USER_ID));
    }

    @Test
    void addNodeInsertsAtRequestedIndex() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        stubUpdateSuccess(guidebook);

        service.addNode(GUIDEBOOK_ID, 1, newNode(1));

        assertThat(guidebook.getDays().get(0).getNodes())
                .extracting(GuidebookNodeDto::getPlace)
                .extracting(PlaceDto::getName)
                .containsExactly("宽窄巷子", "火锅店", "奎星楼街");
    }

    @Test
    void addNodeRejectsInvalidType() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        AddNodeRequest request = newNode(null);
        request.setType("shopping");

        assertBizCode(ResultCode.PARAM_ERROR,
                () -> service.addNode(GUIDEBOOK_ID, 1, request));
        verify(guidebookMapper, never()).updateById(any(Guidebook.class));
    }

    @Test
    void addNodeRejectsMissingTimeOrPlace() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        AddNodeRequest noTime = newNode(null);
        noTime.setTimeSlot(null);
        assertBizCode(ResultCode.PARAM_ERROR,
                () -> service.addNode(GUIDEBOOK_ID, 1, noTime));

        AddNodeRequest blankPlace = newNode(null);
        blankPlace.setPlace(new PlaceDto(" ", 30.0, 104.0));
        assertBizCode(ResultCode.PARAM_ERROR,
                () -> service.addNode(GUIDEBOOK_ID, 1, blankPlace));
    }

    @Test
    void addNodeRejectsOutOfRangeIndex() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertBizCode(ResultCode.PARAM_ERROR,
                () -> service.addNode(GUIDEBOOK_ID, 1, newNode(3)));
    }

    // ---- deleteNode ----

    @Test
    void deleteNodeRemovesNodeAndSaves() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        stubUpdateSuccess(guidebook);

        GuidebookEditResponse response = service.deleteNode(GUIDEBOOK_ID, 1, "d1-1");

        assertThat(guidebook.getDays().get(0).getNodes())
                .extracting(GuidebookNodeDto::getNodeId)
                .containsExactly("d1-2");
        assertThat(response.getChangeSummary()).contains("删除Day1", "宽窄巷子");
        verify(guidebookMapper).updateById(guidebook);
    }

    @Test
    void deleteNodeRejectsUnknownNode() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertBizCode(ResultCode.NOT_FOUND,
                () -> service.deleteNode(GUIDEBOOK_ID, 1, "missing"));
    }

    // ---- moveNode ----

    @Test
    void moveNodeMovesAcrossDays() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        stubUpdateSuccess(guidebook);

        GuidebookEditResponse response = service.moveNode(GUIDEBOOK_ID, "d1-2",
                new MoveNodeRequest(2, 0));

        assertThat(guidebook.getDays().get(0).getNodes())
                .extracting(GuidebookNodeDto::getNodeId)
                .containsExactly("d1-1");
        assertThat(guidebook.getDays().get(1).getNodes())
                .extracting(GuidebookNodeDto::getNodeId)
                .containsExactly("d1-2");
        assertThat(response.getChangeSummary()).contains("移动到Day2");
    }

    @Test
    void moveNodeReordersWithinSameDay() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        stubUpdateSuccess(guidebook);

        service.moveNode(GUIDEBOOK_ID, "d1-1", new MoveNodeRequest(1, 1));

        assertThat(guidebook.getDays().get(0).getNodes())
                .extracting(GuidebookNodeDto::getNodeId)
                .containsExactly("d1-2", "d1-1");
    }

    @Test
    void moveNodeReordersTimelineByNewNodeOrder() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        stubUpdateSuccess(guidebook);

        service.moveNode(GUIDEBOOK_ID, "d1-1", new MoveNodeRequest(1, 1));

        assertThat(guidebook.getDays().get(0).getNodes())
                .extracting(GuidebookNodeDto::getNodeId)
                .containsExactly("d1-2", "d1-1");
        assertThat(guidebook.getDays().get(0).getNodes())
                .extracting(n -> n.getTimeSlot().getStart() + "-" + n.getTimeSlot().getEnd())
                .containsExactly("09:00-10:00", "10:30-12:30");
    }

    @Test
    void moveNodeAcrossDaysReschedulesBothDays() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        stubUpdateSuccess(guidebook);

        service.moveNode(GUIDEBOOK_ID, "d1-2", new MoveNodeRequest(2, 0));

        assertThat(guidebook.getDays().get(0).getNodes())
                .extracting(n -> n.getTimeSlot().getStart() + "-" + n.getTimeSlot().getEnd())
                .containsExactly("09:00-11:00");
        // 目标日原本为空：保留被移动节点的时段，只保证顺序与不重叠
        assertThat(guidebook.getDays().get(1).getNodes())
                .extracting(n -> n.getTimeSlot().getStart() + "-" + n.getTimeSlot().getEnd())
                .containsExactly("12:00-13:00");
    }

    @Test
    void moveNodeRejectsOutOfRangeIndexWithinSameDay() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertBizCode(ResultCode.PARAM_ERROR,
                () -> service.moveNode(GUIDEBOOK_ID, "d1-1", new MoveNodeRequest(1, 2)));
        verify(guidebookMapper, never()).updateById(any(Guidebook.class));
    }

    @Test
    void moveNodeRejectsMissingNode() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertBizCode(ResultCode.NOT_FOUND,
                () -> service.moveNode(GUIDEBOOK_ID, "missing", new MoveNodeRequest(2, 0)));
    }

    @Test
    void moveNodeRejectsUnknownTargetDay() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertBizCode(ResultCode.NOT_FOUND,
                () -> service.moveNode(GUIDEBOOK_ID, "d1-1", new MoveNodeRequest(99, 0)));
    }

    // ---- optimistic lock ----

    @Test
    void saveReturnsConflictWhenVersionWasChanged() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(guidebookMapper.updateById(guidebook)).thenReturn(0);

        assertBizCode(ResultCode.CONFLICT,
                () -> service.updateNode(GUIDEBOOK_ID, 1, "d1-1", new UpdateNodeRequest()));
        verify(versionService, never()).append(any(), anyString(), anyString(), any());
    }

    // ---- fixtures ----

    private Guidebook guidebook(String status) {
        return TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, status);
    }

    private void stubUpdateSuccess(Guidebook guidebook) {
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(guidebookMapper.updateById(guidebook)).thenReturn(1);
    }

    private AddNodeRequest newNode(Integer index) {
        return new AddNodeRequest(index, "meal",
                new TimeSlotDto("18:00", "19:00"),
                new PlaceDto("火锅店", 30.60, 104.10), "晚饭");
    }

    private void assertBizCode(ResultCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
