package com.travel.modules.guidebook.offline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.travel.modules.guidebook.entity.OfflinePack;
import com.travel.modules.guidebook.mapper.OfflinePackMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 在独立线程池中渲染离线包。先在临时文件写完整内容，再原子改名，
 * 避免用户下载到半成品；失败时清理临时文件并把任务置为 failed。
 */
@Slf4j
@Component
public class OfflinePackRunner {

    private static final int MAX_ERROR_LENGTH = 500;

    private final ThreadPoolTaskExecutor executor;
    private final OfflinePackMapper packMapper;
    private final OfflinePackContentBuilder contentBuilder;
    private final HtmlPackRenderer htmlRenderer;
    private final PdfPackRenderer pdfRenderer;
    private final OfflinePackProperties properties;

    public OfflinePackRunner(@Qualifier("offlinePackExecutor") ThreadPoolTaskExecutor executor,
            OfflinePackMapper packMapper, OfflinePackContentBuilder contentBuilder,
            HtmlPackRenderer htmlRenderer, PdfPackRenderer pdfRenderer,
            OfflinePackProperties properties) {
        this.executor = executor;
        this.packMapper = packMapper;
        this.contentBuilder = contentBuilder;
        this.htmlRenderer = htmlRenderer;
        this.pdfRenderer = pdfRenderer;
        this.properties = properties;
    }

    public void submit(Long taskId) {
        executor.execute(() -> run(taskId));
    }

    public void run(Long taskId) {
        OfflinePack pack = packMapper.selectById(taskId);
        if (pack == null || !OfflinePack.STATUS_PROCESSING.equals(pack.getStatus())) {
            return;
        }
        Path target = null;
        Path temp = null;
        try {
            OfflinePackContent content = contentBuilder.build(pack.getGuidebookId(),
                    pack.getGuidebookVersion(), Boolean.TRUE.equals(pack.getIncludeMaps()));
            byte[] bytes = OfflinePack.FORMAT_HTML.equals(pack.getFormat())
                    ? htmlRenderer.render(content).getBytes(StandardCharsets.UTF_8)
                    : pdfRenderer.render(content);

            long maxBytes = properties.getMaxFileMb() * 1024 * 1024;
            if (bytes.length > maxBytes) {
                throw new IllegalStateException("离线包超过体积上限 " + properties.getMaxFileMb() + "MB");
            }

            Path dir = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + "." + pack.getFormat();
            target = dir.resolve(filename);
            temp = dir.resolve(filename + ".tmp");
            Files.write(temp, bytes, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            moveIntoPlace(temp, target);

            OfflinePack update = new OfflinePack();
            update.setId(pack.getId());
            update.setStatus(OfflinePack.STATUS_COMPLETED);
            update.setFilePath(filename);
            update.setFileSize((long) bytes.length);
            update.setFinishedAt(LocalDateTime.now());
            packMapper.updateById(update);
        } catch (Exception e) {
            log.error("离线包生成失败 taskId={}: {}", taskId, e.getMessage(), e);
            deleteQuietly(temp);
            deleteQuietly(target);
            markFailed(taskId, e.getMessage() == null ? "离线包生成失败，请重试" : e.getMessage());
        }
    }

    private void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void markFailed(Long taskId, String message) {
        try {
            OfflinePack update = new OfflinePack();
            update.setId(taskId);
            update.setStatus(OfflinePack.STATUS_FAILED);
            update.setErrorMessage(truncate(message));
            update.setFinishedAt(LocalDateTime.now());
            packMapper.updateById(update);
        } catch (Exception e) {
            log.error("写入离线包失败状态异常 taskId={}: {}", taskId, e.getMessage());
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理离线包文件失败 file={}: {}", path, e.getMessage());
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
