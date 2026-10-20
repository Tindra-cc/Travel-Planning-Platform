package com.travel.modules.guidebook.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.guidebook.dto.OfflinePackRequest;
import com.travel.modules.guidebook.dto.OfflinePackStatusResponse;
import com.travel.modules.guidebook.dto.OfflinePackTaskResponse;
import com.travel.modules.guidebook.entity.OfflinePack;
import com.travel.modules.guidebook.offline.OfflinePackService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** FR-9：离线包任务与文件下载。下载接口不走统一信封，直接返回文件流。 */
@Tag(name = "离线攻略包", description = "FR-9：生成 / 查询状态 / 下载")
@RestController
@RequiredArgsConstructor
public class OfflinePackController {

    private final OfflinePackService offlinePackService;

    @Operation(summary = "生成离线包", description = "同攻略同版本同格式存在未过期任务时直接复用")
    @PostMapping("/api/guidebooks/{guidebookId}/offline-pack")
    public Result<OfflinePackTaskResponse> create(@PathVariable Long guidebookId,
            @RequestBody(required = false) OfflinePackRequest req) {
        return Result.success(offlinePackService.request(guidebookId, req));
    }

    @Operation(summary = "查询离线包状态")
    @GetMapping("/api/offline-packs/{taskId}")
    public Result<OfflinePackStatusResponse> status(@PathVariable Long taskId) {
        return Result.success(offlinePackService.status(taskId));
    }

    @Operation(summary = "下载离线包", description = "未完成或已过期返回 40900；非所有者返回 40301")
    @GetMapping("/api/offline-packs/{taskId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long taskId) {
        OfflinePackService.DownloadInfo info = offlinePackService.download(taskId);
        String extension = info.format();
        String ascii = "guidebook-" + info.guidebookId() + "-v" + info.version() + "." + extension;
        String chinese = "旅行攻略-" + info.guidebookId() + "-v" + info.version() + "." + extension;
        MediaType mediaType = OfflinePack.FORMAT_PDF.equals(extension)
                ? MediaType.APPLICATION_PDF
                : new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(ascii, chinese))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(info.path()));
    }

    /**
     * 同时给出 ASCII 文件名与 RFC 5987 中文名。
     * Spring 的 ContentDisposition 只能保留一个 filename，因此手工拼装。
     */
    private String contentDisposition(String ascii, String chinese) {
        String encoded = URLEncoder.encode(chinese, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
