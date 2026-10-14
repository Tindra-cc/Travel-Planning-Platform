package com.travel.modules.chat.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.AiClient;
import com.travel.ai.AiMessage;
import com.travel.ai.AiProperties;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.chat.dto.IntentResult;
import com.travel.modules.chat.dto.PendingQuestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 旅行意图分析：优先调用大模型输出结构化 JSON，无 key 或调用失败时降级为规则澄清桩。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentService {

    private static final String SYSTEM_PROMPT = """
            你是一个旅行规划助手，负责从用户的对话中提取结构化的旅行意图，并在信息不足时提出澄清问题。
            你必须只输出一个 JSON 对象，不要包含任何解释或 Markdown 代码块，格式如下：
            {
              "reply": "面向用户的中文自然语言回复，用于复述已理解的信息并追问缺失信息",
              "intent": {
                "destination": "目的地，未知则为 null",
                "dateWindow": { "start": "YYYY-MM-DD 或 null", "end": "YYYY-MM-DD 或 null" },
                "party": { "adults": 成人数或 null },
                "budget": { "amount": 数字或 null, "currency": "CNY" },
                "preferences": ["偏好关键词"]
              },
              "missingFields": ["仍缺失的字段名，如 destination/dateWindow/adults/budget"],
              "questions": [ { "questionId": "q1", "text": "澄清问题" } ]
            }
            当 destination、dateWindow、party.adults、budget.amount 全部具备时，missingFields 与 questions 返回空数组。
            """;

    private static final Map<String, String> QUESTION_TEMPLATES = new LinkedHashMap<>();
    static {
        QUESTION_TEMPLATES.put("destination", "想去哪个目的地？");
        QUESTION_TEMPLATES.put("dateWindow", "计划什么时候出发、玩几天？");
        QUESTION_TEMPLATES.put("adults", "一共几个人出行？");
        QUESTION_TEMPLATES.put("budget", "大概的预算范围是多少？");
    }

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern PEOPLE = Pattern.compile("([0-9一二两三四五六七八九十]+)\\s*(?:人|位|个)");
    private static final Pattern WAN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*万");
    private static final Pattern YUAN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块)");
    private static final Pattern DEST = Pattern.compile("(?:去|到|前往)\\s*([\\u4e00-\\u9fa5]{2,10}?)(?=[，,。.；;！!、\\s]|$)");
    private static final Pattern DAYS = Pattern.compile("(\\d+)\\s*天");
    private static final String NEXT_MONTH = "下个月";

    private final AiClient aiClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    /** 根据完整对话历史分析当前意图。history 为按时间正序的消息列表。 */
    public IntentResult analyze(List<AiMessage> history) {
        if (!aiProperties.configured()) {
            return fallback(history);
        }
        try {
            List<AiMessage> messages = new ArrayList<>();
            messages.add(AiMessage.system(SYSTEM_PROMPT));
            messages.addAll(history);
            String raw = aiClient.chat(messages);
            IntentResult parsed = parse(raw);
            if (parsed != null && parsed.getIntent() != null) {
                normalize(parsed);
                return parsed;
            }
            log.warn("AI 意图解析结果为空或不合法，降级为规则分析");
            return fallback(history);
        } catch (Exception e) {
            log.warn("AI 意图分析失败，降级为规则分析: {}", e.getMessage());
            return fallback(history);
        }
    }

    private IntentResult parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String json = stripFence(raw.trim());
        try {
            return objectMapper.readValue(json, IntentResult.class);
        } catch (Exception e) {
            log.warn("无法将模型输出解析为 IntentResult: {}", json);
            return null;
        }
    }

    private String stripFence(String text) {
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLine > 0 && lastFence > firstLine) {
                return text.substring(firstLine + 1, lastFence).trim();
            }
        }
        return text;
    }

    private void normalize(IntentResult result) {
        if (result.getMissingFields() == null) {
            result.setMissingFields(new ArrayList<>());
        }
        if (result.getQuestions() == null) {
            result.setQuestions(new ArrayList<>());
        }
    }

    /**
     * 规则降级：扫描全部用户消息抽取字段（跨轮累加），其余作为澄清问题。
     * 目标是无 AI key 也能走通到 ready_to_generate。
     */
    private IntentResult fallback(List<AiMessage> history) {
        String text = allUserContent(history);
        IntentDto intent = new IntentDto();

        Matcher dest = DEST.matcher(text);
        if (dest.find()) {
            intent.setDestination(dest.group(1));
        }
        Integer adults = extractPeople(text);
        if (adults != null) {
            intent.setParty(new IntentDto.Party());
            intent.getParty().setAdults(adults);
        }
        extractBudget(text, intent);
        extractDateWindow(text, intent);

        List<String> missing = new ArrayList<>();
        List<PendingQuestion> questions = new ArrayList<>();
        if (!StringUtils.hasText(intent.getDestination())) {
            missing.add("destination");
        }
        if (intent.getDateWindow() == null) {
            missing.add("dateWindow");
        }
        if (intent.getParty() == null || intent.getParty().getAdults() == null) {
            missing.add("adults");
        }
        if (intent.getBudget() == null || intent.getBudget().getAmount() == null) {
            missing.add("budget");
        }
        int idx = 1;
        for (String field : missing) {
            String q = QUESTION_TEMPLATES.get(field);
            if (q != null) {
                questions.add(new PendingQuestion("q" + idx++, q));
            }
        }

        IntentResult result = new IntentResult();
        result.setIntent(intent);
        result.setMissingFields(missing);
        result.setQuestions(questions);
        result.setReply(questions.isEmpty()
                ? "信息已经足够，确认生成攻略吗？"
                : "为了给你更精准的行程，请补充以下信息：" + questions.stream()
                        .map(PendingQuestion::getText).reduce((a, b) -> a + "；" + b).orElse(""));
        return result;
    }

    private void extractBudget(String text, IntentDto intent) {
        Matcher wan = WAN.matcher(text);
        Matcher yuan = YUAN.matcher(text);
        if (wan.find()) {
            intent.setBudget(new IntentDto.Budget());
            intent.getBudget().setAmount(new BigDecimal(wan.group(1)).multiply(new BigDecimal(10000)));
            intent.getBudget().setCurrency("CNY");
        } else if (yuan.find()) {
            intent.setBudget(new IntentDto.Budget());
            intent.getBudget().setAmount(new BigDecimal(yuan.group(1)));
            intent.getBudget().setCurrency("CNY");
        }
    }

    /** 仅支持「下个月」+ 「N 天」这一种常见组合，够跑通降级链路即可。 */
    private void extractDateWindow(String text, IntentDto intent) {
        if (!text.contains(NEXT_MONTH)) {
            return;
        }
        Matcher days = DAYS.matcher(text);
        if (!days.find()) {
            return;
        }
        LocalDate start = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        LocalDate end = start.plusDays(Integer.parseInt(days.group(1)) - 1L);
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart(start.format(ISO));
        window.setEnd(end.format(ISO));
        intent.setDateWindow(window);
    }

    private Integer extractPeople(String text) {
        Matcher m = PEOPLE.matcher(text);
        if (m.find()) {
            return parseCount(m.group(1));
        }
        return null;
    }

    private Integer parseCount(String raw) {
        if (raw.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(raw);
        }
        int ten = raw.indexOf('十');
        if (ten >= 0) {
            String before = raw.substring(0, ten);
            String after = raw.substring(ten + 1);
            int tens = before.isEmpty() ? 1 : chineseDigit(before);
            int ones = after.isEmpty() ? 0 : chineseDigit(after);
            return tens * 10 + ones;
        }
        return chineseDigit(raw);
    }

    private int chineseDigit(String s) {
        return switch (s) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> 0;
        };
    }

    private String allUserContent(List<AiMessage> history) {
        StringBuilder sb = new StringBuilder();
        for (AiMessage m : history) {
            if ("user".equals(m.role()) && StringUtils.hasText(m.content())) {
                sb.append(m.content()).append('，');
            }
        }
        return sb.toString();
    }
}
