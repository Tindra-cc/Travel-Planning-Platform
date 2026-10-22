package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.chat.entity.ChatSession;
import com.travel.modules.chat.mapper.ChatSessionMapper;
import com.travel.modules.guidebook.dto.ExportResponse;
import com.travel.modules.guidebook.dto.GenerateGuidebookRequest;
import com.travel.modules.guidebook.dto.GenerateGuidebookResponse;
import com.travel.modules.guidebook.dto.GuidebookDetailResponse;
import com.travel.modules.guidebook.dto.GuidebookListItem;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.generation.GenerationContext;
import com.travel.modules.guidebook.generation.GuidebookGenerationRunner;
import com.travel.modules.guide.service.GuideReferenceBuilder;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.profile.mapper.UserProfileMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class GuidebookServiceTest {

    private static final long USER_ID = 1L;
    private static final long GUIDEBOOK_ID = 5L;
    private static final String LOCK_KEY = "travel:gen:lock:" + USER_ID;

    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private ChatSessionMapper sessionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserProfileMapper profileMapper;
    @Mock
    private GuidebookGenerationRunner runner;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ResourceService resourceService;
    @Mock
    private GuideReferenceBuilder guideReferenceBuilder;

    private GuidebookService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "user1@test.local"));
        service = new GuidebookService(guidebookMapper, sessionMapper, userMapper,
                profileMapper, runner, redis, transactionTemplate, new ObjectMapper(),
                resourceService, guideReferenceBuilder);
        lenientSetUp();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void lenientSetUp() {
        org.mockito.Mockito.lenient().when(redis.opsForValue()).thenReturn(values);
        org.mockito.Mockito.lenient().when(profileMapper.selectOne(any(Wrapper.class)))
                .thenReturn(null);
        org.mockito.Mockito.lenient()
                .when(transactionTemplate.execute(
                        ArgumentMatchers.<TransactionCallback<Object>>any()))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(null));
        // retry 的回调使用 LambdaUpdateWrapper，脱离 MyBatis 容器无法构造列名，单测中跳过执行。
        org.mockito.Mockito.lenient()
                .doNothing().when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void generateFromIntentCreatesGuidebookAndConsumesQuota() {
        GenerateGuidebookRequest request = directRequest();
        stubGenerationAllowed(newUser());

        GenerateGuidebookResponse response = service.generate(request);

        assertThat(response.getGuidebookId()).isEqualTo(50L);
        assertThat(response.getStatus()).isEqualTo(Guidebook.STATUS_GENERATING);
        assertThat(response.getEstimatedSeconds()).isEqualTo(30);
        verify(userMapper).updateById(any(User.class));
        verify(runner).submit(any(GenerationContext.class));
    }

    @Test
    void generateRejectsIncompleteIntent() {
        GenerateGuidebookRequest request = new GenerateGuidebookRequest();
        request.setIntent(new IntentDto());

        assertBizCode(() -> service.generate(request), ResultCode.UNPROCESSABLE);
        verify(values, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    void generateRejectsSessionNotReady() {
        ChatSession session = TestFixtures.session(7L, USER_ID, ChatSession.STATUS_COLLECTING);
        GenerateGuidebookRequest request = sessionRequest(7L);
        when(sessionMapper.selectById(7L)).thenReturn(session);
        when(guidebookMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertBizCode(() -> service.generate(request), ResultCode.UNPROCESSABLE);
    }

    @Test
    void generateReturnsExistingActiveGuidebookForSameSession() {
        ChatSession session = TestFixtures.session(7L, USER_ID, ChatSession.STATUS_READY);
        Guidebook existing = TestFixtures.guidebook(9L, USER_ID, Guidebook.STATUS_DRAFT);
        when(sessionMapper.selectById(7L)).thenReturn(session);
        when(guidebookMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        GenerateGuidebookResponse response = service.generate(sessionRequest(7L));

        assertThat(response.getGuidebookId()).isEqualTo(9L);
        assertThat(response.getStatus()).isEqualTo(Guidebook.STATUS_DRAFT);
        assertThat(response.getEstimatedSeconds()).isZero();
        verify(values, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    void generateRejectsForeignSession() {
        ChatSession session = TestFixtures.session(7L, 2L, ChatSession.STATUS_READY);
        when(sessionMapper.selectById(7L)).thenReturn(session);

        assertBizCode(() -> service.generate(sessionRequest(7L)), ResultCode.NOT_OWNER);
    }

    @Test
    void generateRejectsSecondConcurrentTask() {
        when(values.setIfAbsent(eq(LOCK_KEY), eq("1"), any(Duration.class))).thenReturn(false);

        assertBizCode(() -> service.generate(directRequest()), ResultCode.TOO_MANY_REQUEST);
        verify(userMapper, never()).selectForUpdate(any());
    }

    @Test
    void generateReleasesLockWhenPoolIsFull() {
        when(values.setIfAbsent(eq(LOCK_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(runner.hasCapacity()).thenReturn(false);

        assertBizCode(() -> service.generate(directRequest()), ResultCode.TOO_MANY_REQUEST);
        verify(redis).delete(LOCK_KEY);
    }

    @Test
    void quotaResetsOnNewDay() {
        User user = newUser();
        user.setDailyGenerateUsed(3);
        user.setGenerateQuotaDate(LocalDate.now().minusDays(1));
        stubGenerationAllowed(user);

        service.generate(directRequest());

        assertThat(user.getDailyGenerateUsed()).isEqualTo(1);
    }

    @Test
    void quotaExceededStillGenerates() {
        User user = newUser();
        user.setDailyGenerateUsed(3);
        stubGenerationAllowed(user);

        GenerateGuidebookResponse response = service.generate(directRequest());

        assertThat(response.getGuidebookId()).isEqualTo(50L);
        assertThat(user.getDailyGenerateUsed()).isEqualTo(4);
        verify(guidebookMapper).insert(any(Guidebook.class));
    }

    @Test
    void retryRejectsGuidebookThatIsNotFailed() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_DRAFT));

        assertBizCode(() -> service.retry(GUIDEBOOK_ID), ResultCode.CONFLICT);
    }

    @Test
    void retryRejectsAfterMaxAttempts() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_FAILED);
        guidebook.setAttemptCount(3);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertBizCode(() -> service.retry(GUIDEBOOK_ID), ResultCode.UNPROCESSABLE);
    }

    @Test
    void retryResubmitsFailedGuidebook() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_FAILED);
        guidebook.setAttemptCount(1);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(values.setIfAbsent(eq(LOCK_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(runner.hasCapacity()).thenReturn(true);

        GenerateGuidebookResponse response = service.retry(GUIDEBOOK_ID);

        assertThat(response.getStatus()).isEqualTo(Guidebook.STATUS_GENERATING);
        verify(runner).submit(any(GenerationContext.class));
    }

    @Test
    void retrySubmitFailureMarksGuidebookFailed() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_FAILED);
        guidebook.setAttemptCount(1);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(values.setIfAbsent(eq(LOCK_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(runner.hasCapacity()).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("pool rejected"))
                .when(runner).submit(any(GenerationContext.class));

        assertBizCode(() -> service.retry(GUIDEBOOK_ID), ResultCode.TOO_MANY_REQUEST);

        ArgumentCaptor<Guidebook> captor = ArgumentCaptor.forClass(Guidebook.class);
        verify(guidebookMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Guidebook.STATUS_FAILED);
        verify(redis).delete(LOCK_KEY);
    }

    @Test
    void detailRejectsForeignGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(TestFixtures.guidebook(GUIDEBOOK_ID, 2L, Guidebook.STATUS_DRAFT));

        assertBizCode(() -> service.detail(GUIDEBOOK_ID), ResultCode.NOT_OWNER);
    }

    @Test
    void detailReturnsGeneratingProgress() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_GENERATING);
        guidebook.setCurrentStep("itinerary");
        guidebook.setProgress(40);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        GuidebookDetailResponse response = service.detail(GUIDEBOOK_ID);

        assertThat(response.getStatus()).isEqualTo(Guidebook.STATUS_GENERATING);
        assertThat(response.getProgress()).isEqualTo(40);
        assertThat(response.getDisclaimer()).isNotBlank();
    }

    @Test
    void deleteRejectsConfirmedGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_CONFIRMED));

        assertBizCode(() -> service.delete(GUIDEBOOK_ID), ResultCode.CONFLICT);
        verify(guidebookMapper, never()).deleteById(GUIDEBOOK_ID);
    }

    @Test
    void deleteAllowsCompletedGuidebook() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_COMPLETED));

        service.delete(GUIDEBOOK_ID);

        verify(guidebookMapper).deleteById(GUIDEBOOK_ID);
    }

    @Test
    void exportRejectsPdfAndUnknownFormats() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_DRAFT));

        assertBizCode(() -> service.export(GUIDEBOOK_ID, "pdf"), ResultCode.UNPROCESSABLE);
        assertBizCode(() -> service.export(GUIDEBOOK_ID, "docx"), ResultCode.PARAM_ERROR);
    }

    @Test
    void exportRejectsUnfinishedGuidebook() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_GENERATING);
        guidebook.setDays(null);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertBizCode(() -> service.export(GUIDEBOOK_ID, "markdown"), ResultCode.CONFLICT);
    }

    @Test
    void exportMarkdownContainsTitleNodesAndDisclaimer() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_DRAFT));

        ExportResponse response = service.export(GUIDEBOOK_ID, "markdown");

        assertThat(response.getFormat()).isEqualTo("markdown");
        assertThat(response.getFilename()).isEqualTo("guidebook-5.md");
        assertThat(response.getContent())
                .contains("# 成都两日攻略")
                .contains("宽窄巷子")
                .contains("预算拆解")
                .contains("AI 辅助生成");
    }

    @Test
    void exportHtmlWrapsContentInDocument() {
        when(guidebookMapper.selectById(GUIDEBOOK_ID))
                .thenReturn(TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_DRAFT));

        ExportResponse response = service.export(GUIDEBOOK_ID, "html");

        assertThat(response.getFilename()).isEqualTo("guidebook-5.html");
        assertThat(response.getContent()).startsWith("<!doctype html>").contains("<h1>");
    }

    @Test
    void listReturnsPagedItems() {
        Page<Guidebook> page = new Page<>(1, 10);
        page.setRecords(List.of(
                TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_DRAFT)));
        page.setTotal(1);
        when(guidebookMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<Guidebook>>any(), any(Wrapper.class)))
                .thenReturn(page);

        PageResult<GuidebookListItem> result = service.list(null, 1, 10);

        assertThat(result.getList()).extracting(GuidebookListItem::getGuidebookId)
                .containsExactly(GUIDEBOOK_ID);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    private void stubGenerationAllowed(User user) {
        when(values.setIfAbsent(eq(LOCK_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(runner.hasCapacity()).thenReturn(true);
        when(userMapper.selectForUpdate(USER_ID)).thenReturn(user);
        when(guidebookMapper.insert(any(Guidebook.class))).thenAnswer(invocation -> {
            Guidebook guidebook = invocation.getArgument(0);
            guidebook.setId(50L);
            return 1;
        });
    }

    private User newUser() {
        User user = TestFixtures.user(USER_ID, "1");
        user.setGenerateQuotaDate(LocalDate.now());
        return user;
    }

    private GenerateGuidebookRequest directRequest() {
        GenerateGuidebookRequest request = new GenerateGuidebookRequest();
        request.setIntent(TestFixtures.intent());
        return request;
    }

    private GenerateGuidebookRequest sessionRequest(long sessionId) {
        GenerateGuidebookRequest request = new GenerateGuidebookRequest();
        request.setSessionId(sessionId);
        return request;
    }

    private void assertBizCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
