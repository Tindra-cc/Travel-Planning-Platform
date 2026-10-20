package com.travel.modules.guidebook.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.ExportResponse;
import com.travel.modules.guidebook.dto.GenerateGuidebookRequest;
import com.travel.modules.guidebook.dto.GenerateGuidebookResponse;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookDetailResponse;
import com.travel.modules.guidebook.dto.GuidebookListItem;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
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
    private static final String DISCLAIMER =
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

    /** 每次普通生成扣一次配额；对比生成一次扣一次（由调用方只调用一次）。 */
    public void consumeQuotaOnce(Long userId) {
        User user = userMapper.selectForUpdate(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在或已禁用");
        }
        LocalDate today = LocalDate.now();
        int used = user.getDailyGenerateUsed() == null ? 0 : user.getDailyGenerateUsed();
        if (!today.equals(user.getGenerateQuotaDate())) {
            used = 0;
        }
        int quota = user.getDailyGenerateQuota() == null ? 3 : user.getDailyGenerateQuota();
        if (used >= quota) {
            throw new BizException(ResultCode.TOO_MANY_REQUEST,
                    "今日生成次数已用完（每日 " + quota + " 次）");
        }
        user.setDailyGenerateUsed(used + 1);
        user.setGenerateQuotaDate(today);
        userMapper.updateById(user);
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
        consumeQuotaOnce(userId);

        Guidebook guidebook = new Guidebook();
        guidebook.setUserId(userId);
        guidebook.setSessionId(sessionId);
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

    private void requireCompleteIntent(IntentDto intent) {
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

    private String toMarkdown(Guidebook guidebook) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(text(guidebook.getTitle(), "旅行攻略")).append("\n\n");
        if (guidebook.getIntent() != null) {
            var i = guidebook.getIntent();
            sb.append("- 目的地：").append(i.getDestination()).append('\n');
            if (i.getDateWindow() != null) {
                sb.append("- 日期：").append(i.getDateWindow().getStart())
                        .append(" ~ ").append(i.getDateWindow().getEnd()).append('\n');
            }
            if (i.getBudget() != null) {
                sb.append("- 预算：").append(i.getBudget().getAmount())
                        .append(' ').append(i.getBudget().getCurrency()).append('\n');
            }
            sb.append('\n');
        }
        for (GuidebookDayDto day : guidebook.getDays()) {
            sb.append("## 第 ").append(day.getDay()).append(" 天\n\n");
            if (day.getNodes() == null) {
                continue;
            }
            for (GuidebookNodeDto node : day.getNodes()) {
                sb.append("- ");
                if (node.getTimeSlot() != null) {
                    sb.append(node.getTimeSlot().getStart()).append('-')
                            .append(node.getTimeSlot().getEnd()).append(' ');
                }
                if (node.getPlace() != null) {
                    sb.append(node.getPlace().getName()).append(' ');
                }
                sb.append('（').append(node.getType()).append("）");
                if (StringUtils.hasText(node.getNote())) {
                    sb.append("：").append(node.getNote());
                }
                if (StringUtils.hasText(node.getSource())) {
                    sb.append(" [").append(node.getSource()).append(']');
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
        BudgetBreakdownDto budget = guidebook.getBudgetBreakdown();
        if (budget != null && budget.getItems() != null) {
            sb.append("## 预算拆解\n\n| 类别 | 最低 | 最高 |\n|---|---|---|\n");
            for (BudgetBreakdownDto.Item item : budget.getItems()) {
                sb.append("| ").append(item.getCategory()).append(" | ")
                        .append(item.getMin()).append(" | ").append(item.getMax()).append(" |\n");
            }
            if (budget.getTotal() != null) {
                sb.append("| 合计 | ").append(budget.getTotal().getMin()).append(" | ")
                        .append(budget.getTotal().getMax()).append(" |\n");
            }
            sb.append('\n');
        }
        appendTips(sb, "行前准备", guidebook.getPackingList());
        appendTips(sb, "文化贴士", guidebook.getCulturalTips());
        appendTips(sb, "避坑提示", guidebook.getPitfallTips());
        sb.append("> ").append(DISCLAIMER).append('\n');
        return sb.toString();
    }

    private String toHtml(Guidebook guidebook) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><title>")
                .append(escape(text(guidebook.getTitle(), "旅行攻略")))
                .append("</title></head><body>");
        sb.append("<h1>").append(escape(text(guidebook.getTitle(), "旅行攻略"))).append("</h1>");
        for (GuidebookDayDto day : guidebook.getDays()) {
            sb.append("<h2>第 ").append(day.getDay()).append(" 天</h2><ul>");
            if (day.getNodes() != null) {
                for (GuidebookNodeDto node : day.getNodes()) {
                    sb.append("<li>");
                    if (node.getTimeSlot() != null) {
                        sb.append(escape(node.getTimeSlot().getStart())).append('-')
                                .append(escape(node.getTimeSlot().getEnd())).append(' ');
                    }
                    if (node.getPlace() != null) {
                        sb.append("<strong>").append(escape(node.getPlace().getName()))
                                .append("</strong> ");
                    }
                    if (StringUtils.hasText(node.getNote())) {
                        sb.append(escape(node.getNote()));
                    }
                    sb.append("</li>");
                }
            }
            sb.append("</ul>");
        }
        appendHtmlTips(sb, "行前准备", guidebook.getPackingList());
        appendHtmlTips(sb, "文化贴士", guidebook.getCulturalTips());
        appendHtmlTips(sb, "避坑提示", guidebook.getPitfallTips());
        sb.append("<blockquote>").append(escape(DISCLAIMER)).append("</blockquote></body></html>");
        return sb.toString();
    }

    private void appendTips(StringBuilder sb, String title, List<String> tips) {
        if (tips == null || tips.isEmpty()) {
            return;
        }
        sb.append("## ").append(title).append("\n\n");
        tips.forEach(tip -> sb.append("- ").append(tip).append('\n'));
        sb.append('\n');
    }

    private void appendHtmlTips(StringBuilder sb, String title, List<String> tips) {
        if (tips == null || tips.isEmpty()) {
            return;
        }
        sb.append("<h2>").append(escape(title)).append("</h2><ul>");
        tips.forEach(tip -> sb.append("<li>").append(escape(tip)).append("</li>"));
        sb.append("</ul>");
    }

    private String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
