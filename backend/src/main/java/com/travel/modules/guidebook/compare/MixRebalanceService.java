package com.travel.modules.guidebook.compare;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.AiClient;
import com.travel.ai.AiMessage;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.generation.GeneratedContent;
import com.travel.modules.guidebook.generation.ItineraryJsonParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FR-4 混搭重平衡：把用户挑选的节点交给模型补齐交通与时间。
 * 只调用一次；失败由调用方回退到原始 picks，不用模板攻略替代。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MixRebalanceService {

    private static final int PROMPT_MAX_CHARS = 24_000;
    private static final Set<String> NODE_TYPES =
            Set.of("activity", "meal", "transport", "stay", "free");
    private static final Pattern TIME_PATTERN =
            Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private static final String SYSTEM_PROMPT = """
            你是资深旅行规划师，正在把用户从多套方案中挑选的节点整理成一份完整攻略。
            只输出一个 JSON 对象，不要输出 markdown 代码块或解释文字。结构：
            {
              "title": "攻略标题",
              "days": [
                {
                  "day": 1,
                  "nodes": [
                    {
                      "nodeId": "nxxxxxxxx",
                      "timeSlot": {"start": "09:00", "end": "11:30"},
                      "type": "activity|meal|transport|stay|free",
                      "place": {"name": "地点名称", "lat": null, "lng": null},
                      "source": "选择依据",
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
            1. 用户选中的地点必须保留，不得替换或删除；
            2. 保留原节点所属的日期，按时间顺序排列；
            3. 补齐必要的交通与用餐，调整时间避免时段重叠；
            4. 重新计算预算，币种与用户预算一致，不要超出用户预算；
            5. type 只能取 activity/meal/transport/stay/free，时间格式 HH:mm；
            6. 不编造资料中不存在的门票价格或营业时间。
            """;

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    /**
     * @param intent 旅行意图
     * @param days 已选节点按天分组的结果
     * @return 模型给出的完整攻略
     */
    public GeneratedContent rebalance(IntentDto intent, List<GuidebookDayDto> days) {
        String userPrompt = buildPrompt(intent, days);
        String raw;
        try {
            raw = aiClient.chat(List.of(AiMessage.system(SYSTEM_PROMPT),
                    AiMessage.user(userPrompt)));
        } catch (RuntimeException e) {
            throw new IllegalStateException("混搭重平衡模型调用失败: " + e.getMessage(), e);
        }
        GeneratedContent content = ItineraryJsonParser.parse(objectMapper, raw);
        normalize(content);
        return content;
    }

    private String buildPrompt(IntentDto intent, List<GuidebookDayDto> days) {
        StringBuilder sb = new StringBuilder();
        sb.append("【旅行意图】\n").append(intentText(intent)).append('\n');
        sb.append("【已选节点】\n");
        try {
            sb.append(objectMapper.writeValueAsString(days)).append('\n');
        } catch (Exception e) {
            throw new IllegalStateException("已选节点序列化失败", e);
        }
        sb.append("\n请在保留这些地点的前提下输出完整攻略 JSON。");
        if (sb.length() > PROMPT_MAX_CHARS) {
            throw new IllegalStateException("所选内容过长，无法进行混搭重平衡");
        }
        return sb.toString();
    }

    private String intentText(IntentDto intent) {
        if (intent == null) {
            return "（意图缺失）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("目的地：").append(text(intent.getDestination(), "未知")).append('\n');
        if (intent.getDateWindow() != null) {
            sb.append("日期：").append(intent.getDateWindow().getStart())
                    .append(" 至 ").append(intent.getDateWindow().getEnd()).append('\n');
        }
        if (intent.getParty() != null && intent.getParty().getAdults() != null) {
            sb.append("成人：").append(intent.getParty().getAdults()).append('\n');
        }
        if (intent.getBudget() != null) {
            sb.append("预算：").append(intent.getBudget().getAmount())
                    .append(' ').append(intent.getBudget().getCurrency()).append('\n');
        }
        return sb.toString();
    }

    /** 只做结构修复：无法修复时抛异常，由调用方回退到原始 picks。 */
    private void normalize(GeneratedContent content) {
        if (content.getDays() == null || content.getDays().isEmpty()) {
            throw new IllegalStateException("AI 未返回有效行程");
        }
        Set<String> usedIds = new HashSet<>();
        int dayIndex = 0;
        for (GuidebookDayDto day : content.getDays()) {
            dayIndex++;
            if (day == null) {
                continue;
            }
            if (day.getDay() == null || day.getDay() < 1) {
                day.setDay(dayIndex);
            }
            if (day.getNodes() == null) {
                day.setNodes(new ArrayList<>());
            }
            day.getNodes().removeIf(Objects::isNull);
            for (GuidebookNodeDto node : day.getNodes()) {
                normalizeNode(node, usedIds);
            }
        }
        if (content.getTitle() == null || content.getTitle().isBlank()) {
            content.setTitle("混搭攻略");
        }
    }

    private void normalizeNode(GuidebookNodeDto node, Set<String> usedIds) {
        String id = node.getNodeId();
        if (!StringUtils.hasText(id) || !usedIds.add(id)) {
            id = newNodeId(usedIds);
            node.setNodeId(id);
            usedIds.add(id);
        }
        if (node.getType() == null || !NODE_TYPES.contains(node.getType())) {
            node.setType("activity");
        }
        if (!validTimeSlot(node.getTimeSlot())) {
            node.setTimeSlot(new TimeSlotDto("09:00", "10:00"));
        }
        if (node.getPlace() == null || !StringUtils.hasText(node.getPlace().getName())) {
            node.setPlace(new PlaceDto("待定地点", null, null));
        }
    }

    private boolean validTimeSlot(TimeSlotDto slot) {
        if (slot == null || !TIME_PATTERN.matcher(text(slot.getStart(), "")).matches()
                || !TIME_PATTERN.matcher(text(slot.getEnd(), "")).matches()) {
            return false;
        }
        return LocalTime.parse(slot.getEnd()).isAfter(LocalTime.parse(slot.getStart()));
    }

    private String newNodeId(Set<String> usedIds) {
        String id;
        do {
            id = "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (usedIds.contains(id));
        return id;
    }

    private String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
