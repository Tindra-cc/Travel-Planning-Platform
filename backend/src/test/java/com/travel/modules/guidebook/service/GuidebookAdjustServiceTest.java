package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.AiClient;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.guidebook.dto.AdjustResponse;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class GuidebookAdjustServiceTest {

    private static final long USER_ID = 7L;
    private static final long GUIDEBOOK_ID = 5L;

    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private GuidebookVersionService versionService;
    @Mock
    private GuidebookConflictService conflictService;
    @Mock
    private AiClient aiClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private TransactionTemplate transactionTemplate;

    private GuidebookAdjustService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "tester"));
        service = new GuidebookAdjustService(guidebookMapper, versionService, conflictService,
                aiClient, new ObjectMapper(), redisTemplate, transactionTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(values);
        lenient().when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        lenient().when(transactionTemplate.execute(
                        ArgumentMatchers.<TransactionCallback<Object>>any()))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(null));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void appliesModelOutputAndWritesAdjustSnapshot() {
        Guidebook guidebook = editableGuidebook();
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(aiClient.chat(any())).thenReturn("""
                {"changeSummary":"把博物馆挪到 Day2",
                 "changes":[{"type":"move_node","day":1,"nodeId":"n1"}],
                 "title":"新标题",
                 "days":[{"day":1,"nodes":[
                   {"nodeId":"n1","timeSlot":{"start":"09:00","end":"10:00"},
                    "type":"activity","place":{"name":"博物馆"}}]}],
                 "budgetBreakdown":null}
                """);
        when(guidebookMapper.updateById(guidebook)).thenReturn(1);
        when(conflictService.detect(guidebook)).thenReturn(List.of());

        AdjustResponse response = service.adjust(GUIDEBOOK_ID, "把博物馆挪到第二天");

        assertThat(response.getChangeSummary()).isEqualTo("把博物馆挪到 Day2");
        assertThat(response.getChanges()).hasSize(1);
        assertThat(response.getChanges().get(0)).containsEntry("type", "move_node");
        assertThat(response.getConflicts()).isEmpty();
        assertThat(guidebook.getStatus()).isEqualTo(Guidebook.STATUS_EDITING);
        assertThat(guidebook.getTitle()).isEqualTo("新标题");
        assertThat(guidebook.getDays().get(0).getNodes().get(0).getPlace().getName())
                .isEqualTo("博物馆");
        verify(versionService).append(eq(guidebook),
                eq(GuidebookVersion.CHANGE_TYPE_ADJUST), eq("把博物馆挪到 Day2"), eq(USER_ID));
    }

    @Test
    void normalizesInvalidNodeFromModel() {
        Guidebook guidebook = editableGuidebook();
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(aiClient.chat(any())).thenReturn("""
                {"days":[{"day":1,"nodes":[
                  {"nodeId":"","timeSlot":{"start":"9点","end":"10点"},
                   "type":"shopping","place":null}]}]}
                """);
        when(guidebookMapper.updateById(guidebook)).thenReturn(1);
        when(conflictService.detect(guidebook)).thenReturn(List.of());

        AdjustResponse response = service.adjust(GUIDEBOOK_ID, "随便调一下");

        GuidebookNodeDto node = guidebook.getDays().get(0).getNodes().get(0);
        assertThat(node.getNodeId()).startsWith("n").hasSize(9);
        assertThat(node.getType()).isEqualTo("activity");
        assertThat(node.getTimeSlot().getStart()).isEqualTo("09:00");
        assertThat(node.getTimeSlot().getEnd()).isEqualTo("10:00");
        assertThat(node.getPlace().getName()).isEqualTo("待定地点");
        assertThat(response.getChangeSummary()).isEqualTo("AI 调整：随便调一下");
    }

    @Test
    void rejectsUnparsableOutputWithoutWriting() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(editableGuidebook());
        when(aiClient.chat(any())).thenReturn("抱歉，我无法完成这个修改");

        assertBizCode(ResultCode.UNPROCESSABLE, () -> service.adjust(GUIDEBOOK_ID, "改一下"));
        verify(guidebookMapper, never()).updateById(any(Guidebook.class));
        verify(versionService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    void rejectsEmptyDaysWithoutWriting() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(editableGuidebook());
        when(aiClient.chat(any())).thenReturn("{\"days\":[]}");

        assertBizCode(ResultCode.UNPROCESSABLE, () -> service.adjust(GUIDEBOOK_ID, "改一下"));
        verify(guidebookMapper, never()).updateById(any(Guidebook.class));
    }

    @Test
    void mapsModelFailureToDependencyUnavailable() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(editableGuidebook());
        when(aiClient.chat(any()))
                .thenThrow(new BizException(ResultCode.DEPENDENCY_UNAVAILABLE, "AI 服务暂不可用"));

        assertBizCode(ResultCode.DEPENDENCY_UNAVAILABLE,
                () -> service.adjust(GUIDEBOOK_ID, "改一下"));
        verify(guidebookMapper, never()).updateById(any(Guidebook.class));
    }

    @Test
    void mapsUnexpectedModelExceptionToDependencyUnavailable() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(editableGuidebook());
        when(aiClient.chat(any())).thenThrow(new IllegalStateException("timeout"));

        assertBizCode(ResultCode.DEPENDENCY_UNAVAILABLE,
                () -> service.adjust(GUIDEBOOK_ID, "改一下"));
    }

    @Test
    void rejectsBlankInstructionBeforeLoadingGuidebook() {
        assertBizCode(ResultCode.PARAM_ERROR, () -> service.adjust(GUIDEBOOK_ID, "  "));
        verify(guidebookMapper, never()).selectById(any());
    }

    @Test
    void rejectsInstructionOverLimit() {
        assertBizCode(ResultCode.PARAM_ERROR,
                () -> service.adjust(GUIDEBOOK_ID, "改".repeat(501)));
        verify(guidebookMapper, never()).selectById(any());
    }

    @Test
    void rejectsConfirmedGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(guidebook(Guidebook.STATUS_CONFIRMED));

        assertBizCode(ResultCode.CONFLICT, () -> service.adjust(GUIDEBOOK_ID, "改一下"));
        verify(aiClient, never()).chat(any());
    }

    @Test
    void rejectsForeignGuidebook() {
        Guidebook foreign = TestFixtures.guidebook(GUIDEBOOK_ID, 8L, Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(foreign);

        assertBizCode(ResultCode.NOT_OWNER, () -> service.adjust(GUIDEBOOK_ID, "改一下"));
    }

    @Test
    void rejectsWhenLockIsHeld() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(editableGuidebook());
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertBizCode(ResultCode.TOO_MANY_REQUEST, () -> service.adjust(GUIDEBOOK_ID, "改一下"));
        verify(aiClient, never()).chat(any());
    }

    @Test
    void returnsConflictWhenVersionChangedDuringModelCall() {
        Guidebook guidebook = editableGuidebook();
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(aiClient.chat(any())).thenReturn(validJson());
        when(guidebookMapper.updateById(guidebook)).thenReturn(0);

        assertBizCode(ResultCode.CONFLICT, () -> service.adjust(GUIDEBOOK_ID, "改一下"));
        verify(versionService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    void returnsDetectorConflictsAfterSuccessfulAdjust() {
        Guidebook guidebook = editableGuidebook();
        NodeConflictDto conflict = new NodeConflictDto("time_overlap", 1,
                List.of("n1", "n2"), "时段重叠");
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(aiClient.chat(any())).thenReturn(validJson());
        when(guidebookMapper.updateById(guidebook)).thenReturn(1);
        when(conflictService.detect(guidebook)).thenReturn(List.of(conflict));

        AdjustResponse response = service.adjust(GUIDEBOOK_ID, "改一下");

        assertThat(response.getConflicts()).containsExactly(conflict);
    }

    @Test
    void rejectsPromptThatIsTooLong() {
        Guidebook guidebook = editableGuidebook();
        guidebook.getDays().get(0).getNodes().get(0).setNote("很长".repeat(13_000));
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertBizCode(ResultCode.UNPROCESSABLE, () -> service.adjust(GUIDEBOOK_ID, "改一下"));
        verify(aiClient, never()).chat(any());
    }

    private Guidebook editableGuidebook() {
        return TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_DRAFT);
    }

    private Guidebook guidebook(String status) {
        return TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, status);
    }

    private String validJson() {
        return """
                {"changeSummary":"已调整","days":[{"day":1,"nodes":[
                  {"nodeId":"n1","timeSlot":{"start":"09:00","end":"10:00"},
                   "type":"activity","place":{"name":"博物馆"}}]}]}
                """;
    }

    private void assertBizCode(ResultCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
