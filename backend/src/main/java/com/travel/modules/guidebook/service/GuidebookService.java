package com.travel.modules.guidebook.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.chat.entity.ChatSession;
import com.travel.modules.chat.mapper.ChatSessionMapper;
import com.travel.modules.guide.service.GuideReferenceBuilder;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.ExportResponse;
import com.travel.modules.guidebook.dto.GenerateGuidebookRequest;
import com.travel.modules.guidebook.dto.GenerateGuidebookResponse;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookDetailResponse;
import com.travel.modules.guidebook.dto.GuidebookListItem;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.ReferenceGuidebook;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.generation.GenerationContext;
import com.travel.modules.guidebook.generation.GuidebookGenerationRunner;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.profile.entity.UserProfile;
import com.travel.modules.profile.mapper.UserProfileMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuidebookService {

    private static final int ESTIMATED_SECONDS = 30;
    private static final int MAX_ATTEMPTS = 3;
    private static final String LOCK_PREFIX = "travel:gen:lock:";
    public static final String DISCLAIMER =
            "攻略由 AI 辅助生成，请出行前核实开放时间、票价与天气。";
    private static final List<String> ACTIVE_STATUSES = List.of(
            Guidebook.STATUS_GENERATING, Guidebook.STATUS_DRAFT,
            Guidebook.STATUS_CONFIRMED, Guidebook.STATUS_EXECUTING,
            Guidebook.STATUS_COMPLETED, Guidebook.STATUS_EDITING);

    private final GuidebookMapper guidebookMapper;
    private final ChatSessionMapper sessionMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final GuidebookGenerationRunner runner;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final ResourceService resourceService;
    private final GuideReferenceBuilder guideReferenceBuilder;

    /** §5.1 触发攻略生成；同会话幂等，每用户同一时间只允许一个生成任务。 */
    public GenerateGuidebookResponse generate(GenerateGuidebookRequest req) {
        Long userId = UserContext.getUserId();
        ChatSession session = null;
        IntentDto intent;

        if (req.getSessionId() != null) {
            session = requireOwnedSession(req.getSessionId(), userId);
            Guidebook existing = findActiveBySession(session.getId());
            if (existing != null) {
                return new GenerateGuidebookResponse(existing.getId(), existing.getStatus(), 0);
            }
            if (!ChatSession.STATUS_READY.equals(session.getStatus())) {
                throw new BizException(ResultCode.UNPROCESSABLE, "当前会话尚未就绪，无法生成攻略");
            }
            intent = session.getIntent();
        } else {
            intent = req.getIntent();
        }
        requireCompleteIntent(intent);

        String lockKey = LOCK_PREFIX + userId;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(180));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCode.TOO_MANY_REQUEST, "已有生成任务进行中，请稍后再试");
        }

        try {
            if (!runner.hasCapacity()) {
                throw new BizException(ResultCode.TOO_MANY_REQUEST, "生成任务繁忙，请稍后再试");
            }
            Map<String, Object> snapshot = req.getUseProfile() == null || req.getUseProfile()
                    ? loadProfileSnapshot(userId)
                    : Map.of();
            Long sessionId = session == null ? null : session.getId();
            Guidebook guidebook = transactionTemplate.execute(status -> {
                Guidebook created = createGuidebook(userId, sessionId, intent, snapshot);
                if (sessionId != null) {
                    markSessionGenerating(sessionId, created.getId());
                }
                return created;
            });
            GenerationContext ctx = new GenerationContext(guidebook.getId(), userId, sessionId,
                    intent, snapshot);
            // 从推荐攻略进入的会话：把推荐内容作为参考骨架带给规划步骤
            if (session != null && session.getReferencePostId() != null) {
                ctx.setReference(guideReferenceBuilder.toReference(session.getReferencePostId()));
            }
            try {
                runner.submit(ctx);
            } catch (RuntimeException e) {
                log.warn("生成任务提交失败 guidebookId={}: {}", guidebook.getId(), e.getMessage());
                markSubmitFailed(guidebook.getId(), sessionId);
                throw new BizException(ResultCode.TOO_MANY_REQUEST, "生成任务繁忙，请稍后再试");
            }
            return new GenerateGuidebookResponse(guidebook.getId(), Guidebook.STATUS_GENERATING,
                    ESTIMATED_SECONDS);
        } catch (RuntimeException e) {
            redisTemplate.delete(lockKey);
            throw e;
        }
    }

    /** FR-12：基于分享再生成；调用方已完成登录校验，userId 显式传入。 */
    public GenerateGuidebookResponse generateWithReference(Long userId, Long sessionId,
            ReferenceGuidebook reference, Long sourceShareId) {
        ChatSession session = requireOwnedSession(sessionId, userId);
        if (findActiveBySession(sessionId) != null) {
            throw new BizException(ResultCode.CONFLICT, "该会话已有攻略，请先处理");
        }
        if (!ChatSession.STATUS_READY.equals(session.getStatus())) {
            throw new BizException(ResultCode.UNPROCESSABLE, "当前会话尚未就绪，无法生成攻略");
        }
        IntentDto intent = session.getIntent();
        requireCompleteIntent(intent);

        String lockKey = LOCK_PREFIX + userId;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(180));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCode.TOO_MANY_REQUEST, "已有生成任务进行中，请稍后再试");
        }

        try {
            if (!runner.hasCapacity()) {
                throw new BizException(ResultCode.TOO_MANY_REQUEST, "生成任务繁忙，请稍后再试");
            }
            Map<String, Object> snapshot = loadProfileSnapshot(userId);
            Guidebook guidebook = transactionTemplate.execute(status -> {
                Guidebook created = createGuidebook(userId, sessionId, intent, snapshot,
                        sourceShareId);
                markSessionGenerating(sessionId, created.getId());
                return created;
            });
            GenerationContext ctx = new GenerationContext(guidebook.getId(), userId, sessionId,
                    intent, snapshot);
            ctx.setReference(reference);
            try {
                runner.submit(ctx);
            } catch (RuntimeException e) {
                log.warn("再生成任务提交失败 guidebookId={}: {}", guidebook.getId(), e.getMessage());
                markSubmitFailed(guidebook.getId(), sessionId);
                throw new BizException(ResultCode.TOO_MANY_REQUEST, "生成任务繁忙，请稍后再试");
            }
            return new GenerateGuidebookResponse(guidebook.getId(), Guidebook.STATUS_GENERATING,
                    ESTIMATED_SECONDS);
        } catch (RuntimeException e) {
            redisTemplate.delete(lockKey);
            throw e;
        }
    }

    /** 会话确认联动：由 ChatService.confirm 调用。 */
    public GenerateGuidebookResponse generateFromSession(Long sessionId) {
        GenerateGuidebookRequest req = new GenerateGuidebookRequest();
        req.setSessionId(sessionId);
        req.setUseProfile(true);
        return generate(req);
    }

    /** §5.2 攻略详情；生成中也返回部分字段与进度。 */
    public GuidebookDetailResponse detail(Long guidebookId) {
        Guidebook guidebook = requireOwned(guidebookId);
        GuidebookDetailResponse resp = new GuidebookDetailResponse();
        resp.setGuidebookId(guidebook.getId());
        resp.setStatus(guidebook.getStatus());
        resp.setVersion(guidebook.getVersion());
        resp.setTitle(guidebook.getTitle());
        resp.setIntent(guidebook.getIntent());
        resourceService.enrichNodes(guidebook.getDays());
        sortDaysByTime(guidebook.getDays());
        resp.setDays(guidebook.getDays());
        resp.setBudgetBreakdown(guidebook.getBudgetBreakdown());
        resp.setPackingList(guidebook.getPackingList());
        resp.setCulturalTips(guidebook.getCulturalTips());
        resp.setPitfallTips(guidebook.getPitfallTips());
        resp.setTravelData(guidebook.getTravelData());
        resp.setTravelDataAt(guidebook.getTravelDataAt());
        resp.setDisclaimer(DISCLAIMER);
        resp.setDegradedMode(guidebook.getDegradedMode());
        resp.setDegradationReasons(guidebook.getDegradationReasons());
        resp.setCurrentStep(guidebook.getCurrentStep());
        resp.setProgress(guidebook.getProgress());
        resp.setErrorMessage(guidebook.getErrorMessage());
        return resp;
    }

    /** 展示前按开始时间排序，历史数据里的节点顺序也能呈现为连续时间线。 */
    private void sortDaysByTime(List<GuidebookDayDto> days) {
        if (days == null) {
            return;
        }
        for (GuidebookDayDto day : days) {
            if (day == null || day.getNodes() == null || day.getNodes().size() < 2) {
                continue;
            }
            List<GuidebookNodeDto> sorted = new ArrayList<>(day.getNodes());
            sorted.sort(Comparator.comparing(GuidebookService::nodeStart,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            day.setNodes(sorted);
        }
    }

    private static LocalTime nodeStart(GuidebookNodeDto node) {
        if (node == null || node.getTimeSlot() == null || node.getTimeSlot().getStart() == null) {
            return null;
        }
        try {
            return LocalTime.parse(node.getTimeSlot().getStart(), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** §5.3 失败重试；复用原行，最多 3 次。 */
    public GenerateGuidebookResponse retry(Long guidebookId) {
        Long userId = UserContext.getUserId();
        Guidebook guidebook = requireOwned(guidebookId);
        if (!Guidebook.STATUS_FAILED.equals(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "仅失败的攻略可以重试");
        }
        int attempts = guidebook.getAttemptCount() == null ? 1 : guidebook.getAttemptCount();
        if (attempts >= MAX_ATTEMPTS) {
            throw new BizException(ResultCode.UNPROCESSABLE, "重试次数已达上限");
        }
        String lockKey = LOCK_PREFIX + userId;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(180));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCode.TOO_MANY_REQUEST, "已有生成任务进行中，请稍后再试");
        }

        try {
            if (!runner.hasCapacity()) {
                throw new BizException(ResultCode.TOO_MANY_REQUEST, "生成任务繁忙，请稍后再试");
            }
            transactionTemplate.executeWithoutResult(status -> {
                guidebookMapper.update(null, new LambdaUpdateWrapper<Guidebook>()
                        .eq(Guidebook::getId, guidebookId)
                        .set(Guidebook::getStatus, Guidebook.STATUS_GENERATING)
                        .set(Guidebook::getProgress, 0)
                        .set(Guidebook::getCurrentStep, "queued")
                        .set(Guidebook::getErrorMessage, null)
                        .set(Guidebook::getFinishedAt, null)
                        .set(Guidebook::getDegradedMode, false)
                        .set(Guidebook::getDegradationReasons, null)
                        .set(Guidebook::getStartedAt, LocalDateTime.now())
                        .set(Guidebook::getAttemptCount, attempts + 1));
                if (guidebook.getSessionId() != null) {
                    markSessionGenerating(guidebook.getSessionId(), guidebookId);
                }
            });
            GenerationContext ctx = new GenerationContext(guidebookId, userId,
                    guidebook.getSessionId(), guidebook.getIntent(),
                    guidebook.getProfileSnapshot() == null ? Map.of() : guidebook.getProfileSnapshot());
            try {
                runner.submit(ctx);
            } catch (RuntimeException e) {
                markSubmitFailed(guidebookId, guidebook.getSessionId());
                throw new BizException(ResultCode.TOO_MANY_REQUEST, "生成任务繁忙，请稍后再试");
            }
            return new GenerateGuidebookResponse(guidebookId, Guidebook.STATUS_GENERATING,
                    ESTIMATED_SECONDS);
        } catch (RuntimeException e) {
            redisTemplate.delete(lockKey);
            throw e;
        }
    }

    /** §5.4 我的攻略分页。 */
    public PageResult<GuidebookListItem> list(String status, long page, long pageSize) {
        LambdaQueryWrapper<Guidebook> query = new LambdaQueryWrapper<Guidebook>()
                .eq(Guidebook::getUserId, UserContext.getUserId())
                .orderByDesc(Guidebook::getId);
        if (StringUtils.hasText(status)) {
            query.eq(Guidebook::getStatus, status);
        }
        var p = guidebookMapper.selectPage(new Page<>(page, pageSize), query);
        List<GuidebookListItem> items = p.getRecords().stream()
                .map(g -> new GuidebookListItem(g.getId(), g.getTitle(), g.getStatus(),
                        g.getProgress(), g.getVersion(), g.getUpdateTime()))
                .toList();
        return PageResult.of(items, p.getCurrent(), p.getSize(), p.getTotal());
    }

    /** §5.5 删除；执行中的攻略不允许删除。 */
    public void delete(Long guidebookId) {
        Guidebook guidebook = requireOwned(guidebookId);
        if (Guidebook.STATUS_CONFIRMED.equals(guidebook.getStatus())
                || Guidebook.STATUS_EXECUTING.equals(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "攻略已确认或执行中，无法删除");
        }
        guidebookMapper.deleteById(guidebookId);
    }

    /** §5.6 导出 markdown/html。 */
    public ExportResponse export(Long guidebookId, String format) {
        Guidebook guidebook = requireOwned(guidebookId);
        String fmt = StringUtils.hasText(format) ? format.toLowerCase() : "markdown";
        if ("pdf".equals(fmt)) {
            throw new BizException(ResultCode.UNPROCESSABLE, "暂不支持 PDF 导出");
        }
        if (!"markdown".equals(fmt) && !"html".equals(fmt)) {
            throw new BizException(ResultCode.PARAM_ERROR, "format 仅支持 markdown / html");
        }
        if (guidebook.getDays() == null || guidebook.getDays().isEmpty()) {
            throw new BizException(ResultCode.CONFLICT, "攻略尚未生成完成，暂不能导出");
        }
        String content = "html".equals(fmt) ? toHtml(guidebook) : toMarkdown(guidebook);
        String filename = "guidebook-" + guidebookId + ("html".equals(fmt) ? ".html" : ".md");
        return new ExportResponse(fmt, filename, content);
    }

    // ---- 内部方法 ----

    /** 每次普通生成扣一次计数；对比生成一次扣一次（由调用方只调用一次）。 */
    public void consumeQuotaOnce(Long userId) {
        consumeQuota(userId, 1);
    }

    /** 批量计数：仅累计当日生成次数，暂不限制上限。调用方负责事务边界。 */
    public void consumeQuota(Long userId, int count) {
        User user = userMapper.selectForUpdate(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在或已禁用");
        }
        LocalDate today = LocalDate.now();
        int used = user.getDailyGenerateUsed() == null ? 0 : user.getDailyGenerateUsed();
        if (!today.equals(user.getGenerateQuotaDate())) {
            used = 0;
        }
        user.setDailyGenerateUsed(used + count);
        user.setGenerateQuotaDate(today);
        userMapper.updateById(user);
    }

    /** FR-13：达人代客户创建生成中攻略，不扣配额（由批量服务统一预扣）。 */
    public Guidebook createExpertGuidebook(Long expertId, Long clientId, IntentDto intent,
            Map<String, Object> profileSnapshot) {
        Guidebook guidebook = new Guidebook();
        guidebook.setUserId(expertId);
        guidebook.setSessionId(null);
        guidebook.setExpertClientId(clientId);
        guidebook.setStatus(Guidebook.STATUS_GENERATING);
        guidebook.setVersion(1);
        guidebook.setIntent(intent);
        guidebook.setProfileSnapshot(profileSnapshot);
        guidebook.setDegradedMode(false);
        guidebook.setCurrentStep("queued");
        guidebook.setProgress(0);
        guidebook.setAttemptCount(1);
        guidebook.setStartedAt(LocalDateTime.now());
        guidebookMapper.insert(guidebook);
        return guidebook;
    }

    /** FR-4：为对比方案创建一条独立的生成中攻略，不重复扣配额。 */
    public Guidebook createComparisonGuidebook(Long userId, Guidebook base, String variant,
            Long comparisonId) {
        Guidebook guidebook = new Guidebook();
        guidebook.setUserId(userId);
        guidebook.setSessionId(null);
        guidebook.setStatus(Guidebook.STATUS_GENERATING);
        guidebook.setVersion(1);
        guidebook.setIntent(base.getIntent());
        guidebook.setProfileSnapshot(base.getProfileSnapshot());
        guidebook.setComparisonId(comparisonId);
        guidebook.setVariant(variant);
        guidebook.setDegradedMode(false);
        guidebook.setCurrentStep("queued");
        guidebook.setProgress(0);
        guidebook.setAttemptCount(1);
        guidebook.setStartedAt(LocalDateTime.now());
        guidebookMapper.insert(guidebook);
        return guidebook;
    }

    private Guidebook createGuidebook(Long userId, Long sessionId, IntentDto intent,
            Map<String, Object> snapshot) {
        return createGuidebook(userId, sessionId, intent, snapshot, null);
    }

    private Guidebook createGuidebook(Long userId, Long sessionId, IntentDto intent,
            Map<String, Object> snapshot, Long sourceShareId) {
        consumeQuotaOnce(userId);

        Guidebook guidebook = new Guidebook();
        guidebook.setUserId(userId);
        guidebook.setSessionId(sessionId);
        guidebook.setSourceShareId(sourceShareId);
        guidebook.setStatus(Guidebook.STATUS_GENERATING);
        guidebook.setVersion(1);
        guidebook.setIntent(intent);
        guidebook.setProfileSnapshot(snapshot);
        guidebook.setDegradedMode(false);
        guidebook.setCurrentStep("queued");
        guidebook.setProgress(0);
        guidebook.setAttemptCount(1);
        guidebook.setStartedAt(LocalDateTime.now());
        guidebookMapper.insert(guidebook);
        return guidebook;
    }

    private void markSessionGenerating(Long sessionId, Long guidebookId) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setStatus(ChatSession.STATUS_GENERATING);
        session.setGuidebookId(guidebookId);
        sessionMapper.updateById(session);
    }

    private void markSubmitFailed(Long guidebookId, Long sessionId) {
        Guidebook update = new Guidebook();
        update.setId(guidebookId);
        update.setStatus(Guidebook.STATUS_FAILED);
        update.setCurrentStep("failed");
        update.setErrorMessage("生成任务繁忙，请稍后重试");
        update.setFinishedAt(LocalDateTime.now());
        guidebookMapper.updateById(update);
        if (sessionId != null) {
            ChatSession session = new ChatSession();
            session.setId(sessionId);
            session.setStatus(ChatSession.STATUS_FAILED);
            sessionMapper.updateById(session);
        }
    }

    private Guidebook findActiveBySession(Long sessionId) {
        return guidebookMapper.selectOne(new LambdaQueryWrapper<Guidebook>()
                .eq(Guidebook::getSessionId, sessionId)
                .in(Guidebook::getStatus, ACTIVE_STATUSES)
                .orderByDesc(Guidebook::getId)
                .last("LIMIT 1"));
    }

    /** FR-13 批量生成在创建攻略前统一校验，全部通过才开始。 */
    public void requireCompleteIntent(IntentDto intent) {
        if (intent == null) {
            throw new BizException(ResultCode.UNPROCESSABLE, "缺少旅行意图");
        }
        if (!StringUtils.hasText(intent.getDestination())) {
            throw new BizException(ResultCode.UNPROCESSABLE, "目的地缺失，无法生成攻略");
        }
        if (intent.getDateWindow() == null
                || !StringUtils.hasText(intent.getDateWindow().getStart())
                || !StringUtils.hasText(intent.getDateWindow().getEnd())) {
            throw new BizException(ResultCode.UNPROCESSABLE, "出行日期缺失，无法生成攻略");
        }
        if (intent.getParty() == null || intent.getParty().getAdults() == null
                || intent.getParty().getAdults() < 1) {
            throw new BizException(ResultCode.UNPROCESSABLE, "出行人数缺失，无法生成攻略");
        }
        if (intent.getBudget() == null || intent.getBudget().getAmount() == null) {
            throw new BizException(ResultCode.UNPROCESSABLE, "预算缺失，无法生成攻略");
        }
    }

    public Map<String, Object> loadProfileSnapshot(Long userId) {
        UserProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId)
                .last("LIMIT 1"));
        if (profile == null) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(profile,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            log.warn("画像快照转换失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private ChatSession requireOwnedSession(Long sessionId, Long userId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该会话");
        }
        return session;
    }

    public Guidebook requireOwned(Long guidebookId) {
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null) {
            throw new BizException(ResultCode.NOT_FOUND, "攻略不存在");
        }
        if (!guidebook.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该攻略");
        }
        return guidebook;
    }

    private static final Map<String, String> NODE_TYPE_LABELS = Map.of(
            "activity", "游玩", "meal", "餐饮", "transport", "交通",
            "stay", "住宿", "free", "自由活动");

    private String nodeTypeLabel(String type) {
        return type == null ? "安排" : NODE_TYPE_LABELS.getOrDefault(type, type);
    }

    /** Markdown 导出：按天分节，每个节点独立成块，避免所有字段挤在一行。 */
    private String toMarkdown(Guidebook guidebook) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(text(guidebook.getTitle(), "旅行攻略")).append("\n\n");
        appendMarkdownMeta(sb, guidebook);
        appendMarkdownOverview(sb, guidebook.getDays());

        if (guidebook.getDays() != null) {
            for (GuidebookDayDto day : guidebook.getDays()) {
                sb.append("## 第 ").append(day.getDay()).append(" 天\n\n");
                if (day.getNodes() == null || day.getNodes().isEmpty()) {
                    sb.append("_本日暂无安排_\n\n");
                    continue;
                }
                for (GuidebookNodeDto node : day.getNodes()) {
                    sb.append("### ");
                    if (node.getTimeSlot() != null
                            && StringUtils.hasText(node.getTimeSlot().getStart())) {
                        sb.append(node.getTimeSlot().getStart());
                        if (StringUtils.hasText(node.getTimeSlot().getEnd())) {
                            sb.append(" - ").append(node.getTimeSlot().getEnd());
                        }
                        sb.append(" · ");
                    }
                    sb.append(node.getPlace() != null && StringUtils.hasText(node.getPlace().getName())
                            ? node.getPlace().getName() : "待定地点");
                    sb.append("\n\n");
                    sb.append("- 类型：").append(nodeTypeLabel(node.getType())).append('\n');
                    if (StringUtils.hasText(node.getNote())) {
                        sb.append("- 提示：").append(node.getNote().strip()).append('\n');
                    }
                    String source = traceableSource(node.getSource());
                    if (source != null) {
                        sb.append("- 参考：").append(source).append('\n');
                    }
                    sb.append('\n');
                }
            }
        }

        BudgetBreakdownDto budget = guidebook.getBudgetBreakdown();
        if (budget != null && budget.getItems() != null && !budget.getItems().isEmpty()) {
            String currency = StringUtils.hasText(budget.getCurrency()) ? budget.getCurrency() : "CNY";
            sb.append("## 预算拆解\n\n");
            sb.append("| 类别 | 最低 | 最高 |\n|---|---:|---:|\n");
            for (BudgetBreakdownDto.Item item : budget.getItems()) {
                sb.append("| ").append(item.getCategory() == null ? "其他" : item.getCategory())
                        .append(" | ").append(money(item.getMin())).append(" | ")
                        .append(money(item.getMax())).append(" |\n");
            }
            if (budget.getTotal() != null) {
                sb.append("| **合计** | **").append(money(budget.getTotal().getMin()))
                        .append("** | **").append(money(budget.getTotal().getMax()))
                        .append("** |\n");
            }
            sb.append("\n金额单位：").append(currency).append("\n\n");
        }
        appendTips(sb, "## 行前准备", guidebook.getPackingList());
        appendTips(sb, "## 文化贴士", guidebook.getCulturalTips());
        appendTips(sb, "## 避坑提示", guidebook.getPitfallTips());
        sb.append("---\n\n> ").append(DISCLAIMER).append('\n');
        return sb.toString();
    }

    private void appendMarkdownMeta(StringBuilder sb, Guidebook guidebook) {
        var i = guidebook.getIntent();
        if (i == null) {
            return;
        }
        StringBuilder meta = new StringBuilder();
        if (StringUtils.hasText(i.getDestination())) {
            meta.append("目的地：").append(i.getDestination());
        }
        if (i.getDateWindow() != null) {
            if (meta.length() > 0) meta.append(" ｜ ");
            meta.append("日期：").append(i.getDateWindow().getStart())
                    .append(" ~ ").append(i.getDateWindow().getEnd());
        }
        if (i.getBudget() != null && i.getBudget().getAmount() != null) {
            if (meta.length() > 0) meta.append(" ｜ ");
            meta.append("预算：").append(money(i.getBudget().getAmount()))
                    .append(' ').append(i.getBudget().getCurrency());
        }
        if (meta.length() > 0) {
            sb.append("> ").append(meta).append("\n\n");
        }
    }

    /** 行程总览表格，方便快速扫一眼每天去了哪里。 */
    private void appendMarkdownOverview(StringBuilder sb, List<GuidebookDayDto> days) {
        if (days == null || days.isEmpty()) {
            return;
        }
        sb.append("## 行程总览\n\n| 天数 | 主要安排 |\n|---|---|\n");
        for (GuidebookDayDto day : days) {
            String places = day.getNodes() == null ? "" : day.getNodes().stream()
                    .filter(Objects::nonNull)
                    .map(n -> n.getPlace() != null && StringUtils.hasText(n.getPlace().getName())
                            ? n.getPlace().getName() : "待定地点")
                    .collect(Collectors.joining(" → "));
            sb.append("| 第 ").append(day.getDay()).append(" 天 | ")
                    .append(places.isBlank() ? "自由活动" : places).append(" |\n");
        }
        sb.append('\n');
    }

    /** HTML 导出：内嵌样式、无外部依赖，手机和打印都能直接看。 */
    private String toHtml(Guidebook guidebook) {
        StringBuilder sb = new StringBuilder();
        String title = escape(text(guidebook.getTitle(), "旅行攻略"));
        sb.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(title).append("</title>")
                .append("<style>").append(EXPORT_CSS).append("</style></head><body>")
                .append("<main class=\"page\">")
                .append("<header class=\"hero\"><h1>").append(title).append("</h1>");
        appendHtmlMeta(sb, guidebook);
        sb.append("</header>");

        if (guidebook.getDays() != null) {
            for (GuidebookDayDto day : guidebook.getDays()) {
                sb.append("<section class=\"day\"><h2>第 ").append(day.getDay())
                        .append(" 天</h2><ol class=\"timeline\">");
                if (day.getNodes() == null || day.getNodes().isEmpty()) {
                    sb.append("<li class=\"empty\">本日暂无安排</li>");
                } else {
                    for (GuidebookNodeDto node : day.getNodes()) {
                        sb.append("<li class=\"node\"><div class=\"time\">")
                                .append(node.getTimeSlot() != null
                                        ? escape(node.getTimeSlot().getStart()) + "<br>"
                                            + escape(node.getTimeSlot().getEnd())
                                        : "待定")
                                .append("</div><div class=\"body\"><div class=\"node-head\">")
                                .append("<strong>")
                                .append(escape(node.getPlace() != null
                                        ? node.getPlace().getName() : "待定地点"))
                                .append("</strong><span class=\"badge\">")
                                .append(escape(nodeTypeLabel(node.getType())))
                                .append("</span></div>");
                        if (StringUtils.hasText(node.getNote())) {
                            sb.append("<p class=\"note\">").append(escape(node.getNote().strip()))
                                    .append("</p>");
                        }
                        if (node.getOpeningHours() != null
                                && !node.getOpeningHours().isBlank()) {
                            sb.append("<p class=\"meta-line\">营业时间：")
                                    .append(escape(node.getOpeningHours())).append("</p>");
                        }
                        String source = traceableSource(node.getSource());
                        if (source != null) {
                            sb.append("<p class=\"meta-line\">参考：")
                                    .append(escape(source)).append("</p>");
                        }
                        sb.append("</div></li>");
                    }
                }
                sb.append("</ol></section>");
            }
        }

        BudgetBreakdownDto budget = guidebook.getBudgetBreakdown();
        if (budget != null && budget.getItems() != null && !budget.getItems().isEmpty()) {
            String currency = StringUtils.hasText(budget.getCurrency()) ? budget.getCurrency() : "CNY";
            sb.append("<section class=\"card\"><h2>预算拆解</h2><table><thead><tr>")
                    .append("<th>类别</th><th class=\"num\">最低</th><th class=\"num\">最高</th>")
                    .append("</tr></thead><tbody>");
            for (BudgetBreakdownDto.Item item : budget.getItems()) {
                sb.append("<tr><td>").append(escape(item.getCategory() == null ? "其他" : item.getCategory()))
                        .append("</td><td class=\"num\">").append(money(item.getMin()))
                        .append("</td><td class=\"num\">").append(money(item.getMax()))
                        .append("</td></tr>");
            }
            if (budget.getTotal() != null) {
                sb.append("<tr class=\"total\"><td>合计</td><td class=\"num\">")
                        .append(money(budget.getTotal().getMin()))
                        .append("</td><td class=\"num\">")
                        .append(money(budget.getTotal().getMax()))
                        .append("</td></tr>");
            }
            sb.append("</tbody></table><p class=\"meta-line\">金额单位：")
                    .append(escape(currency)).append("</p></section>");
        }

        appendHtmlTips(sb, "行前准备", guidebook.getPackingList());
        appendHtmlTips(sb, "文化贴士", guidebook.getCulturalTips());
        appendHtmlTips(sb, "避坑提示", guidebook.getPitfallTips());
        sb.append("<footer class=\"disclaimer\">").append(escape(DISCLAIMER))
                .append("</footer></main></body></html>");
        return sb.toString();
    }

    private void appendHtmlMeta(StringBuilder sb, Guidebook guidebook) {
        var i = guidebook.getIntent();
        if (i == null) {
            return;
        }
        sb.append("<div class=\"chips\">");
        if (StringUtils.hasText(i.getDestination())) {
            sb.append("<span class=\"chip\">📍 ").append(escape(i.getDestination()))
                    .append("</span>");
        }
        if (i.getDateWindow() != null) {
            sb.append("<span class=\"chip\">🗓 ")
                    .append(escape(i.getDateWindow().getStart())).append(" ~ ")
                    .append(escape(i.getDateWindow().getEnd())).append("</span>");
        }
        if (i.getBudget() != null && i.getBudget().getAmount() != null) {
            sb.append("<span class=\"chip\">💰 ").append(money(i.getBudget().getAmount()))
                    .append(' ').append(escape(i.getBudget().getCurrency())).append("</span>");
        }
        sb.append("</div>");
    }

    private String money(java.math.BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private void appendTips(StringBuilder sb, String heading, List<String> tips) {
        if (tips == null || tips.isEmpty()) {
            return;
        }
        sb.append(heading).append("\n\n");
        tips.forEach(tip -> sb.append("- ").append(tip).append('\n'));
        sb.append('\n');
    }

    private void appendHtmlTips(StringBuilder sb, String title, List<String> tips) {
        if (tips == null || tips.isEmpty()) {
            return;
        }
        sb.append("<section class=\"card\"><h2>").append(escape(title))
                .append("</h2><ul class=\"tips\">");
        tips.forEach(tip -> sb.append("<li>").append(escape(tip)).append("</li>"));
        sb.append("</ul></section>");
    }

    private static final String EXPORT_CSS = """
            :root{--brand:#2f6fed;--text:#1f2430;--muted:#6b7280;--line:#e6e8ee;--bg:#f5f6f8;}
            *{box-sizing:border-box;}
            body{margin:0;background:var(--bg);color:var(--text);
              font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif;
              line-height:1.7;}
            .page{max-width:860px;margin:0 auto;padding:28px 20px 56px;}
            .hero{background:linear-gradient(135deg,#2f6fed,#5b8dff);color:#fff;
              border-radius:18px;padding:28px 26px;box-shadow:0 10px 28px rgba(47,111,237,.22);}
            .hero h1{margin:0 0 12px;font-size:26px;line-height:1.35;letter-spacing:.2px;}
            .chips{display:flex;flex-wrap:wrap;gap:8px;}
            .chip{background:rgba(255,255,255,.18);border:1px solid rgba(255,255,255,.32);
              border-radius:999px;padding:3px 12px;font-size:13px;}
            .day,.card{background:#fff;border-radius:16px;padding:22px 24px;margin-top:18px;
              box-shadow:0 2px 10px rgba(16,24,40,.06);}
            h2{font-size:18px;margin:0 0 16px;}
            .timeline{list-style:none;margin:0;padding:0;position:relative;}
            .timeline:before{content:"";position:absolute;left:63px;top:6px;bottom:6px;
              width:2px;background:var(--line);}
            .node{display:flex;gap:16px;padding:12px 0;position:relative;}
            .node+.node{border-top:1px dashed var(--line);}
            .time{width:54px;flex:none;text-align:right;font-size:12px;color:var(--muted);
              padding-top:2px;line-height:1.5;}
            .body{flex:1;min-width:0;}
            .node-head{display:flex;align-items:center;gap:8px;flex-wrap:wrap;}
            .node-head strong{font-size:15px;}
            .badge{background:#eef4ff;color:var(--brand);border-radius:999px;
              padding:1px 9px;font-size:12px;}
            .note{margin:6px 0 0;color:#374151;font-size:14px;}
            .meta-line{margin:4px 0 0;color:var(--muted);font-size:12px;}
            .empty{color:var(--muted);font-size:14px;padding:8px 0;}
            table{width:100%;border-collapse:collapse;font-size:14px;}
            th,td{padding:9px 10px;border-bottom:1px solid var(--line);text-align:left;}
            th{background:#f8fafc;color:var(--muted);font-weight:600;}
            .num{text-align:right;font-variant-numeric:tabular-nums;}
            .total td{font-weight:700;border-top:2px solid var(--line);}
            .tips{margin:0;padding-left:20px;font-size:14px;}
            .tips li{margin:4px 0;}
            .disclaimer{color:var(--muted);font-size:12px;margin-top:22px;text-align:center;}
            @media (max-width:600px){
              .page{padding:16px 12px 40px;}
              .hero{padding:22px 18px;border-radius:14px;}
              .hero h1{font-size:21px;}
              .day,.card{padding:18px 16px;}
              .timeline:before{left:50px;}
              .time{width:44px;}
            }
            @media print{
              body{background:#fff;}
              .page{max-width:none;padding:0;}
              .hero{box-shadow:none;-webkit-print-color-adjust:exact;print-color-adjust:exact;}
              .day,.card{box-shadow:none;border:1px solid var(--line);page-break-inside:avoid;}
            }
            """;

    private String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /** 仅保留真实引用标注，通用知识兜底文案不进入导出内容。 */
    private String traceableSource(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        String value = source.strip();
        if ("通用知识".equals(value) || value.contains("知识库无相关资料")) {
            return null;
        }
        return value;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
