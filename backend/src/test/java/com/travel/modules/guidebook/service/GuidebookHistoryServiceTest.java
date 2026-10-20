package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookEditResponse;
import com.travel.modules.guidebook.dto.GuidebookVersionItem;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.GuidebookVersionMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class GuidebookHistoryServiceTest {

    private static final long USER_ID = 7L;
    private static final long GUIDEBOOK_ID = 5L;

    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private GuidebookVersionMapper versionMapper;
    @Mock
    private GuidebookVersionService versionService;
    @Mock
    private GuidebookConflictService conflictService;

    private GuidebookHistoryService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "tester"));
        service = new GuidebookHistoryService(guidebookMapper, versionMapper,
                versionService, conflictService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void listVersionsMapsLightweightItems() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(guidebook(Guidebook.STATUS_DRAFT));
        LocalDateTime now = LocalDateTime.now();
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                snapshot(3, "manual", "修改Day1", now),
                snapshot(2, "adjust", "AI 调整", now),
                snapshot(1, "generate", "初次生成", now)));

        List<GuidebookVersionItem> items = service.listVersions(GUIDEBOOK_ID);

        assertThat(items).extracting(GuidebookVersionItem::getVersion)
                .containsExactly(3, 2, 1);
        assertThat(items.get(0).getChangeType()).isEqualTo("manual");
        assertThat(items.get(0).getChangeSummary()).isEqualTo("修改Day1");
        assertThat(items.get(0).getCreatedAt()).isEqualTo(now);
    }

    @Test
    void listVersionsRejectsForeignGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(TestFixtures.guidebook(GUIDEBOOK_ID, 8L, Guidebook.STATUS_DRAFT));

        assertBizCode(ResultCode.NOT_OWNER, () -> service.listVersions(GUIDEBOOK_ID));
        verify(versionMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void listVersionsRejectsMissingGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(null);

        assertBizCode(ResultCode.NOT_FOUND, () -> service.listVersions(GUIDEBOOK_ID));
    }

    @Test
    void restoreCopiesSnapshotAndWritesNewVersion() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        GuidebookVersion snapshot = snapshot(1, "generate", "初次生成", LocalDateTime.now());
        snapshot.setTitle("旧标题");
        snapshot.setDays(List.of(new GuidebookDayDto(1, List.of())));
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(snapshot);
        when(guidebookMapper.updateById(guidebook)).thenReturn(1);
        when(conflictService.detect(guidebook)).thenReturn(List.of());

        GuidebookEditResponse response = service.restore(GUIDEBOOK_ID, 1);

        assertThat(response.getChangeSummary()).isEqualTo("回溯到 v1");
        assertThat(guidebook.getTitle()).isEqualTo("旧标题");
        assertThat(guidebook.getDays()).isSameAs(snapshot.getDays());
        assertThat(guidebook.getStatus()).isEqualTo(Guidebook.STATUS_EDITING);
        verify(versionService).append(eq(guidebook),
                eq(GuidebookVersion.CHANGE_TYPE_RESTORE), eq("回溯到 v1"), eq(USER_ID));
    }

    @Test
    void restoreReturnsConflictsFromDetector() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        NodeConflictDto conflict = new NodeConflictDto("time_overlap", 1,
                List.of("n1", "n2"), "时段重叠");
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(versionMapper.selectOne(any(Wrapper.class)))
                .thenReturn(snapshot(1, "generate", "初次生成", LocalDateTime.now()));
        when(guidebookMapper.updateById(guidebook)).thenReturn(1);
        when(conflictService.detect(guidebook)).thenReturn(List.of(conflict));

        assertThat(service.restore(GUIDEBOOK_ID, 1).getConflicts()).containsExactly(conflict);
    }

    @Test
    void restoreRejectsMissingVersion() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(guidebook(Guidebook.STATUS_DRAFT));
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertBizCode(ResultCode.NOT_FOUND, () -> service.restore(GUIDEBOOK_ID, 99));
        verify(guidebookMapper, never()).updateById(any(Guidebook.class));
    }

    @Test
    void restoreRejectsInvalidVersionNumber() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(guidebook(Guidebook.STATUS_DRAFT));

        assertBizCode(ResultCode.PARAM_ERROR, () -> service.restore(GUIDEBOOK_ID, 0));
        verify(versionMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void restoreRejectsNonEditableStatus() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(guidebook(Guidebook.STATUS_CONFIRMED));

        assertBizCode(ResultCode.CONFLICT, () -> service.restore(GUIDEBOOK_ID, 1));
        verify(versionMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void restoreReturnsConflictWhenVersionWasChanged() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(versionMapper.selectOne(any(Wrapper.class)))
                .thenReturn(snapshot(1, "generate", "初次生成", LocalDateTime.now()));
        when(guidebookMapper.updateById(guidebook)).thenReturn(0);

        assertBizCode(ResultCode.CONFLICT, () -> service.restore(GUIDEBOOK_ID, 1));
        verify(versionService, never()).append(any(), anyString(), anyString(), any());
    }

    private Guidebook guidebook(String status) {
        return TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, status);
    }

    private GuidebookVersion snapshot(int version, String type, String summary,
            LocalDateTime createdAt) {
        GuidebookVersion snapshot = new GuidebookVersion();
        snapshot.setGuidebookId(GUIDEBOOK_ID);
        snapshot.setVersion(version);
        snapshot.setTitle("攻略标题");
        snapshot.setChangeType(type);
        snapshot.setChangeSummary(summary);
        snapshot.setCreateTime(createdAt);
        return snapshot;
    }

    private void assertBizCode(ResultCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
