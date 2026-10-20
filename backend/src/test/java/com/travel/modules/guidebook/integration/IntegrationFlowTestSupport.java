package com.travel.modules.guidebook.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookComparison;
import com.travel.modules.guidebook.mapper.GuidebookComparisonMapper;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.GuidebookVersionMapper;
import com.travel.modules.guidebook.mapper.OfflinePackMapper;
import com.travel.modules.guidebook.service.GuidebookVersionService;

/**
 * FR-4/6/7/8/9 集成测试基类：真实 PostgreSQL + Redis，外部 HTTP 源全部指向不可达地址，
 * 保证测试不依赖公网且能在断网环境跑通。
 */
@Tag("integration")
@SpringBootTest(properties = {
        "ai.deepseek.api-key=",
        "ai.dashscope.api-key=",
        "app.travel-data.gdelt-url=http://127.0.0.1:1/gdelt",
        "app.travel-data.nominatim-url=http://127.0.0.1:1/nominatim",
        "app.travel-data.overpass-url=http://127.0.0.1:1/overpass",
        "app.travel-data.flight-price-url=",
        "app.travel-data.hotel-price-url=",
        "app.travel-data.timeout-seconds=1",
        "app.offline-pack.storage-dir=target/test-offline-packs",
        "app.offline-pack.map-url-template=",
        "app.link.signing-secret=integration-test-secret"
})
public abstract class IntegrationFlowTestSupport {

    @Autowired
    protected UserMapper userMapper;
    @Autowired
    protected GuidebookMapper guidebookMapper;
    @Autowired
    protected GuidebookVersionMapper versionMapper;
    @Autowired
    protected OfflinePackMapper offlinePackMapper;
    @Autowired
    protected GuidebookComparisonMapper comparisonMapper;
    @Autowired
    protected GuidebookVersionService versionService;

    protected Long userId;

    @BeforeEach
    void setUpUser() {
        User user = new User();
        user.setAccount("it-" + UUID.randomUUID() + "@test.local");
        user.setAccountType("email");
        user.setPasswordHash("x");
        user.setNickname("integration");
        user.setStatus(1);
        user.setDailyGenerateQuota(3);
        user.setDailyGenerateUsed(0);
        user.setGenerateQuotaDate(LocalDate.now());
        user.setProfileCompleted(false);
        userMapper.insert(user);
        userId = user.getId();
        UserContext.set(new LoginUser(userId, user.getAccount()));
    }

    @AfterEach
    void tearDownUser() {
        if (userId == null) {
            UserContext.clear();
            return;
        }
        List<Long> guidebookIds = guidebookMapper
                .selectList(Wrappers.<Guidebook>lambdaQuery().eq(Guidebook::getUserId, userId))
                .stream()
                .map(Guidebook::getId)
                .toList();
        if (!guidebookIds.isEmpty()) {
            versionMapper.delete(Wrappers.<com.travel.modules.guidebook.entity.GuidebookVersion>
                    lambdaQuery().in(com.travel.modules.guidebook.entity.GuidebookVersion::getGuidebookId,
                            guidebookIds));
        }
        offlinePackMapper.delete(Wrappers.<com.travel.modules.guidebook.entity.OfflinePack>
                lambdaQuery().eq(com.travel.modules.guidebook.entity.OfflinePack::getUserId, userId));
        comparisonMapper.delete(Wrappers.<GuidebookComparison>lambdaQuery()
                .eq(GuidebookComparison::getUserId, userId));
        guidebookMapper.delete(Wrappers.<Guidebook>lambdaQuery().eq(Guidebook::getUserId, userId));
        userMapper.deleteById(userId);
        UserContext.clear();
    }

    protected IntentDto intent(String destination, String start, String end) {
        IntentDto intent = new IntentDto();
        intent.setDestination(destination);
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart(start);
        window.setEnd(end);
        intent.setDateWindow(window);
        IntentDto.Party party = new IntentDto.Party();
        party.setAdults(2);
        intent.setParty(party);
        IntentDto.Budget budget = new IntentDto.Budget();
        budget.setAmount(new BigDecimal("5000"));
        budget.setCurrency("CNY");
        intent.setBudget(budget);
        intent.setPreferences(List.of("美食"));
        return intent;
    }

    protected Guidebook createGuidebook(String status, boolean withDays) {
        Guidebook guidebook = new Guidebook();
        guidebook.setUserId(userId);
        guidebook.setSessionId(null);
        guidebook.setStatus(status);
        guidebook.setVersion(1);
        guidebook.setTitle("集成测试攻略");
        guidebook.setIntent(intent("成都", "2026-11-01", "2026-11-02"));
        if (withDays) {
            guidebook.setDays(List.of(new GuidebookDayDto(1, List.of(new GuidebookNodeDto(
                    "n-it-1",
                    new TimeSlotDto("09:00", "11:00"),
                    "activity",
                    new PlaceDto("宽窄巷子", 30.6696, 104.0550),
                    null,
                    "集成测试",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "集成测试节点")))));
        }
        guidebook.setDegradedMode(false);
        guidebook.setCurrentStep("completed");
        guidebook.setProgress(100);
        guidebook.setAttemptCount(1);
        guidebook.setStartedAt(LocalDateTime.now());
        guidebook.setFinishedAt(LocalDateTime.now());
        guidebookMapper.insert(guidebook);
        return guidebook;
    }
}
