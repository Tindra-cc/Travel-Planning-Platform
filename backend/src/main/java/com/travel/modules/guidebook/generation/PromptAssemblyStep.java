package com.travel.modules.guidebook.generation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.AiMessage;
import com.travel.modules.guidebook.data.DataBlock;
import com.travel.modules.guidebook.data.TravelDataSnapshot;
import com.travel.modules.guidebook.dto.ReferenceDay;
import com.travel.modules.guidebook.dto.ReferenceGuidebook;
import com.travel.modules.guidebook.dto.ReferenceNode;
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
                      "source": "选择依据，引用资料编号如 [K1]；未使用资料时留空字符串",
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
            1. 每天至少 4 个节点，覆盖上午、午餐、下午、晚餐；同一天内节点时间严禁重叠，后一节点的 start 必须晚于前一节点 end；
            2. type 只能取 activity/meal/transport/stay/free；
            3. 时间格式 HH:mm，金额为数字且币种与用户预算一致；
            4. 优先使用提供的资料，并在 source 中以 [K1] 形式标注依据；
            5. 资料为空或未使用资料时，source 留空，不要在正文或字段里提及资料库、检索结果或信息来源不足；
            6. 不要编造资料中不存在的门票价格或营业时间；
            7. 实时数据只使用标记为可用的源；不可用时直接略过，不要写「时效未知」或任何数据缺失说明，禁止编造价格、天气或营业时间；
            8. budgetBreakdown 的 total.max 不得超过用户预算金额：预算不足时压缩住宿与交通档次，而不是让总额超限。
            """;

    private static final Map<String, String> SOURCE_LABELS = Map.of(
            "weather", "天气", "exchange", "汇率", "local_news", "当地动态",
            "visa", "签证", "flight_price", "机票价格", "hotel_price", "酒店价格");

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
        appendVariant(sb, ctx);
        sb.append("\n【用户画像】\n");
        sb.append(safeJson(ctx.getProfileSnapshot()));
        appendReference(sb, ctx);
        sb.append("\n【目的地资料】\n");
        if (ctx.getKnowledge().isEmpty()) {
            sb.append("（暂无匹配资料，请直接基于常识规划，不要在文案中提及资料库或信息来源）\n");
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

    private void appendVariant(StringBuilder sb, GenerationContext ctx) {
        String variant = ctx.getVariant();
        if (variant == null) {
            return;
        }
        sb.append("\n【方案风格】\n");
        switch (variant) {
            case "budget" -> sb.append("省钱优先：公共交通、青旅或经济型酒店、免费或低价景点，"
                    + "餐饮以本地小吃为主，总预算贴近下限。\n");
            case "balanced" -> sb.append("均衡方案：交通与住宿兼顾舒适与价格，"
                    + "保留必去景点与一两次特色体验。\n");
            case "luxury" -> sb.append("品质优先：专车或包车、高品质住宿、付费深度体验，"
                    + "减少排队与转场，预算上限更宽松。\n");
            case "mixed" -> sb.append("混搭方案：保留用户指定的地点，补齐必要交通，"
                    + "调整时间避免冲突，并重算预算。\n");
            default -> {
                return;
            }
        }
    }

    private void appendReference(StringBuilder sb, GenerationContext ctx) {
        ReferenceGuidebook ref = ctx.getReference();
        if (ref == null) {
            return;
        }
        sb.append("\n【参考攻略】\n");
        sb.append("以下内容来自其他用户分享，只用于参考行程结构、节奏与地点组合。\n");
        sb.append("必须遵守：\n");
        sb.append("1. 按本次用户的意图、预算与画像重新规划，不得照抄节点顺序与文案；\n");
        sb.append("2. 不合适的参考地点直接舍弃，不要为了保留而硬塞；\n");
        sb.append("3. 参考攻略中的任何来源标注都不是你的依据，禁止在 source 中引用它；\n");
        sb.append("4. 实时数据必须使用本次提供的数据，参考攻略里的价格与时间一律视为过期。\n");
        if (ref.getRawText() != null) {
            sb.append(ref.getRawText()).append('\n');
        }
        if (ref.getTitle() != null) {
            sb.append("参考标题：").append(ref.getTitle()).append('\n');
        }
        if (ref.getDays() != null) {
            for (ReferenceDay day : ref.getDays()) {
                sb.append("第").append(day.getDay()).append("天：");
                if (day.getNodes() == null || day.getNodes().isEmpty()) {
                    sb.append("（无节点）");
                } else {
                    for (ReferenceNode node : day.getNodes()) {
                        sb.append('[').append(nullToEmpty(node.getStart())).append(' ')
                                .append(nullToEmpty(node.getType())).append(' ')
                                .append(nullToEmpty(node.getPlaceName())).append("] ");
                    }
                }
                sb.append('\n');
            }
        }
        if (ref.getBudgetSummary() != null) {
            sb.append("参考预算区间：").append(ref.getBudgetSummary()).append('\n');
        }
        if (ref.getCulturalTips() != null && !ref.getCulturalTips().isEmpty()) {
            sb.append("参考文化提示：").append(String.join("；", ref.getCulturalTips())).append('\n');
        }
        if (ref.getPitfallTips() != null && !ref.getPitfallTips().isEmpty()) {
            sb.append("参考避坑提示：").append(String.join("；", ref.getPitfallTips())).append('\n');
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void appendTravelData(StringBuilder sb, GenerationContext ctx) {
        TravelDataSnapshot snapshot = ctx.getTravelData();
        if (snapshot == null || snapshot.blocks().isEmpty()) {
            sb.append("（实时数据暂不可用，请在提示中注明时效未知）\n");
            return;
        }
        for (DataBlock block : snapshot.blocks()) {
            sb.append("· ").append(SOURCE_LABELS.getOrDefault(block.source(), block.source()))
                    .append("：");
            if (block.available()) {
                sb.append(block.summary() == null ? "已采集" : block.summary());
                if (block.fetchedAt() != null) {
                    sb.append("（采集于 ").append(block.fetchedAt()).append("）");
                }
            } else {
                sb.append("不可用（时效未知，不要编造具体价格或营业时间）");
            }
            sb.append('\n');
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
