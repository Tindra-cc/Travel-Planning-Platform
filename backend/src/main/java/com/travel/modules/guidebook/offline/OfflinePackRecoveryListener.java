package com.travel.modules.guidebook.offline;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.modules.guidebook.entity.OfflinePack;
import com.travel.modules.guidebook.mapper.OfflinePackMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动恢复：超时仍为 processing 的任务直接置 failed（不做断点续跑），
 * 同时清理崩溃残留的 .tmp 文件，避免目录无限增长。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflinePackRecoveryListener {

    private static final String REASON = "服务器重启，离线包任务已中断，请重试";

    private final OfflinePackMapper packMapper;
    private final OfflinePackProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverStaleTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(properties.getStaleMinutes());
        List<OfflinePack> stale = packMapper.selectList(new LambdaQueryWrapper<OfflinePack>()
                .eq(OfflinePack::getStatus, OfflinePack.STATUS_PROCESSING)
                .lt(OfflinePack::getStartedAt, threshold));
        for (OfflinePack pack : stale) {
            OfflinePack update = new OfflinePack();
            update.setId(pack.getId());
            update.setStatus(OfflinePack.STATUS_FAILED);
            update.setErrorMessage(REASON);
            update.setFinishedAt(LocalDateTime.now());
            packMapper.updateById(update);
            log.warn("恢复中断的离线包任务 taskId={}", pack.getId());
        }
        cleanupTempFiles(threshold);
    }

    private void cleanupTempFiles(LocalDateTime threshold) {
        Path dir = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.tmp")) {
            for (Path file : stream) {
                try {
                    if (Files.getLastModifiedTime(file).toInstant()
                            .isBefore(threshold.atZone(java.time.ZoneId.systemDefault()).toInstant())) {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException e) {
                    log.warn("清理临时文件失败 file={}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("扫描离线包目录失败 dir={}: {}", dir, e.getMessage());
        }
    }
}
