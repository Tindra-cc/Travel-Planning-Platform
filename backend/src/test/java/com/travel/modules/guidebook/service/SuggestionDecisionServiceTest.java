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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.dto.SuggestionDecisionResponse;
import com.travel.modules.guidebook.dto.SuggestionOperation;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.entity.TripSuggestion;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.TripSuggestionMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class SuggestionDecisionServiceTest {

    private static final long USER_ID = 7L;
    private static final long GUIDEBOOK_ID = 5L;
    private static final long SUGGESTION_ID = 9L;

    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private TripSuggestionMapper suggestionMapper;
    @Mock
    private GuidebookVersionService versionService;
    @Mock
    private GuidebookConflictService conflictService;

    private SuggestionDecisionService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "tester"));
        service = new SuggestionDecisionService(guidebookMapper, suggestionMapper,
                versionService, conflictService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void acceptAppliesOperationsAndAppendsCompanionSnapshot() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING, move("d1-1", 2));
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID,
                Guidebook.STATUS_EXECUTING);
        stubPending(suggestion, guidebook);
        when(guidebookMapper.updateById(guidebook)).thenAnswer(invocation -> {
            Guidebook saved = invocation.getArgument(0);
            saved.setVersion(saved.getVersion() + 1);
            return 1;
        });
        when(conflictService.detect(any(Guidebook.class))).thenReturn(List.of());
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        SuggestionDecisionResponse response = service.accept(GUIDEBOOK_ID, SUGGESTION_ID);

        assertThat(response.getStatus()).isEqualTo(TripSuggestion.STATUS_ACCEPTED);
        assertThat(response.getApplied()).isTrue();
        assertThat(response.getVersion()).isEqualTo(2);
        assertThat(ids(guidebook)).containsExactly("d1-2");
        GuidebookNodeDto moved = guidebook.getDays().get(1).getNodes().get(0);
        assertThat(moved.getNodeId()).isEqualTo("d1-1");
        assertThat(moved.getTimeSlot().getStart()).isEqualTo("08:00");
        verify(versionService).append(eq(guidebook),
                eq(GuidebookVersion.CHANGE_TYPE_COMPANION), eq("移到 Day2"), eq(USER_ID));
    }

    @Test
    void acceptHintMarksAcceptedWithoutTouchingGuidebook() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING);
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID,
                Guidebook.STATUS_EXECUTING);
        stubPending(suggestion, guidebook);
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        SuggestionDecisionResponse response = service.accept(GUIDEBOOK_ID, SUGGESTION_ID);

        assertThat(response.getApplied()).isFalse();
        assertThat(response.getVersion()).isEqualTo(1);
        verify(guidebookMapper, never()).updateById(any());
        verify(versionService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    void acceptReturnsUnprocessableWhenConflictsRemain() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING, move("d1-1", 2));
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID,
                Guidebook.STATUS_EXECUTING);
        stubPending(suggestion, guidebook);
        when(conflictService.detect(any(Guidebook.class)))
                .thenReturn(List.of(new NodeConflictDto("time_overlap", 2,
                        List.of("n1"), "时段重叠")));

        assertBizCode(ResultCode.UNPROCESSABLE,
                () -> service.accept(GUIDEBOOK_ID, SUGGESTION_ID));
        verify(guidebookMapper, never()).updateById(any());
        verify(versionService, never()).append(any(), anyString(), anyString(), any());
        verify(suggestionMapper, never()).update(any(), any());
    }

    @Test
    void acceptFailsWhenGuidebookChangedConcurrently() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING, move("d1-1", 2));
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID,
                Guidebook.STATUS_EXECUTING);
        stubPending(suggestion, guidebook);
        when(conflictService.detect(any(Guidebook.class))).thenReturn(List.of());
        when(guidebookMapper.updateById(any(Guidebook.class))).thenReturn(0);

        assertBizCode(ResultCode.CONFLICT,
                () -> service.accept(GUIDEBOOK_ID, SUGGESTION_ID));
        verify(versionService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    void acceptFailsWhenSuggestionAlreadyDecided() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_ACCEPTED, move("d1-1", 2));
        when(suggestionMapper.selectById(SUGGESTION_ID)).thenReturn(suggestion);

        assertBizCode(ResultCode.CONFLICT,
                () -> service.accept(GUIDEBOOK_ID, SUGGESTION_ID));
        verify(guidebookMapper, never()).selectById(any());
    }

    @Test
    void acceptFailsWhenSuggestionBelongsToAnotherGuidebook() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING, move("d1-1", 2));
        suggestion.setGuidebookId(GUIDEBOOK_ID + 1);
        when(suggestionMapper.selectById(SUGGESTION_ID)).thenReturn(suggestion);

        assertBizCode(ResultCode.NOT_FOUND,
                () -> service.accept(GUIDEBOOK_ID, SUGGESTION_ID));
    }

    @Test
    void acceptFailsWhenSuggestionExpired() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING, move("d1-1", 2));
        suggestion.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(suggestionMapper.selectById(SUGGESTION_ID)).thenReturn(suggestion);

        assertBizCode(ResultCode.CONFLICT,
                () -> service.accept(GUIDEBOOK_ID, SUGGESTION_ID));
        verify(suggestionMapper).update(any(), any());
        verify(guidebookMapper, never()).selectById(any());
    }

    @Test
    void acceptRejectsForeignGuidebook() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING, move("d1-1", 2));
        Guidebook foreign = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID + 1,
                Guidebook.STATUS_EXECUTING);
        stubPending(suggestion, foreign);

        assertBizCode(ResultCode.NOT_OWNER,
                () -> service.accept(GUIDEBOOK_ID, SUGGESTION_ID));
    }

    @Test
    void acceptRejectsGuidebookNotExecuting() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING, move("d1-1", 2));
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID,
                Guidebook.STATUS_CONFIRMED);
        stubPending(suggestion, guidebook);

        assertBizCode(ResultCode.CONFLICT,
                () -> service.accept(GUIDEBOOK_ID, SUGGESTION_ID));
        verify(conflictService, never()).detect(any());
    }

    @Test
    void acceptRejectsMissingNode() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING,
                move("missing", 2));
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID,
                Guidebook.STATUS_EXECUTING);
        stubPending(suggestion, guidebook);

        assertBizCode(ResultCode.NOT_FOUND,
                () -> service.accept(GUIDEBOOK_ID, SUGGESTION_ID));
        verify(guidebookMapper, never()).updateById(any());
    }

    @Test
    void rejectMarksSuggestionWithoutChangingGuidebook() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING, move("d1-1", 2));
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID,
                Guidebook.STATUS_EXECUTING);
        stubPending(suggestion, guidebook);
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        SuggestionDecisionResponse response = service.reject(GUIDEBOOK_ID, SUGGESTION_ID);

        assertThat(response.getStatus()).isEqualTo(TripSuggestion.STATUS_REJECTED);
        assertThat(response.getApplied()).isFalse();
        assertThat(response.getVersion()).isNull();
        verify(guidebookMapper, never()).updateById(any());
        verify(versionService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    void rejectRejectsForeignGuidebook() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING, move("d1-1", 2));
        Guidebook foreign = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID + 1,
                Guidebook.STATUS_EXECUTING);
        stubPending(suggestion, foreign);

        assertBizCode(ResultCode.NOT_OWNER,
                () -> service.reject(GUIDEBOOK_ID, SUGGESTION_ID));
        verify(suggestionMapper, never()).update(any(), any());
    }

    @Test
    void rejectFailsWhenConditionalUpdateMatchesNothing() {
        TripSuggestion suggestion = suggestion(TripSuggestion.STATUS_PENDING, move("d1-1", 2));
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID,
                Guidebook.STATUS_EXECUTING);
        when(suggestionMapper.selectById(SUGGESTION_ID)).thenReturn(suggestion);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(suggestionMapper.update(any(), any())).thenReturn(0);

        assertBizCode(ResultCode.CONFLICT,
                () -> service.reject(GUIDEBOOK_ID, SUGGESTION_ID));
    }

    private void stubPending(TripSuggestion suggestion, Guidebook guidebook) {
        when(suggestionMapper.selectById(SUGGESTION_ID)).thenReturn(suggestion);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
    }

    private static TripSuggestion suggestion(String status, SuggestionOperation... operations) {
        TripSuggestion suggestion = new TripSuggestion();
        suggestion.setId(SUGGESTION_ID);
        suggestion.setGuidebookId(GUIDEBOOK_ID);
        suggestion.setUserId(USER_ID);
        suggestion.setStatus(status);
        suggestion.setSuggestionType(TripSuggestion.TYPE_WEATHER);
        suggestion.setAction("移到 Day2");
        suggestion.setOperations(List.of(operations));
        return suggestion;
    }

    private static SuggestionOperation move(String nodeId, Integer toDay) {
        return new SuggestionOperation(SuggestionOperation.MOVE_NODE, nodeId, toDay,
                new TimeSlotDto("08:00", "09:00"), null);
    }

    private static List<String> ids(Guidebook guidebook) {
        return guidebook.getDays().get(0).getNodes().stream()
                .map(GuidebookNodeDto::getNodeId)
                .toList();
    }

    private static void assertBizCode(ResultCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
