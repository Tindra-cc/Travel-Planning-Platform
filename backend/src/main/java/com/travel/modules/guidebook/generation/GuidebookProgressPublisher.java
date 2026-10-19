package com.travel.modules.guidebook.generation;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.ws.GuidebookProgressHub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 把流水线进度写入 DB、Redis，并通过 WebSocket 广播；推送失败不影响生成。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuidebookProgressPublisher {

    private static final Duration PROGRESS_TTL = Duration.ofHours(1);
    private static final String KEY_PREFIX = "travel:guidebook:progress:";

    private final GuidebookMapper guidebookMapper;
    private final StringRedisTemplate redisTemplate;
    private final GuidebookProgressHub hub;
    private final ObjectMapper objectMapper;

    public void step(Long guidebookId, String currentStep, int progress) {
        try {
            guidebookMapper.update(null, new LambdaUpdateWrapper<Guidebook>()
                    .eq(Guidebook::getId, guidebookId)
                    .set(Guidebook::getCurrentStep, currentStep)
                    .set(Guidebook::getProgress, progress));
        } catch (Exception e) {
            log.warn("写入攻略进度失败: {}", e.getMessage());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "guidebook_progress");
        payload.put("guidebookId", guidebookId);
        payload.put("status", Guidebook.STATUS_GENERATING);
        payload.put("currentStep", currentStep);
        payload.put("progress", progress);
        publish(guidebookId, payload, "step");
    }

    public void done(Long guidebookId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "guidebook_done");
        payload.put("guidebookId", guidebookId);
        payload.put("status", Guidebook.STATUS_DRAFT);
        payload.put("progress", 100);
        publish(guidebookId, payload, "done");
    }

    public void failed(Long guidebookId, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("guidebookId", guidebookId);
        payload.put("status", Guidebook.STATUS_FAILED);
        payload.put("message", message);
        publish(guidebookId, payload, "failed");
    }

    private void publish(Long guidebookId, Map<String, Object> payload, String phase) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("进度消息序列化失败: {}", e.getMessage());
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + guidebookId, json, PROGRESS_TTL);
        } catch (Exception e) {
            log.warn("缓存攻略进度失败: {}", e.getMessage());
        }
        hub.broadcast(guidebookId, json);
    }
}
