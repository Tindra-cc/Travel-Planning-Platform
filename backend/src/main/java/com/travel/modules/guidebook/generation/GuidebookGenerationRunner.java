package com.travel.modules.guidebook.generation;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.travel.modules.chat.entity.ChatSession;
import com.travel.modules.chat.mapper.ChatSessionMapper;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;

import lombok.extern.slf4j.Slf4j;

/** 在专用线程池中执行生成流水线，并负责终态落库与推送。 */
@Slf4j
@Component
public class GuidebookGenerationRunner {

    private static final int MAX_ERROR_LENGTH = 500;

    private final ThreadPoolTaskExecutor executor;
    private final GuidebookGenerationPipeline pipeline;
    private final GuidebookMapper guidebookMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final GuidebookProgressPublisher publisher;
    private final StringRedisTemplate redisTemplate;

    public GuidebookGenerationRunner(@Qualifier("guidebookExecutor") ThreadPoolTaskExecutor executor,
            GuidebookGenerationPipeline pipeline, GuidebookMapper guidebookMapper,
            ChatSessionMapper chatSessionMapper, GuidebookProgressPublisher publisher,
            StringRedisTemplate redisTemplate) {
        this.executor = executor;
        this.pipeline = pipeline;
        this.guidebookMapper = guidebookMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.publisher = publisher;
        this.redisTemplate = redisTemplate;
    }

    public boolean hasCapacity() {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        return pool.getActiveCount() < pool.getMaximumPoolSize()
                || pool.getQueue().remainingCapacity() > 0;
    }

    public void submit(GenerationContext ctx) {
        executor.execute(() -> run(ctx));
    }

    public void run(GenerationContext ctx) {
        try {
            pipeline.run(ctx);
            markSession(ctx, ChatSession.STATUS_GENERATED);
            publisher.done(ctx.getGuidebookId());
        } catch (Exception e) {
            log.error("攻略生成失败 guidebookId={}: {}", ctx.getGuidebookId(), e.getMessage(), e);
            String message = e.getMessage() == null ? "生成失败，请重试" : e.getMessage();
            markFailed(ctx.getGuidebookId(), ctx.getSessionId(), message);
            publisher.failed(ctx.getGuidebookId(), message);
        } finally {
            if (!ctx.isHoldsGenerateLock()) {
                return;
            }
            try {
                redisTemplate.delete("travel:gen:lock:" + ctx.getUserId());
            } catch (Exception e) {
                log.warn("释放生成锁失败 userId={}: {}", ctx.getUserId(), e.getMessage());
            }
        }
    }

    private void markSession(GenerationContext ctx, String status) {
        if (ctx.getSessionId() == null) {
            return;
        }
        ChatSession session = new ChatSession();
        session.setId(ctx.getSessionId());
        session.setStatus(status);
        session.setGuidebookId(ctx.getGuidebookId());
        chatSessionMapper.updateById(session);
    }

    private void markFailed(Long guidebookId, Long sessionId, String message) {
        try {
            Guidebook guidebook = new Guidebook();
            guidebook.setId(guidebookId);
            guidebook.setStatus(Guidebook.STATUS_FAILED);
            guidebook.setCurrentStep("failed");
            guidebook.setErrorMessage(truncate(message));
            guidebook.setFinishedAt(LocalDateTime.now());
            guidebookMapper.updateById(guidebook);
            if (sessionId != null) {
                ChatSession session = new ChatSession();
                session.setId(sessionId);
                session.setStatus(ChatSession.STATUS_FAILED);
                chatSessionMapper.updateById(session);
            }
        } catch (Exception e) {
            log.error("写入失败状态异常 guidebookId={}: {}", guidebookId, e.getMessage());
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
