package com.travel.modules.guidebook.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.PageResult;
import com.travel.common.Result;
import com.travel.modules.guidebook.dto.ExportRequest;
import com.travel.modules.guidebook.dto.ExportResponse;
import com.travel.modules.guidebook.dto.GenerateGuidebookRequest;
import com.travel.modules.guidebook.dto.GenerateGuidebookResponse;
import com.travel.modules.guidebook.dto.GuidebookDetailResponse;
import com.travel.modules.guidebook.dto.GuidebookListItem;
import com.travel.modules.guidebook.dto.GuidebookValidateResponse;
import com.travel.modules.guidebook.service.GuidebookConflictService;
import com.travel.modules.guidebook.service.GuidebookService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "攻略生成", description = "FR-3：触发生成 / 查询进度 / 重试 / 列表 / 删除 / 导出")
@RestController
@RequestMapping("/api/guidebooks")
@RequiredArgsConstructor
public class GuidebookController {

    private final GuidebookService guidebookService;
    private final GuidebookConflictService conflictService;

    @Operation(summary = "触发攻略生成",
            description = "sessionId 与 intent 二选一。同会话幂等返回已有攻略；每用户同时只允许一个生成任务。")
    @PostMapping("/generate")
    public Result<GenerateGuidebookResponse> generate(@RequestBody GenerateGuidebookRequest req) {
        return Result.success(guidebookService.generate(req));
    }

    @Operation(summary = "攻略详情（轮询兜底接口）")
    @GetMapping("/{guidebookId}")
    public Result<GuidebookDetailResponse> detail(@PathVariable Long guidebookId) {
        return Result.success(guidebookService.detail(guidebookId));
    }

    @Operation(summary = "失败重试", description = "仅 failed 可重试，最多 3 次")
    @PostMapping("/{guidebookId}/retry")
    public Result<GenerateGuidebookResponse> retry(@PathVariable Long guidebookId) {
        return Result.success(guidebookService.retry(guidebookId));
    }

    @Operation(summary = "我的攻略列表")
    @GetMapping
    public Result<PageResult<GuidebookListItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(guidebookService.list(status, page, pageSize));
    }

    @Operation(summary = "删除攻略", description = "confirmed/executing 状态返回 40900")
    @DeleteMapping("/{guidebookId}")
    public Result<Void> delete(@PathVariable Long guidebookId) {
        guidebookService.delete(guidebookId);
        return Result.success();
    }

    @Operation(summary = "一键修复行程冲突",
            description = "自动顺延同日重叠时段、把预算上限压回意图预算；营业时间冲突需手动调整")
    @PostMapping("/{guidebookId}/conflicts/repair")
    public Result<GuidebookValidateResponse> repairConflicts(@PathVariable Long guidebookId) {
        return Result.success(conflictService.repair(guidebookId));
    }

    @Operation(summary = "导出攻略", description = "支持 markdown/html；pdf 返回 42200")
    @PostMapping("/{guidebookId}/export")
    public Result<ExportResponse> export(@PathVariable Long guidebookId,
                                         @RequestBody(required = false) ExportRequest req) {
        String format = req == null ? "markdown" : req.getFormat();
        return Result.success(guidebookService.export(guidebookId, format));
    }
}
