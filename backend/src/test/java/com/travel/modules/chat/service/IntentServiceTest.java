package com.travel.modules.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.AiClient;
import com.travel.ai.AiMessage;
import com.travel.ai.AiProperties;
import com.travel.modules.chat.dto.IntentResult;

@ExtendWith(MockitoExtension.class)
class IntentServiceTest {

    private static final String FULL_TEXT = "我想从北京出发去成都，玩3天，2个人，预算1万，下个月出发";

    @Mock
    private AiClient aiClient;

    private ObjectMapper objectMapper;
    private IntentService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new IntentService(aiClient, new AiProperties(), objectMapper);
    }

    @Test
    void withoutApiKeyUsesRuleFallback() {
        IntentResult result = service.analyze(List.of(AiMessage.user(FULL_TEXT)));

        assertThat(result.getIntent().getDestination()).isEqualTo("成都");
        assertThat(result.getIntent().getOrigin()).isEqualTo("北京");
        assertThat(result.getIntent().getParty().getAdults()).isEqualTo(2);
        assertThat(result.getIntent().getBudget().getAmount())
                .isEqualByComparingTo(new BigDecimal("10000"));
        LocalDate start = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        assertThat(result.getIntent().getDateWindow().getStart()).isEqualTo(start.toString());
        assertThat(result.getIntent().getDateWindow().getEnd())
                .isEqualTo(start.plusDays(2).toString());
        assertThat(result.getMissingFields()).isEmpty();
        assertThat(result.getQuestions()).isEmpty();
        verify(aiClient, never()).chat(anyList());
    }

    @Test
    void fallbackParsesChinesePeopleCount() {
        IntentResult result = service.analyze(List.of(AiMessage.user("我们两个人去成都")));

        assertThat(result.getIntent().getParty().getAdults()).isEqualTo(2);
    }

    @Test
    void fallbackParsesOrigin() {
        IntentResult result = service.analyze(List.of(AiMessage.user("从上海出发，去成都，玩两天")));

        assertThat(result.getIntent().getOrigin()).isEqualTo("上海");
        assertThat(result.getIntent().getDestination()).isEqualTo("成都");
    }

    @Test
    void fallbackParsesTenPeople() {
        IntentResult result = service.analyze(List.of(AiMessage.user("十个人去成都")));

        assertThat(result.getIntent().getParty().getAdults()).isEqualTo(10);
    }

    @Test
    void fallbackParsesYuanBudget() {
        IntentResult result = service.analyze(List.of(AiMessage.user("去成都，3000元")));

        assertThat(result.getIntent().getBudget().getAmount())
                .isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(result.getIntent().getBudget().getCurrency()).isEqualTo("CNY");
    }

    @Test
    void fallbackGeneratesQuestionsForMissingFields() {
        IntentResult result = service.analyze(List.of(AiMessage.user("帮我规划一下")));

        assertThat(result.getMissingFields())
                .containsExactly("destination", "origin", "dateWindow", "adults", "budget");
        assertThat(result.getQuestions()).extracting("questionId")
                .containsExactly("q1", "q2", "q3", "q4", "q5");
        assertThat(result.getReply()).contains("请补充", "从哪个城市出发");
    }

    @Test
    void parsesFencedJsonFromModel() {
        service = configuredService();
        when(aiClient.chat(anyList())).thenReturn("```json\n"
                + "{\"reply\":\"好的\",\"intent\":{\"destination\":\"成都\","
                + "\"dateWindow\":{\"start\":\"2026-11-01\",\"end\":\"2026-11-02\"},"
                + "\"party\":{\"adults\":3},\"budget\":{\"amount\":5000,\"currency\":\"CNY\"},"
                + "\"preferences\":[\"美食\"]},\"missingFields\":[],\"questions\":[]}\n"
                + "```");

        IntentResult result = service.analyze(List.of(AiMessage.user("去成都")));

        assertThat(result.getReply()).isEqualTo("好的");
        assertThat(result.getIntent().getDestination()).isEqualTo("成都");
        assertThat(result.getIntent().getParty().getAdults()).isEqualTo(3);
        assertThat(result.getMissingFields()).isEmpty();
    }

    @Test
    void normalizesMissingListsFromModel() {
        service = configuredService();
        when(aiClient.chat(anyList())).thenReturn(
                "{\"reply\":\"好的\",\"intent\":{\"destination\":\"成都\"}}");

        IntentResult result = service.analyze(List.of(AiMessage.user("去成都")));

        assertThat(result.getMissingFields()).isNotNull().isEmpty();
        assertThat(result.getQuestions()).isNotNull().isEmpty();
    }

    @Test
    void invalidModelOutputFallsBackToRules() {
        service = configuredService();
        when(aiClient.chat(anyList())).thenReturn("这不是 JSON");

        // 规则文本里写的是「成都」，只要命中即说明走了降级分支。
        IntentResult result = service.analyze(List.of(AiMessage.user("我想去成都")));

        assertThat(result.getIntent().getDestination()).isEqualTo("成都");
    }

    @Test
    void modelFailureFallsBackToRules() {
        service = configuredService();
        when(aiClient.chat(anyList())).thenThrow(new RuntimeException("timeout"));

        IntentResult result = service.analyze(List.of(AiMessage.user("我想去成都")));

        assertThat(result.getIntent().getDestination()).isEqualTo("成都");
    }

    private IntentService configuredService() {
        AiProperties properties = new AiProperties();
        properties.setApiKey("test-key");
        return new IntentService(aiClient, properties, objectMapper);
    }
}
