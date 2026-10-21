package com.travel.modules.guidebook.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.guidebook.dto.ReferenceDay;
import com.travel.modules.guidebook.dto.ReferenceGuidebook;
import com.travel.modules.guidebook.dto.ReferenceNode;
import com.travel.support.TestFixtures;

class PromptAssemblyStepTest {

    private final PromptAssemblyStep step = new PromptAssemblyStep(new ObjectMapper());

    @Test
    void withoutReferencePromptIsUnchanged() {
        GenerationContext ctx = context();

        step.execute(ctx);

        String prompt = userPrompt(ctx);
        assertThat(prompt).doesNotContain("【参考攻略】");
        assertThat(prompt).contains("【旅行意图】", "【用户画像】", "【目的地资料】");
    }

    @Test
    void referenceSectionIncludesSkeletonAndGuardrails() {
        GenerationContext ctx = context();
        ReferenceGuidebook reference = new ReferenceGuidebook();
        reference.setTitle("别人的成都行");
        reference.setDays(List.of(new ReferenceDay(1, List.of(
                new ReferenceNode("09:00", "activity", "宽窄巷子"),
                new ReferenceNode(null, "meal", "用餐")))));
        reference.setBudgetSummary("500-1000 CNY");
        reference.setCulturalTips(List.of("茶馆慢坐"));
        reference.setPitfallTips(List.of("避开周末"));
        ctx.setReference(reference);

        step.execute(ctx);

        String prompt = userPrompt(ctx);
        assertThat(prompt).contains("【参考攻略】", "参考标题：别人的成都行",
                "[09:00 activity 宽窄巷子]", "[ meal 用餐]",
                "参考预算区间：500-1000 CNY", "参考文化提示：茶馆慢坐",
                "参考避坑提示：避开周末");
        assertThat(prompt).contains("不得照抄节点顺序与文案", "禁止在 source 中引用它",
                "参考攻略里的价格与时间一律视为过期");
        assertThat(prompt).doesNotContain("[K1]", "测试节点");
    }

    @Test
    void promptIsTruncatedAtLimit() {
        GenerationContext ctx = context();
        ReferenceGuidebook reference = new ReferenceGuidebook();
        String longName = "地点".repeat(200);
        List<ReferenceDay> days = new java.util.ArrayList<>();
        for (int day = 1; day <= 8; day++) {
            List<ReferenceNode> nodes = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                nodes.add(new ReferenceNode("09:00", "activity", longName));
            }
            days.add(new ReferenceDay(day, nodes));
        }
        reference.setDays(days);
        ctx.setReference(reference);

        step.execute(ctx);

        assertThat(userPrompt(ctx)).hasSize(12_000);
    }

    private GenerationContext context() {
        return new GenerationContext(5L, 7L, 9L, TestFixtures.intent(),
                Map.of("pace", "balanced"));
    }

    private String userPrompt(GenerationContext ctx) {
        return ctx.getPromptMessages().stream()
                .filter(message -> "user".equals(message.role()))
                .findFirst()
                .orElseThrow()
                .content();
    }
}
