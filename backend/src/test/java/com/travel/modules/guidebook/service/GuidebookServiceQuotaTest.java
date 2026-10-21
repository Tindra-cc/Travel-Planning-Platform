package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;
import com.travel.modules.chat.mapper.ChatSessionMapper;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.generation.GuidebookGenerationRunner;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.profile.mapper.UserProfileMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class GuidebookServiceQuotaTest {

    private static final long USER_ID = 7L;

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
    private StringRedisTemplate redisTemplate;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ResourceService resourceService;

    private GuidebookService service() {
        return new GuidebookService(guidebookMapper, sessionMapper, userMapper, profileMapper,
                runner, redisTemplate, transactionTemplate, new ObjectMapper(), resourceService);
    }

    @Test
    void consumeQuotaDeductsWholeBatchAtOnce() {
        when(userMapper.selectForUpdate(USER_ID)).thenReturn(user(0, 3, LocalDate.now()));

        service().consumeQuota(USER_ID, 3);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDailyGenerateUsed()).isEqualTo(3);
    }

    @Test
    void consumeQuotaRejectsBatchLargerThanRemainingWithoutDeducting() {
        when(userMapper.selectForUpdate(USER_ID)).thenReturn(user(1, 3, LocalDate.now()));

        assertBizCode(() -> service().consumeQuota(USER_ID, 5), ResultCode.TOO_MANY_REQUEST);
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void consumeQuotaResetsOnNewDay() {
        when(userMapper.selectForUpdate(USER_ID))
                .thenReturn(user(3, 3, LocalDate.now().minusDays(1)));

        service().consumeQuota(USER_ID, 2);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDailyGenerateUsed()).isEqualTo(2);
        assertThat(captor.getValue().getGenerateQuotaDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void consumeQuotaOnceStillDeductsOne() {
        when(userMapper.selectForUpdate(USER_ID)).thenReturn(user(2, 3, LocalDate.now()));

        service().consumeQuotaOnce(USER_ID);

        verify(userMapper).updateById(any(User.class));
    }

    @Test
    void consumeQuotaRejectsMissingUser() {
        when(userMapper.selectForUpdate(USER_ID)).thenReturn(null);

        assertBizCode(() -> service().consumeQuota(USER_ID, 1), ResultCode.UNAUTHORIZED);
    }

    @Test
    void createExpertGuidebookMarksClientAndDoesNotConsumeQuota() {
        service().createExpertGuidebook(USER_ID, 11L, TestFixtures.intent(), Map.of("style", "慢游"));

        ArgumentCaptor<Guidebook> captor = ArgumentCaptor.forClass(Guidebook.class);
        verify(guidebookMapper).insert(captor.capture());
        Guidebook saved = captor.getValue();
        assertThat(saved.getExpertClientId()).isEqualTo(11L);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getSessionId()).isNull();
        assertThat(saved.getStatus()).isEqualTo(Guidebook.STATUS_GENERATING);
        verify(userMapper, never()).selectForUpdate(any());
    }

    private User user(int used, int quota, LocalDate quotaDate) {
        User user = TestFixtures.user(USER_ID, "1");
        user.setDailyGenerateUsed(used);
        user.setDailyGenerateQuota(quota);
        user.setGenerateQuotaDate(quotaDate);
        return user;
    }

    private void assertBizCode(Runnable action, ResultCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(code);
    }
}
