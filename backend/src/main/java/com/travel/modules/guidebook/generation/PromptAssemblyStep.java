package com.travel.modules.guidebook.generation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.AiMessage;
import com.travel.modules.guidebook.knowledge.KnowledgeSnippet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Step 4：把意图、画像、知识条目与实时数据拼成结构化 prompt。 */
@Slf4j
@Order(4)
@Component
@RequiredArgsConstructor
public class PromptAssemblyStep implements GenerationStep {

    private static final int MAX_PROMPT_CHARS = 12_000;

    private static final String SYSTEM_PROMPT = """
            你是一名资深旅行规划师，请根据用户意图、目的地资料与实时数据生成可执行的旅行攻略。
            只输出一个 JSON 对象，不要输出 markdown 代码块或任何解释文字。JSON 结构：
            {
              "title": "攻略标题",
              "days": [
                {
                  "day": 1,
                  "nodes": [
                    {
                      "nodeId": "d1-1",
                      "timeSlot": {"start": "09:00", "end": "11:30"},
                      "type": "activity|meal|transport|stay|free",
                      "place": {"name": "地点名称", "lat": null, "lng": null},
                      "source": "选择依据，引用资料编号如 [K1]，无资料时写 通用知识",
                      "note": "简短提示"
                    }
                  ]
                }
              ],
              "budgetBreakdown": {
                "currency": "CNY",
                "items": [{"category": "交通", "min": 0, "max": 0}],
                "total": {"min": 0, "max": 0}
              },
              "packingList": ["物品"],
              "culturalTips": ["文化提示"],
              "pitfallTips": ["避坑提示"]
            }
            要求：
            1. 每天至少 4 个节点，时间不重叠，覆盖上午、午餐、下午、晚餐；
            2. type 只能取 activity/meal/transport/stay/free；
            3. 时间格式 HH:mm，金额为数字且币种与用户预算一致；
            4. 优先使用提供的资料，并在 source 中标注对应编号；
            5. 不要编造资料中不存在的门票价格或营业时间。
            """;

    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "prompt_assembly";
    }

    @Override
    public int progress() {
        return 30;
    }

    @Override
    public void execute(GenerationContext ctx) {
        StringBuilder sb = new StringBuilder();
        appendIntent(sb, ctx);
        sb.append("\n【用户画像】\n");
        sb.append(safeJson(ctx.getProfileSnapshot()));
        sb.append("\n【目的地资料】\n");
        if (ctx.getKnowledge().isEmpty()) {
            sb.append("（暂无匹配资料，请基于通用知识规划，并标注 通用知识）\n");
        } else {
            for (KnowledgeSnippet snippet : ctx.getKnowledge()) {
                sb.append(snippet.code()).append(" ").append(snippet.title()).append("：")
                        .append(snippet.content()).append('\n');
            }
        }
        sb.append("\n【实时数据】\n");
        appendTravelData(sb, ctx);
        sb.append("\n请按系统要求输出 JSON。");

        String userPrompt = sb.length() > MAX_PROMPT_CHARS
                ? sb.substring(0, MAX_PROMPT_CHARS)
                : sb.toString();
        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.system(SYSTEM_PROMPT));
        messages.add(AiMessage.user(userPrompt));
        ctx.setPromptMessages(messages);
    }

    private void appendIntent(StringBuilder sb, GenerationContext ctx) {
        var intent = ctx.getIntent();
        sb.append("【旅行意图】\n");
        sb.append("目的地：").append(intent.getDestination()).append('\n');
        if (intent.getDateWindow() != null) {
            sb.append("日期：").append(intent.getDateWindow().getStart())
                    .append(" 至 ").append(intent.getDateWindow().getEnd())
                    .append("，共 ").append(expectedDays(intent)).append(" 天\n");
        }
        if (intent.getParty() != null) {
            sb.append("人数：成人 ").append(intent.getParty().getAdults()).append('\n');
        }
        if (intent.getBudget() != null) {
            sb.append("预算：").append(intent.getBudget().getAmount())
                    .append(' ').append(intent.getBudget().getCurrency()).append('\n');
        }
        if (intent.getPreferences() != null && !intent.getPreferences().isEmpty()) {
            sb.append("偏好：").append(String.join("、", intent.getPreferences())).append('\n');
        }
    }

    private void appendTravelData(StringBuilder sb, GenerationContext ctx) {
        var snapshot = ctx.getTravelData();
        if (snapshot == null || (snapshot.weatherSummary() == null
                && snapshot.exchangeSummary() == null)) {
            sb.append("（实时数据暂不可用，请在提示中注明时效未知）\n");
            return;
        }
        if (snapshot.weatherSummary() != null) {
            sb.append(snapshot.weatherSummary()).append('\n');
        }
        if (snapshot.exchangeSummary() != null) {
            sb.append(snapshot.exchangeSummary()).append('\n');
        }
        if (snapshot.fetchedAt() != null) {
            sb.append("数据采集时间：").append(snapshot.fetchedAt()).append('\n');
        }
    }

    static int expectedDays(com.travel.modules.chat.dto.IntentDto intent) {
        try {
            LocalDate start = LocalDate.parse(intent.getDateWindow().getStart());
            LocalDate end = LocalDate.parse(intent.getDateWindow().getEnd());
            long days = ChronoUnit.DAYS.between(start, end) + 1;
            return (int) Math.max(1, Math.min(days, 10));
        } catch (Exception e) {
            return 3;
        }
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("画像快照序列化失败: {}", e.getMessage());
            return "{}";
        }
    }
}
