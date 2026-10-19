package com.travel.modules.guidebook.generation;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.modules.chat.entity.ChatSession;
import com.travel.modules.chat.mapper.ChatSessionMapper;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 启动时把超过 10 分钟仍为 generating 的任务置为 failed，允许用户重试。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationRecoveryListener {

    private static final String REASON = "服务器重启，生成任务已中断，请重试";

    private final GuidebookMapper guidebookMapper;
    private final ChatSessionMapper chatSessionMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverStaleTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<Guidebook> stale = guidebookMapper.selectList(new LambdaQueryWrapper<Guidebook>()
                .eq(Guidebook::getStatus, Guidebook.STATUS_GENERATING)
                .lt(Guidebook::getUpdateTime, threshold));
        for (Guidebook guidebook : stale) {
            Guidebook update = new Guidebook();
            update.setId(guidebook.getId());
            update.setStatus(Guidebook.STATUS_FAILED);
            update.setCurrentStep("failed");
            update.setErrorMessage(REASON);
            update.setFinishedAt(LocalDateTime.now());
            guidebookMapper.updateById(update);
            if (guidebook.getSessionId() != null) {
                ChatSession session = new ChatSession();
                session.setId(guidebook.getSessionId());
                session.setStatus(ChatSession.STATUS_FAILED);
                chatSessionMapper.updateById(session);
            }
            log.warn("恢复中断的攻略生成任务 guidebookId={}", guidebook.getId());
        }
    }
}
