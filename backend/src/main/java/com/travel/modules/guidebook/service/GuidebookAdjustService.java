package com.travel.modules.guidebook.service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.AiClient;
import com.travel.ai.AiMessage;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.AdjustResponse;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.edit.ItineraryRepairSupport;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.generation.GeneratedContent;
import com.travel.modules.guidebook.generation.ItineraryJsonParser;
import com.travel.modules.guidebook.mapper.GuidebookMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FR-5 对话式调整：模型改写完整攻略，服务端只做修复、落库与冲突检测。
 * 模型调用在事务外；失败时不改动任何数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuidebookAdjustService {

    private static final int INSTRUCTION_MAX = 500;
    private static final int PROMPT_MAX_CHARS = 24_000;
    private static final Duration LOCK_TTL = Duration.ofSeconds(180);
    private static final String LOCK_PREFIX = "travel:edit:lock:";
    private static final Set<String> EDITABLE_STATUSES =
            Set.of(Guidebook.STATUS_DRAFT, Guidebook.STATUS_EDITING);
    private static final Set<String> NODE_TYPES =
            Set.of("activity", "meal", "transport", "stay", "free");
    private static final Pattern TIME_PATTERN =
            Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private static final String SYSTEM_PROMPT = """
            你是资深旅行规划师，正在修改一份已经生成的旅行攻略。
            只输出一个 JSON 对象，不要输出 markdown 代码块或解释文字。结构：
            {
              "changeSummary": "一句话说明改了什么，不超过 50 字",
              "changes": [
                {"type": "move_node", "day": 3, "nodeId": "n5", "into": [4, 0]}
              ],
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
            1. 输出完整攻略，不是差异；
            2. 未涉及的节点必须原样保留，nodeId 不得改变；
            3. 新增节点的 nodeId 使用 n 开头加 8 位随机字符；
            4. 时间格式 HH:mm，结束时间必须晚于开始时间；
            5. type 只能取 activity/meal/transport/stay/free；
            6. 只修改用户指令涉及的部分，不要重写无关内容；
            7. 金额与币种保持与当前攻略一致；
            8. 不要编造资料中不存在的门票价格或营业时间。
            """;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final GuidebookMapper guidebookMapper;
    private final GuidebookVersionService versionService;
    private final GuidebookConflictService conflictService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * §7.5 对话式调整。
     * 流程：校验 → 加锁 → 调模型 → 修复 → 短事务落库 → 释放锁。
     * @param guidebookId 攻略 ID
     * @param instruction 用户指令
     * @return 新版本、摘要、变更与冲突
     */
    public AdjustResponse adjust(Long guidebookId, String instruction) {
        if (!StringUtils.hasText(instruction)) {
            throw new BizException(ResultCode.PARAM_ERROR, "调整指令不能为空");
        }
        if (instruction.length() > INSTRUCTION_MAX) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "调整指令不能超过 " + INSTRUCTION_MAX + " 字");
        }
        Guidebook guidebook = requireEditable(guidebookId);

        String lockKey = LOCK_PREFIX + guidebookId;
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCode.TOO_MANY_REQUEST, "攻略正在调整中，请稍后再试");
        }
        try {
            GeneratedContent content = callModel(guidebook, instruction);
            normalize(content);
            String summary = summaryOf(content, instruction);
            return transactionTemplate.execute(status -> apply(guidebook, content, summary));
        } finally {
            releaseLock(lockKey, token);
        }
    }

    /**
     * 短事务内应用调整结果：写当前表 + 追加 adjust 快照 + 检测冲突。
     * @param guidebook 调整前读到的攻略（带旧版本号）
     * @param content 修复后的模型输出
     * @param summary 变更摘要
     * @return 响应
     */
    private AdjustResponse apply(Guidebook guidebook, GeneratedContent content, String summary) {
        guidebook.setStatus(Guidebook.STATUS_EDITING);
        guidebook.setTitle(text(content.getTitle(), guidebook.getTitle()));
        guidebook.setDays(content.getDays());
        if (content.getBudgetBreakdown() != null) {
            guidebook.setBudgetBreakdown(content.getBudgetBreakdown());
        }
        guidebook.setPackingList(orElse(content.getPackingList(), guidebook.getPackingList()));
        guidebook.setCulturalTips(orElse(content.getCulturalTips(), guidebook.getCulturalTips()));
        guidebook.setPitfallTips(orElse(content.getPitfallTips(), guidebook.getPitfallTips()));

        // 模型改完后同样要压实时间线、收束预算，避免调整后立刻冒出可自动修复的冲突
        ItineraryRepairSupport.repair(guidebook.getDays(), guidebook.getBudgetBreakdown(),
                guidebook.getIntent());

        if (guidebookMapper.updateById(guidebook) != 1) {
            throw new BizException(ResultCode.CONFLICT, "攻略已被修改，请刷新后重试");
        }
        versionService.append(guidebook, GuidebookVersion.CHANGE_TYPE_ADJUST,
                summary, UserContext.getUserId());

        List<NodeConflictDto> conflicts = conflictService.detect(guidebook);
        return new AdjustResponse(guidebook.getId(), guidebook.getVersion(), summary,
                content.getChanges() == null ? List.of() : content.getChanges(), conflicts);
    }

    /**
     * 调用模型并解析为攻略内容。
     * 只调用一次：调整失败立即报错，不做结构修复重试。
     * @param guidebook 当前攻略
     * @param instruction 用户指令
     * @return 解析后的完整攻略
     */
    private GeneratedContent callModel(Guidebook guidebook, String instruction) {
        String raw;
        try {
            raw = aiClient.chat(buildMessages(guidebook, instruction));
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("调整模型调用失败 guidebookId={}: {}", guidebook.getId(), e.getMessage());
            throw new BizException(ResultCode.DEPENDENCY_UNAVAILABLE, "AI 服务暂不可用");
        }
        try {
            return ItineraryJsonParser.parse(objectMapper, raw);
        } catch (IllegalArgumentException e) {
            log.warn("调整输出无法解析 guidebookId={}: {}", guidebook.getId(), e.getMessage());
            throw new BizException(ResultCode.UNPROCESSABLE,
                    "AI 返回内容无法解析，请换一种说法后重试");
        }
    }

    /**
     * 组装调整对话消息。
     * @param guidebook 当前攻略
     * @param instruction 用户指令
     * @return system + user 两条消息
     */
    private List<AiMessage> buildMessages(Guidebook guidebook, String instruction) {
        StringBuilder user = new StringBuilder();
        user.append("【旅行意图】\n").append(intentText(guidebook.getIntent())).append('\n');
        user.append("【当前攻略 JSON】\n").append(currentGuidebookJson(guidebook)).append('\n');
        user.append("【调整指令】\n").append(instruction).append('\n');
        user.append("\n请输出修改后的完整攻略 JSON。");
        if (user.length() > PROMPT_MAX_CHARS) {
            throw new BizException(ResultCode.UNPROCESSABLE, "攻略内容过长，暂不支持对话调整");
        }
        return List.of(AiMessage.system(SYSTEM_PROMPT), AiMessage.user(user.toString()));
    }

    /**
     * 把意图压成简短文本，避免整段 JSON 占比过大。
     * @param intent 旅行意图
     * @return 可读文本
     */
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

    /**
     * 只序列化攻略内容，不把 userId / status / version 等内部字段喂给模型。
     * @param guidebook 当前攻略
     * @return JSON 字符串
     */
    private String currentGuidebookJson(Guidebook guidebook) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", guidebook.getTitle());
        snapshot.put("days", guidebook.getDays());
        snapshot.put("budgetBreakdown", guidebook.getBudgetBreakdown());
        snapshot.put("packingList", guidebook.getPackingList());
        snapshot.put("culturalTips", guidebook.getCulturalTips());
        snapshot.put("pitfallTips", guidebook.getPitfallTips());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.FAIL, "攻略内容序列化失败");
        }
    }

    /**
     * 修复模型输出的基础结构，使其满足 DTO 与前端展示要求。
     * 只修复，不兜底：无法修复时直接报错，避免把无关模板写进用户攻略。
     * @param content 模型解析结果
     */
    private void normalize(GeneratedContent content) {
        if (content.getDays() == null || content.getDays().isEmpty()) {
            throw new BizException(ResultCode.UNPROCESSABLE, "AI 未能返回有效的行程内容");
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
    }

    /**
     * 修复单个节点的 ID、类型、时间与地点。
     * @param node 节点
     * @param usedIds 全攻略已占用的 nodeId
     */
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

    /**
     * 校验时间段是否可用。
     * @param slot 时间段
     * @return 起止均符合 HH:mm 且结束晚于开始时为 true
     */
    private boolean validTimeSlot(TimeSlotDto slot) {
        if (slot == null || !TIME_PATTERN.matcher(text(slot.getStart(), "")).matches()
                || !TIME_PATTERN.matcher(text(slot.getEnd(), "")).matches()) {
            return false;
        }
        return LocalTime.parse(slot.getEnd()).isAfter(LocalTime.parse(slot.getStart()));
    }

    /**
     * 生成全攻略唯一的节点 ID。
     * @param usedIds 已占用集合
     * @return 新 ID
     */
    private String newNodeId(Set<String> usedIds) {
        String id;
        do {
            id = "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (usedIds.contains(id));
        return id;
    }

    /**
     * 读取并校验攻略可调整。
     * @param guidebookId 攻略 ID
     * @return 攻略实体
     */
    private Guidebook requireEditable(Long guidebookId) {
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null) {
            throw new BizException(ResultCode.NOT_FOUND, "攻略不存在");
        }
        if (!guidebook.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该攻略");
        }
        if (!EDITABLE_STATUSES.contains(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "当前状态不可编辑");
        }
        return guidebook;
    }

    /**
     * 生成变更摘要：优先用模型给的一句话，缺失时用指令兜底。
     * @param content 模型输出
     * @param instruction 用户指令
     * @return 可读摘要
     */
    private String summaryOf(GeneratedContent content, String instruction) {
        if (StringUtils.hasText(content.getChangeSummary())) {
            return content.getChangeSummary().trim();
        }
        String brief = instruction.length() > 50 ? instruction.substring(0, 50) + "…" : instruction;
        return "AI 调整：" + brief;
    }

    /**
     * 释放编辑锁：只有 token 匹配才删除，避免误删他人新获取的锁。
     * @param key 锁 key
     * @param token 本次请求的标识
     */
    private void releaseLock(String key, String token) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (RuntimeException e) {
            log.warn("释放调整锁失败 key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 文本为空时返回兜底值。
     * @param value 原始值
     * @param fallback 兜底值
     * @return 非空文本
     */
    private String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 列表为 null 时返回兜底列表。
     * @param value 原始列表
     * @param fallback 兜底列表
     * @return 非空列表
     */
    private List<String> orElse(List<String> value, List<String> fallback) {
        return value == null ? fallback : value;
    }
}
