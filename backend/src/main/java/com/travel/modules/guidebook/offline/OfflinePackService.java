package com.travel.modules.guidebook.offline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.dto.OfflinePackRequest;
import com.travel.modules.guidebook.dto.OfflinePackStatusResponse;
import com.travel.modules.guidebook.dto.OfflinePackTaskResponse;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.OfflinePack;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.OfflinePackMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FR-9 离线包任务：只负责状态流转与文件定位，渲染在 OfflinePackRunner 中异步执行。
 * 幂等键是（攻略, 版本, 格式, 用户）：重复点击不会重复渲染。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflinePackService {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            Guidebook.STATUS_DRAFT, Guidebook.STATUS_EDITING,
            Guidebook.STATUS_CONFIRMED, Guidebook.STATUS_EXECUTING);
    private static final String EXPIRED_MESSAGE = "离线包已过期，请重新生成";
    private static final String MISSING_MESSAGE = "离线包文件已丢失，请重新生成";

    private final OfflinePackMapper packMapper;
    private final GuidebookMapper guidebookMapper;
    private final OfflinePackRunner runner;
    private final OfflinePackProperties properties;

    /** §9.1 创建（或复用）离线包任务。 */
    public OfflinePackTaskResponse request(Long guidebookId, OfflinePackRequest req) {
        Guidebook guidebook = requireOwnedGuidebook(guidebookId);
        if (!ALLOWED_STATUSES.contains(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "当前状态不可生成离线包");
        }
        String format = resolveFormat(req);
        boolean includeMaps = req == null || req.getIncludeMaps() == null || req.getIncludeMaps();
        Long userId = UserContext.getUserId();

        OfflinePack reusable = packMapper.selectOne(new LambdaQueryWrapper<OfflinePack>()
                .eq(OfflinePack::getGuidebookId, guidebookId)
                .eq(OfflinePack::getGuidebookVersion, guidebook.getVersion())
                .eq(OfflinePack::getFormat, format)
                .eq(OfflinePack::getUserId, userId)
                .in(OfflinePack::getStatus, OfflinePack.STATUS_PROCESSING, OfflinePack.STATUS_COMPLETED)
                .gt(OfflinePack::getExpiresAt, LocalDateTime.now())
                .orderByDesc(OfflinePack::getId)
                .last("LIMIT 1"));
        if (reusable != null) {
            return new OfflinePackTaskResponse(reusable.getId(), reusable.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        OfflinePack pack = new OfflinePack();
        pack.setGuidebookId(guidebookId);
        pack.setGuidebookVersion(guidebook.getVersion());
        pack.setUserId(userId);
        pack.setFormat(format);
        pack.setIncludeMaps(includeMaps);
        pack.setStatus(OfflinePack.STATUS_PROCESSING);
        pack.setStartedAt(now);
        pack.setExpiresAt(now.plusHours(properties.getExpireHours()));
        packMapper.insert(pack);

        try {
            runner.submit(pack.getId());
        } catch (TaskRejectedException e) {
            markFailed(pack.getId(), "离线包任务繁忙，请稍后重试");
            throw new BizException(ResultCode.CONFLICT, "离线包任务繁忙，请稍后重试");
        }
        return new OfflinePackTaskResponse(pack.getId(), OfflinePack.STATUS_PROCESSING);
    }

    /** §9.2 查询任务状态；过期的 completed 会惰性转为 failed 并删除文件。 */
    public OfflinePackStatusResponse status(Long taskId) {
        OfflinePack pack = expireIfNeeded(requireOwnedTask(taskId));
        String downloadUrl = OfflinePack.STATUS_COMPLETED.equals(pack.getStatus())
                ? "/api/offline-packs/" + pack.getId() + "/download"
                : null;
        return new OfflinePackStatusResponse(pack.getId(), pack.getStatus(), downloadUrl,
                pack.getExpiresAt(), pack.getErrorMessage());
    }

    /** §9.3 定位下载文件；未完成或已过期返回 40900。 */
    public DownloadInfo download(Long taskId) {
        OfflinePack pack = expireIfNeeded(requireOwnedTask(taskId));
        if (!OfflinePack.STATUS_COMPLETED.equals(pack.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "离线包尚未生成完成");
        }
        Path path = resolveFile(pack.getFilePath());
        if (path == null || !Files.isRegularFile(path)) {
            markFailed(pack.getId(), MISSING_MESSAGE);
            throw new BizException(ResultCode.CONFLICT, MISSING_MESSAGE);
        }
        return new DownloadInfo(path, pack.getFormat(), pack.getGuidebookId(),
                pack.getGuidebookVersion(), pack.getExpiresAt());
    }

    private String resolveFormat(OfflinePackRequest req) {
        String raw = req == null ? null : req.getFormat();
        if (!StringUtils.hasText(raw)) {
            return OfflinePack.FORMAT_PDF;
        }
        String format = raw.trim().toLowerCase();
        if (!OfflinePack.FORMAT_PDF.equals(format) && !OfflinePack.FORMAT_HTML.equals(format)) {
            throw new BizException(ResultCode.PARAM_ERROR, "format 仅支持 pdf / html");
        }
        return format;
    }

    private OfflinePack requireOwnedTask(Long taskId) {
        OfflinePack pack = packMapper.selectById(taskId);
        if (pack == null) {
            throw new BizException(ResultCode.NOT_FOUND, "离线包任务不存在");
        }
        if (!pack.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该离线包");
        }
        return pack;
    }

    private Guidebook requireOwnedGuidebook(Long guidebookId) {
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null) {
            throw new BizException(ResultCode.NOT_FOUND, "攻略不存在");
        }
        if (!guidebook.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该攻略");
        }
        return guidebook;
    }

    private OfflinePack expireIfNeeded(OfflinePack pack) {
        if (!OfflinePack.STATUS_COMPLETED.equals(pack.getStatus())
                || pack.getExpiresAt() == null
                || pack.getExpiresAt().isAfter(LocalDateTime.now())) {
            return pack;
        }
        deleteQuietly(pack.getFilePath());
        markFailed(pack.getId(), EXPIRED_MESSAGE);
        pack.setStatus(OfflinePack.STATUS_FAILED);
        pack.setErrorMessage(EXPIRED_MESSAGE);
        return pack;
    }

    private void markFailed(Long packId, String message) {
        try {
            OfflinePack update = new OfflinePack();
            update.setId(packId);
            update.setStatus(OfflinePack.STATUS_FAILED);
            update.setErrorMessage(message);
            update.setFinishedAt(LocalDateTime.now());
            packMapper.updateById(update);
        } catch (Exception e) {
            log.error("写入离线包失败状态异常 taskId={}: {}", packId, e.getMessage());
        }
    }

    private Path resolveFile(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        Path base = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
        Path file = base.resolve(filename).normalize();
        // 文件名由服务端生成，这里仍校验一次，避免脏数据导致目录穿越。
        return file.startsWith(base) ? file : null;
    }

    private void deleteQuietly(String filename) {
        Path file = resolveFile(filename);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("删除离线包文件失败 file={}: {}", file, e.getMessage());
        }
    }

    public record DownloadInfo(Path path, String format, Long guidebookId,
            Integer version, LocalDateTime expiresAt) {
    }
}
