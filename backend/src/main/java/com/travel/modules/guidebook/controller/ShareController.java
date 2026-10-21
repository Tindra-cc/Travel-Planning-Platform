package com.travel.modules.guidebook.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.guidebook.dto.RegenerateRequest;
import com.travel.modules.guidebook.dto.RegenerateResponse;
import com.travel.modules.guidebook.dto.ShareCreateRequest;
import com.travel.modules.guidebook.dto.ShareCreateResponse;
import com.travel.modules.guidebook.dto.SharedGuidebookResponse;
import com.travel.modules.guidebook.service.ShareRegenerationService;
import com.travel.modules.guidebook.service.ShareService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "攻略分享", description = "FR-12：快照分享与公开访问")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final ShareRegenerationService regenerationService;

    @Operation(summary = "创建分享")
    @PostMapping("/guidebooks/{guidebookId}/share")
    public Result<ShareCreateResponse> create(@PathVariable Long guidebookId,
            @Valid @RequestBody ShareCreateRequest req) {
        return Result.success(shareService.create(guidebookId, req));
    }

    @Operation(summary = "公开获取分享攻略")
    @GetMapping("/share/{shareCode}")
    public Result<SharedGuidebookResponse> get(@PathVariable String shareCode) {
        return Result.success(shareService.getPublic(shareCode));
    }

    @Operation(summary = "基于分享再生成", description = "位于公开前缀下，但服务层显式要求登录")
    @PostMapping("/share/{shareCode}/regenerate")
    public Result<RegenerateResponse> regenerate(@PathVariable String shareCode,
            @Valid @RequestBody RegenerateRequest req) {
        return Result.success(regenerationService.regenerate(shareCode, req.getSessionId()));
    }

    @Operation(summary = "撤回分享")
    @DeleteMapping("/guidebooks/{guidebookId}/share/{shareCode}")
    public Result<Void> revoke(@PathVariable Long guidebookId, @PathVariable String shareCode) {
        shareService.revoke(guidebookId, shareCode);
        return Result.success();
    }
}
