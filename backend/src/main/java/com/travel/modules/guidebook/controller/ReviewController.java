package com.travel.modules.guidebook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.guidebook.dto.ProfileIterationRequest;
import com.travel.modules.guidebook.dto.ProfileIterationResponse;
import com.travel.modules.guidebook.dto.ReviewDto;
import com.travel.modules.guidebook.dto.ReviewRequest;
import com.travel.modules.guidebook.dto.ReviewSummaryResponse;
import com.travel.modules.guidebook.service.ProfileIterationService;
import com.travel.modules.guidebook.service.ReviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "复盘闭环", description = "FR-11：执行统计与评价")
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ProfileIterationService profileIterationService;

    @Operation(summary = "获取复盘摘要")
    @GetMapping("/{guidebookId}/summary")
    public Result<ReviewSummaryResponse> summary(@PathVariable Long guidebookId) {
        return Result.success(reviewService.summary(guidebookId));
    }

    @Operation(summary = "提交或更新评价")
    @PostMapping
    public Result<ReviewDto> submit(@Valid @RequestBody ReviewRequest req) {
        return Result.success(reviewService.submit(req));
    }

    @Operation(summary = "确认或拒绝画像迭代建议")
    @PostMapping("/{guidebookId}/profile-iteration")
    public Result<ProfileIterationResponse> decideProfileIteration(
            @PathVariable Long guidebookId,
            @Valid @RequestBody ProfileIterationRequest req) {
        int version = profileIterationService.decide(guidebookId, req.getAccept());
        return Result.success(new ProfileIterationResponse(version));
    }
}
