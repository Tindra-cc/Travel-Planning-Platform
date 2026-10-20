package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
import com.travel.modules.guidebook.dto.GuidebookConfirmResponse;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class GuidebookConfirmServiceTest {

    private static final long USER_ID = 7L;
    private static final long GUIDEBOOK_ID = 5L;

    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private GuidebookConflictService conflictService;

    private GuidebookConfirmService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "tester"));
        service = new GuidebookConfirmService(guidebookMapper, conflictService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void confirmsDraftWithoutConflicts() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(conflictService.detect(guidebook)).thenReturn(List.of());
        when(guidebookMapper.updateById(guidebook)).thenReturn(1);

        GuidebookConfirmResponse response = service.confirm(GUIDEBOOK_ID);

        assertThat(response.getStatus()).isEqualTo(Guidebook.STATUS_CONFIRMED);
        assertThat(guidebook.getStatus()).isEqualTo(Guidebook.STATUS_CONFIRMED);
        verify(guidebookMapper).updateById(guidebook);
    }

    @Test
    void confirmsEditingWithoutConflicts() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EDITING);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(conflictService.detect(guidebook)).thenReturn(List.of());
        when(guidebookMapper.updateById(guidebook)).thenReturn(1);

        assertThat(service.confirm(GUIDEBOOK_ID).getStatus())
                .isEqualTo(Guidebook.STATUS_CONFIRMED);
    }

    @Test
    void blocksConfirmationWhenConflictsExist() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EDITING);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(conflictService.detect(guidebook)).thenReturn(List.of(
                new NodeConflictDto("time_overlap", 1, List.of("n1", "n2"), "时段重叠")));

        assertThatThrownBy(() -> service.confirm(GUIDEBOOK_ID))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.UNPROCESSABLE);
                    assertThat(e.getMessage()).contains("存在 1 处冲突", "时段重叠");
                });
        verify(guidebookMapper, never()).updateById(any(Guidebook.class));
        assertThat(guidebook.getStatus()).isEqualTo(Guidebook.STATUS_EDITING);
    }

    @Test
    void limitsConflictDetailsInMessage() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(conflictService.detect(guidebook)).thenReturn(List.of(
                new NodeConflictDto("time_overlap", 1, List.of("n1", "n2"), "冲突一"),
                new NodeConflictDto("unreachable", 1, List.of("n2", "n3"), "冲突二"),
                new NodeConflictDto("budget_exceeded", null, List.of(), "冲突三")));

        assertThatThrownBy(() -> service.confirm(GUIDEBOOK_ID))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getMessage()).contains("存在 3 处冲突", "冲突一", "冲突二");
                    assertThat(e.getMessage()).doesNotContain("冲突三");
                });
    }

    @Test
    void rejectsAlreadyConfirmedWithoutDetecting() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(guidebook(Guidebook.STATUS_CONFIRMED));

        assertBizCode(ResultCode.CONFLICT);
        verify(conflictService, never()).detect(any(Guidebook.class));
        verify(guidebookMapper, never()).updateById(any(Guidebook.class));
    }

    @Test
    void rejectsNonConfirmableStatuses() {
        for (String status : List.of(Guidebook.STATUS_GENERATING, Guidebook.STATUS_FAILED,
                Guidebook.STATUS_EXECUTING, Guidebook.STATUS_COMPLETED)) {
            when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook(status));

            assertBizCode(ResultCode.CONFLICT);
        }
        verify(conflictService, never()).detect(any(Guidebook.class));
    }

    @Test
    void rejectsForeignGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(TestFixtures.guidebook(GUIDEBOOK_ID, 8L, Guidebook.STATUS_DRAFT));

        assertBizCode(ResultCode.NOT_OWNER);
    }

    @Test
    void rejectsMissingGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(null);

        assertBizCode(ResultCode.NOT_FOUND);
    }

    @Test
    void returnsConflictWhenVersionChangedBeforeUpdate() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(conflictService.detect(guidebook)).thenReturn(List.of());
        when(guidebookMapper.updateById(guidebook)).thenReturn(0);

        assertBizCode(ResultCode.CONFLICT);
    }

    private Guidebook guidebook(String status) {
        return TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, status);
    }

    private void assertBizCode(ResultCode expected) {
        assertThatThrownBy(() -> service.confirm(GUIDEBOOK_ID))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
