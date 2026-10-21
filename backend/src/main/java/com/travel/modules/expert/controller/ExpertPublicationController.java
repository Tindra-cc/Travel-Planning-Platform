package com.travel.modules.expert.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.PageResult;
import com.travel.common.Result;
import com.travel.modules.expert.dto.PublicationItem;
import com.travel.modules.expert.dto.PublishRequest;
import com.travel.modules.expert.dto.PublishResponse;
import com.travel.modules.expert.service.GuidePublicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "达人工作台-发布", description = "FR-13：攻略发布到市场与下线")
@RestController
@RequestMapping("/api/expert")
@RequiredArgsConstructor
public class ExpertPublicationController {

    private final GuidePublicationService publicationService;

    @Operation(summary = "发布攻略到市场（待审核）")
    @PostMapping("/guidebooks/{guidebookId}/publish")
    public Result<PublishResponse> publish(@PathVariable Long guidebookId,
            @Valid @RequestBody PublishRequest request) {
        return Result.success(publicationService.publish(guidebookId, request));
    }

    @Operation(summary = "我的发布列表")
    @GetMapping("/publications")
    public Result<PageResult<PublicationItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(publicationService.list(page, pageSize, status));
    }

    @Operation(summary = "下线发布")
    @PostMapping("/publications/{publicationId}/offline")
    public Result<Void> offline(@PathVariable Long publicationId) {
        publicationService.offline(publicationId);
        return Result.success();
    }
}
