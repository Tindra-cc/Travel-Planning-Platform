package com.travel.modules.guidebook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.AdminGuard;
import com.travel.common.PageResult;
import com.travel.common.Result;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.dto.CreateKnowledgeRequest;
import com.travel.modules.guidebook.dto.KnowledgeDocItem;
import com.travel.modules.guidebook.dto.KnowledgeReviewResponse;
import com.travel.modules.guidebook.dto.ReviewGuidePostRequest;
import com.travel.modules.guidebook.service.AdminKnowledgeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "运营-知识库", description = "FR-7：知识条目维护与达人攻略审核")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminKnowledgeController {

    private final AdminGuard adminGuard;
    private final AdminKnowledgeService adminKnowledgeService;

    @Operation(summary = "知识条目列表")
    @GetMapping("/knowledge")
    public Result<PageResult<KnowledgeDocItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        adminGuard.requireAdmin();
        return Result.success(adminKnowledgeService.list(status, page, pageSize));
    }

    @Operation(summary = "新增知识条目（直接入库）")
    @PostMapping("/knowledge")
    public Result<KnowledgeDocItem> create(@RequestBody CreateKnowledgeRequest request) {
        adminGuard.requireAdmin();
        return Result.success(adminKnowledgeService.create(request, UserContext.getUserId()));
    }

    @Operation(summary = "审核达人攻略；通过则写入知识库")
    @PostMapping("/guidebooks/{publishedId}/review")
    public Result<KnowledgeReviewResponse> review(@PathVariable Long publishedId,
            @RequestBody ReviewGuidePostRequest request) {
        adminGuard.requireAdmin();
        boolean approved = request != null && request.isApproved();
        return Result.success(adminKnowledgeService.review(publishedId, approved,
                UserContext.getUserId()));
    }
}
