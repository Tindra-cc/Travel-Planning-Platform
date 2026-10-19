package com.travel.modules.guidebook;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.GenerateGuidebookRequest;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.service.GuidebookService;

/**
 * 无 AI key 的完整链路：触发 → 线程池 → 知识/实时数据 → 模板降级 → draft。
 * 需要本地 PostgreSQL 与 Redis（与 dev 环境一致）。
 */
@SpringBootTest(properties = {
        "ai.deepseek.api-key=",
        "ai.dashscope.api-key="
})
class GuidebookGenerationE2ETest {

    @Autowired
    private GuidebookService guidebookService;
    @Autowired
    private GuidebookMapper guidebookMapper;
    @Autowired
    private UserMapper userMapper;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setAccount("e2e-" + System.nanoTime() + "@test.local");
        user.setAccountType("email");
        user.setPasswordHash("x");
        user.setNickname("e2e");
        user.setStatus(1);
        user.setDailyGenerateQuota(3);
        user.setDailyGenerateUsed(0);
        user.setProfileCompleted(false);
        userMapper.insert(user);
        userId = user.getId();
        UserContext.set(new LoginUser(userId, user.getAccount()));
    }

    @AfterEach
    void tearDown() {
        guidebookMapper.delete(Wrappers.<Guidebook>lambdaQuery()
                .eq(Guidebook::getUserId, userId));
        userMapper.deleteById(userId);
        UserContext.clear();
    }

    @Test
    void generatesDraftWithTemplateWhenNoAiKeyConfigured() throws Exception {
        GenerateGuidebookRequest request = new GenerateGuidebookRequest();
        request.setIntent(intent());
        request.setUseProfile(false);

        var response = guidebookService.generate(request);
        assertThat(response.getGuidebookId()).isNotNull();

        Guidebook guidebook = awaitTerminal(response.getGuidebookId());
        assertThat(guidebook.getStatus()).isEqualTo(Guidebook.STATUS_DRAFT);
        assertThat(guidebook.getDays()).hasSize(2);
        assertThat(guidebook.getDegradedMode()).isTrue();
        assertThat(guidebook.getDegradationReasons()).contains("model_fallback");
    }

    private Guidebook awaitTerminal(Long guidebookId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        while (System.currentTimeMillis() < deadline
                && guidebook != null
                && Guidebook.STATUS_GENERATING.equals(guidebook.getStatus())) {
            Thread.sleep(500);
            guidebook = guidebookMapper.selectById(guidebookId);
        }
        assertThat(guidebook).isNotNull();
        assertThat(guidebook.getStatus()).isNotEqualTo(Guidebook.STATUS_GENERATING);
        return guidebook;
    }

    private IntentDto intent() {
        IntentDto intent = new IntentDto();
        intent.setDestination("成都");
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart("2026-11-01");
        window.setEnd("2026-11-02");
        intent.setDateWindow(window);
        IntentDto.Party party = new IntentDto.Party();
        party.setAdults(2);
        intent.setParty(party);
        IntentDto.Budget budget = new IntentDto.Budget();
        budget.setAmount(new BigDecimal("3000"));
        budget.setCurrency("CNY");
        intent.setBudget(budget);
        intent.setPreferences(List.of("美食", "人文"));
        return intent;
    }
}
