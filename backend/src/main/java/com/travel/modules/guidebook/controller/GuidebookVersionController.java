package com.travel.modules.guidebook.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.guidebook.dto.GuidebookEditResponse;
import com.travel.modules.guidebook.dto.GuidebookVersionItem;
import com.travel.modules.guidebook.service.GuidebookHistoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "攻略版本", description = "FR-5：版本列表与回溯")
@RestController
@RequestMapping("/api/guidebooks")
@RequiredArgsConstructor
public class GuidebookVersionController {

    private final GuidebookHistoryService historyService;

    /**
     * §7.8 版本列表：按版本号倒序。
     * @param guidebookId 攻略 ID
     * @return 版本条目
     */
    @Operation(summary = "攻略版本列表")
    @GetMapping("/{guidebookId}/versions")
    public Result<List<GuidebookVersionItem>> list(@PathVariable Long guidebookId) {
        return Result.success(historyService.listVersions(guidebookId));
    }

    /**
     * §7.9 回溯版本：复制旧快照成新版本。
     * @param guidebookId 攻略 ID
     * @param version 目标版本号
     * @return 新版本信息与冲突
     */
    @Operation(summary = "回溯版本")
    @PostMapping("/{guidebookId}/versions/{version}/restore")
    public Result<GuidebookEditResponse> restore(@PathVariable Long guidebookId,
            @PathVariable Integer version) {
        return Result.success(historyService.restore(guidebookId, version));
    }
}
