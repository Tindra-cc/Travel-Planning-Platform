package com.travel.modules.guidebook.generation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.AiClient;
import com.travel.ai.AiMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Step 5：调用模型生成攻略；解析失败修复一次，仍失败或模型不可用时使用模板。 */
@Slf4j
@Order(5)
@Component
@RequiredArgsConstructor
public class ItineraryComposeStep implements GenerationStep {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final GuidebookTemplateComposer templateComposer;

    @Override
    public String name() {
        return "itinerary_compose";
    }

    @Override
    public int progress() {
        return 60;
    }

    @Override
    public void execute(GenerationContext ctx) {
        String raw = null;
        try {
            raw = aiClient.chat(ctx.getPromptMessages());
            apply(ctx, ItineraryJsonParser.parse(objectMapper, raw));
            return;
        } catch (Exception firstError) {
            log.warn("AI 攻略生成失败，尝试修复: {}", firstError.getMessage());
        }

        try {
            String repaired = aiClient.chat(repairMessages(ctx, raw));
            apply(ctx, ItineraryJsonParser.parse(objectMapper, repaired));
            ctx.addReason("json_repaired");
            return;
        } catch (Exception repairError) {
            log.warn("JSON 修复失败，使用模板: {}", repairError.getMessage());
        }

        ctx.addReason("model_fallback");
        apply(ctx, templateComposer.compose(ctx));
    }

    private void apply(GenerationContext ctx, GeneratedContent content) {
        ctx.setTitle(content.getTitle());
        ctx.setDays(content.getDays());
        ctx.setBudgetBreakdown(content.getBudgetBreakdown());
        ctx.setPackingList(nullSafe(content.getPackingList()));
        ctx.setCulturalTips(nullSafe(content.getCulturalTips()));
        ctx.setPitfallTips(nullSafe(content.getPitfallTips()));
    }

    private List<AiMessage> repairMessages(GenerationContext ctx, String raw) {
        List<AiMessage> messages = new ArrayList<>(ctx.getPromptMessages());
        if (raw != null && !raw.isBlank()) {
            messages.add(AiMessage.assistant(raw));
        }
        messages.add(AiMessage.user("上一条输出无法解析为 JSON。请只输出合法 JSON 对象，"
                + "结构保持不变，不要输出代码块或解释。"));
        return messages;
    }

    private List<String> nullSafe(List<String> value) {
        return value == null ? List.of() : value;
    }
}
