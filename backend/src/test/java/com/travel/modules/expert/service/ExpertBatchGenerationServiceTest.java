package com.travel.modules.expert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.expert.dto.BatchGenerateRequest;
import com.travel.modules.expert.dto.BatchGenerateResponse;
import com.travel.modules.expert.dto.BatchTaskResponse;
import com.travel.modules.expert.entity.ExpertClient;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.generation.GenerationContext;
import com.travel.modules.guidebook.generation.GuidebookGenerationRunner;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.service.GuidebookService;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class ExpertBatchGenerationServiceTest {

    private static final long EXPERT_ID = 7L;
    private static final long CLIENT_ID = 11L;
    private static final long CLIENT_USER_ID = 21L;

    @Mock
    private ClientAuthorizationService authorizationService;
    @Mock
    private ClientAccessAuditService auditService;
    @Mock
    private GuidebookService guidebookService;
    @Mock
    private GuidebookGenerationRunner runner;
    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ExpertBatchGenerationService service;
    private final AtomicLong guidebookIds = new AtomicLong(1200);

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(EXPERT_ID, "expert"));
        service = new ExpertBatchGenerationService(authorizationService, auditService,
                guidebookService, runner, guidebookMapper, redisTemplate, transactionTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(values);
        lenient().when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        lenient().when(authorizationService.requireAuthorized(any(), any())).thenReturn(client());
        lenient().when(runner.hasCapacity()).thenReturn(true);
        lenient().when(guidebookService.loadProfileSnapshot(CLIENT_USER_ID)).thenReturn(Map.of("style", "慢游"));
        lenient().when(transactionTemplate.execute(
                        ArgumentMatchers.<TransactionCallback<Object>>any()))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(null));
        lenient().when(guidebookService.createExpertGuidebook(eq(EXPERT_ID), eq(CLIENT_ID),
                        any(IntentDto.class), any()))
                .thenAnswer(invocation -> {
                    Guidebook guidebook = new Guidebook();
                    guidebook.setId(guidebookIds.incrementAndGet());
                    guidebook.setIntent(invocation.getArgument(2));
                    return guidebook;
                });
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void rejectsEmptyAndOversizedBatches() {
        assertBizCode(() -> service.generate(CLIENT_ID, request(), null), ResultCode.PARAM_ERROR);
        assertBizCode(() -> service.generate(CLIENT_ID, request(intents(6)), null),
                ResultCode.PARAM_ERROR);
        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void validatesEveryIntentBeforeTakingLock() {
        doThrow(new BizException(ResultCode.UNPROCESSABLE, "目的地缺失，无法生成攻略"))
                .when(guidebookService).requireCompleteIntent(any());

        assertBizCode(() -> service.generate(CLIENT_ID, request(intents(2)), null),
                ResultCode.UNPROCESSABLE);
        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(guidebookService, never()).consumeQuota(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void requiresBothProfileAndGuidebooksScopes() {
        when(authorizationService.requireAuthorized(CLIENT_ID, "profile")).thenReturn(client());
        when(authorizationService.requireAuthorized(CLIENT_ID, "guidebooks"))
                .thenThrow(new BizException(ResultCode.FORBIDDEN, "客户未授权访问该数据"));

        assertBizCode(() -> service.generate(CLIENT_ID, request(intents(1)), null),
                ResultCode.FORBIDDEN);
        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void rejectsWhenBatchLockHeld() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertBizCode(() -> service.generate(CLIENT_ID, request(intents(2)), null),
                ResultCode.TOO_MANY_REQUEST);
        verify(guidebookService, never()).createExpertGuidebook(any(), any(), any(), any());
    }

    @Test
    void rejectsWhenPoolFullWithoutTouchingQuota() {
        when(runner.hasCapacity()).thenReturn(false);

        assertBizCode(() -> service.generate(CLIENT_ID, request(intents(2)), null),
                ResultCode.TOO_MANY_REQUEST);
        verify(guidebookService, never()).consumeQuota(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void consumesQuotaOnceForWholeBatchAndCreatesRows() {
        BatchGenerateResponse response = service.generate(CLIENT_ID, request(intents(3)), "1.2.3.4");

        verify(guidebookService).consumeQuota(EXPERT_ID, 3);
        verify(guidebookService, times(3)).createExpertGuidebook(eq(EXPERT_ID), eq(CLIENT_ID),
                any(IntentDto.class), any());
        assertThat(response.getTasks()).extracting(BatchTaskResponse::getStatus)
                .containsExactly("generating", "generating", "generating");
        assertThat(response.getTasks()).extracting(BatchTaskResponse::getTaskId)
                .containsExactly("1201", "1202", "1203");
        verify(guidebookService).loadProfileSnapshot(CLIENT_USER_ID);
        verify(auditService).record(eq(EXPERT_ID), eq(CLIENT_ID),
                eq(ClientAccessAuditService.ACTION_READ_PROFILE), eq("profile"),
                eq("user_profile"), eq(CLIENT_USER_ID), eq("1.2.3.4"));
        verify(auditService, times(3)).record(eq(EXPERT_ID), eq(CLIENT_ID),
                eq(ClientAccessAuditService.ACTION_GENERATE_GUIDEBOOK), eq("guidebooks"),
                eq("guidebook"), any(), eq("1.2.3.4"));
        verify(redisTemplate).delete("travel:expert:batch:lock:" + EXPERT_ID);
    }

    @Test
    void batchTasksDoNotHoldGenerateLock() {
        service.generate(CLIENT_ID, request(intents(1)), null);

        ArgumentCaptor<GenerationContext> captor = ArgumentCaptor.forClass(GenerationContext.class);
        verify(runner).submit(captor.capture());
        assertThat(captor.getValue().isHoldsGenerateLock()).isFalse();
    }

    @Test
    void submitFailureMarksOnlyThatRowFailed() {
        Guidebook failed = new Guidebook();
        failed.setId(1202L);
        when(guidebookService.createExpertGuidebook(eq(EXPERT_ID), eq(CLIENT_ID),
                        any(IntentDto.class), any()))
                .thenReturn(book(1201L))
                .thenReturn(failed);
        doThrow(new RuntimeException("pool full")).doNothing().when(runner).submit(any());

        BatchGenerateResponse response = service.generate(CLIENT_ID, request(intents(2)), null);

        assertThat(response.getTasks()).extracting(BatchTaskResponse::getStatus)
                .containsExactly("failed", "generating");
        verify(guidebookMapper).update(any(), any());
        // 失败任务不写 generate_guidebook 审计
        verify(auditService, times(1)).record(eq(EXPERT_ID), eq(CLIENT_ID),
                eq(ClientAccessAuditService.ACTION_GENERATE_GUIDEBOOK), any(), any(), any(), any());
    }

    @Test
    void quotaFailureRollsBackWholeBatch() {
        doThrow(new BizException(ResultCode.TOO_MANY_REQUEST, "今日生成次数不足"))
                .when(guidebookService).consumeQuota(EXPERT_ID, 2);

        assertBizCode(() -> service.generate(CLIENT_ID, request(intents(2)), null),
                ResultCode.TOO_MANY_REQUEST);
        verify(guidebookService, never()).createExpertGuidebook(any(), any(), any(), any());
        verify(redisTemplate).delete(anyString());
    }

    private Guidebook book(long id) {
        Guidebook guidebook = new Guidebook();
        guidebook.setId(id);
        return guidebook;
    }

    private ExpertClient client() {
        ExpertClient client = new ExpertClient();
        client.setId(CLIENT_ID);
        client.setExpertId(EXPERT_ID);
        client.setLinkedUserId(CLIENT_USER_ID);
        client.setStatus(ExpertClient.STATUS_ACTIVE);
        return client;
    }

    private BatchGenerateRequest request() {
        return request(List.of());
    }

    private BatchGenerateRequest request(List<IntentDto> intents) {
        BatchGenerateRequest request = new BatchGenerateRequest();
        request.setIntents(intents);
        return request;
    }

    private List<IntentDto> intents(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> TestFixtures.intent())
                .toList();
    }

    private void assertBizCode(Runnable action, ResultCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(code);
    }
}
