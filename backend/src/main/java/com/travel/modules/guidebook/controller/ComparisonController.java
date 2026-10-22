package com.travel.modules.guidebook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.guidebook.compare.ComparisonService;
import com.travel.modules.guidebook.compare.MixService;
import com.travel.modules.guidebook.dto.CompareRequest;
import com.travel.modules.guidebook.dto.CompareResponse;
import com.travel.modules.guidebook.dto.ComparisonDetailResponse;
import com.travel.modules.guidebook.dto.MixRequest;
import com.travel.modules.guidebook.dto.MixResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "多方案对比", description = "FR-4：生成对比方案 / 对比详情 / 混搭")
@RestController
@RequestMapping("/api/guidebooks/compare")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final MixService mixService;

    @Operation(summary = "生成对比方案", description = "一次对比只扣一次配额；默认 budget/balanced/luxury")
    @PostMapping
    public Result<CompareResponse> start(@RequestBody CompareRequest req) {
        return Result.success(comparisonService.start(req));
    }

    @Operation(summary = "最近一次对比", description = "用于退出后恢复上次对比结果；没有时返回 null")
    @GetMapping("/latest")
    public Result<ComparisonDetailResponse> latest(
            @RequestParam(required = false) Long baseGuidebookId) {
        return Result.success(comparisonService.latest(baseGuidebookId));
    }

    @Operation(summary = "对比详情（轮询）")
    @GetMapping("/{comparisonId}")
    public Result<ComparisonDetailResponse> detail(@PathVariable Long comparisonId) {
        return Result.success(comparisonService.detail(comparisonId));
    }

    @Operation(summary = "混搭生成", description = "存在冲突时返回 blocked 且不落库")
    @PostMapping("/{comparisonId}/mix")
    public Result<MixResponse> mix(@PathVariable Long comparisonId,
                                   @RequestBody MixRequest req) {
        return Result.success(mixService.mix(comparisonId, req));
    }
}
