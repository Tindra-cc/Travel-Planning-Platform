package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.travel.modules.guidebook.dto.GuidebookValidateResponse;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.edit.ConflictDetector;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class GuidebookConflictServiceTest {

    private static final long USER_ID = 1L;
    private static final long GUIDEBOOK_ID = 5L;

    @Mock
    private GuidebookMapper guidebookMapper;

    private GuidebookConflictService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "user1@test.local"));
        service = new GuidebookConflictService(guidebookMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void returnsValidWhenNoConflicts() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(guidebook(Guidebook.STATUS_DRAFT));

        GuidebookValidateResponse response = service.validate(GUIDEBOOK_ID);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getConflicts()).isEmpty();
    }

    @Test
    void returnsConflictsWhenSlotsOverlap() {
        Guidebook guidebook = guidebook(Guidebook.STATUS_EDITING);
        guidebook.getDays().get(0).getNodes().get(1)
                .setTimeSlot(new TimeSlotDto("10:30", "12:00"));
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        GuidebookValidateResponse response = service.validate(GUIDEBOOK_ID);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getConflicts()).extracting(NodeConflictDto::getType)
                .containsExactly(ConflictDetector.TYPE_TIME_OVERLAP);
    }

    @Test
    void rejectsGeneratingGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(guidebook(Guidebook.STATUS_GENERATING));

        assertBizCode(ResultCode.CONFLICT);
    }

    @Test
    void rejectsFailedGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(guidebook(Guidebook.STATUS_FAILED));

        assertBizCode(ResultCode.CONFLICT);
    }

    @Test
    void allowsReadOnlyValidationForConfirmedGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(guidebook(Guidebook.STATUS_CONFIRMED));

        assertThat(service.validate(GUIDEBOOK_ID).isValid()).isTrue();
    }

    @Test
    void rejectsForeignGuidebook() {
        Guidebook foreign = TestFixtures.guidebook(GUIDEBOOK_ID, 2L, Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(foreign);

        assertBizCode(ResultCode.NOT_OWNER);
    }

    @Test
    void rejectsMissingGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(null);

        assertBizCode(ResultCode.NOT_FOUND);
    }

    private Guidebook guidebook(String status) {
        return TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, status);
    }

    private void assertBizCode(ResultCode expected) {
        assertThatThrownBy(() -> service.validate(GUIDEBOOK_ID))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
