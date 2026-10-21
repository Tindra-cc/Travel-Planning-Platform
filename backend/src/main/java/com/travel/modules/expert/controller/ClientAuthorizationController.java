package com.travel.modules.expert.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.expert.dto.AuthorizationConfirmResponse;
import com.travel.modules.expert.service.ClientAuthorizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 客户侧授权接口。刻意不放在 /api/expert/** 下，
 * 否则普通客户会被达人门禁拦成 40300；仍要求登录。
 */
@Tag(name = "客户授权", description = "FR-13：客户确认与撤回授权")
@RestController
@RequestMapping("/api/client-authorizations")
@RequiredArgsConstructor
public class ClientAuthorizationController {

    private final ClientAuthorizationService authorizationService;

    @Operation(summary = "客户确认授权")
    @PostMapping("/{token}/confirm")
    public Result<AuthorizationConfirmResponse> confirm(@PathVariable String token) {
        return Result.success(authorizationService.confirm(token));
    }

    @Operation(summary = "撤回授权（达人或客户）")
    @PostMapping("/{authorizationId}/revoke")
    public Result<Void> revoke(@PathVariable Long authorizationId) {
        authorizationService.revoke(authorizationId);
        return Result.success();
    }
}
