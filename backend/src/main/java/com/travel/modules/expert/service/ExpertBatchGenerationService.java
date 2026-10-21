package com.travel.modules.expert.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.expert.dto.BatchGenerateRequest;
import com.travel.modules.expert.dto.BatchGenerateResponse;
import com.travel.modules.expert.dto.BatchTaskResponse;
import com.travel.modules.expert.entity.ExpertClient;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.generation.GenerationContext;
import com.travel.modules.guidebook.generation.GuidebookGenerationRunner;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.service.GuidebookService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 达人批量生成：全有或全无的准入、一次预扣 N 次配额、单任务提交失败隔离。
 * 画像来自客户本人（linked_user_id），攻略归属达人账号并标记 expert_client_id。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpertBatchGenerationService {

    private static final String BATCH_LOCK_PREFIX = "travel:expert:batch:lock:";
    private static final Duration BATCH_LOCK_TTL = Duration.ofSeconds(300);
    private static final int MAX_BATCH = 5;

    private final ClientAuthorizationService authorizationService;
    private final ClientAccessAuditService auditService;
    private final GuidebookService guidebookService;
    private final GuidebookGenerationRunner runner;
    private final GuidebookMapper guidebookMapper;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;

    public BatchGenerateResponse generate(Long clientId, BatchGenerateRequest req, String ip) {
        Long expertId = UserContext.getUserId();
        ExpertClient client = authorizationService.requireAuthorized(clientId,
                ClientAuthorizationService.SCOPE_PROFILE);
        authorizationService.requireAuthorized(clientId,
                ClientAuthorizationService.SCOPE_GUIDEBOOKS);

        List<IntentDto> intents = req.getIntents();
        if (intents == null || intents.isEmpty() || intents.size() > MAX_BATCH) {
            throw new BizException(ResultCode.PARAM_ERROR, "一次最多生成 5 本攻略");
        }
        for (IntentDto intent : intents) {
            guidebookService.requireCompleteIntent(intent);
        }

        String lockKey = BATCH_LOCK_PREFIX + expertId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", BATCH_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCode.TOO_MANY_REQUEST, "已有批量生成任务进行中");
        }

        try {
            if (!runner.hasCapacity()) {
                throw new BizException(ResultCode.TOO_MANY_REQUEST, "生成任务繁忙，请稍后再试");
            }
            Long clientUserId = client.getLinkedUserId();
            Map<String, Object> profileSnapshot = guidebookService.loadProfileSnapshot(clientUserId);
            auditService.record(expertId, clientId, ClientAccessAuditService.ACTION_READ_PROFILE,
                    ClientAuthorizationService.SCOPE_PROFILE, "user_profile", clientUserId, ip);

            List<Guidebook> created = createGuidebooks(expertId, clientId, intents,
                    profileSnapshot);
            return submitAll(expertId, clientId, created, profileSnapshot, ip);
        } finally {
            try {
                redisTemplate.delete(lockKey);
            } catch (Exception e) {
                log.warn("释放批量生成锁失败 expertId={}: {}", expertId, e.getMessage());
            }
        }
    }

    /** 配额与 N 行创建同事务：任一行失败，扣减一起回滚。 */
    private List<Guidebook> createGuidebooks(Long expertId, Long clientId, List<IntentDto> intents,
            Map<String, Object> profileSnapshot) {
        List<Guidebook> created = transactionTemplate.execute(status -> {
            guidebookService.consumeQuota(expertId, intents.size());
            List<Guidebook> rows = new ArrayList<>();
            for (IntentDto intent : intents) {
                rows.add(guidebookService.createExpertGuidebook(expertId, clientId, intent,
                        profileSnapshot));
            }
            return rows;
        });
        if (created == null) {
            throw new BizException(ResultCode.FAIL, "批量任务创建失败");
        }
        return created;
    }

    /** 提交阶段允许部分失败：已创建的行保留，失败行标记 failed 交给现有重试链路。 */
    private BatchGenerateResponse submitAll(Long expertId, Long clientId, List<Guidebook> created,
            Map<String, Object> profileSnapshot, String ip) {
        List<BatchTaskResponse> tasks = new ArrayList<>();
        for (Guidebook guidebook : created) {
            GenerationContext ctx = new GenerationContext(guidebook.getId(), expertId, null,
                    guidebook.getIntent(), profileSnapshot);
            // 批量任务没有持有普通生成锁，结束时不得误删
            ctx.setHoldsGenerateLock(false);
            try {
                runner.submit(ctx);
                tasks.add(new BatchTaskResponse(String.valueOf(guidebook.getId()), "generating"));
                auditService.record(expertId, clientId,
                        ClientAccessAuditService.ACTION_GENERATE_GUIDEBOOK,
                        ClientAuthorizationService.SCOPE_GUIDEBOOKS, "guidebook",
                        guidebook.getId(), ip);
            } catch (RuntimeException e) {
                log.warn("批量任务提交失败 guidebookId={}: {}", guidebook.getId(), e.getMessage());
                markFailed(guidebook.getId());
                tasks.add(new BatchTaskResponse(String.valueOf(guidebook.getId()), "failed"));
            }
        }
        BatchGenerateResponse response = new BatchGenerateResponse();
        response.setTasks(tasks);
        return response;
    }

    private void markFailed(Long guidebookId) {
        guidebookMapper.update(null, new UpdateWrapper<Guidebook>()
                .eq("id", guidebookId)
                .set("status", Guidebook.STATUS_FAILED)
                .set("current_step", "failed")
                .set("error_message", "任务提交失败，请重试该本攻略")
                .set("finished_at", LocalDateTime.now()));
    }
}
