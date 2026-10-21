package com.travel.modules.expert.controller;

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
import com.travel.modules.expert.dto.AuthorizeRequest;
import com.travel.modules.expert.dto.AuthorizeResponse;
import com.travel.modules.expert.dto.ClientAccessLogResponse;
import com.travel.modules.expert.dto.ExpertClientRequest;
import com.travel.modules.expert.dto.ExpertClientResponse;
import com.travel.modules.expert.entity.ExpertClient;
import com.travel.modules.expert.service.ClientAccessAuditService;
import com.travel.modules.expert.service.ClientAuthorizationService;
import com.travel.modules.expert.service.ExpertClientService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 达人侧客户管理；达人门禁由 ExpertInterceptor 统一完成。 */
@Tag(name = "达人工作台-客户", description = "FR-13：客户档案、授权与访问审计")
@RestController
@RequestMapping("/api/expert/clients")
@RequiredArgsConstructor
public class ExpertClientController {

    private final ExpertClientService clientService;
    private final ClientAuthorizationService authorizationService;
    private final ClientAccessAuditService auditService;

    @Operation(summary = "客户列表")
    @GetMapping
    public Result<PageResult<ExpertClientResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(clientService.list(page, pageSize, status));
    }

    @Operation(summary = "创建客户档案")
    @PostMapping
    public Result<ExpertClientResponse> create(@Valid @RequestBody ExpertClientRequest request) {
        return Result.success(clientService.create(request));
    }

    @Operation(summary = "客户详情（含授权状态）")
    @GetMapping("/{clientId}")
    public Result<ExpertClientResponse> detail(@PathVariable Long clientId,
            HttpServletRequest request) {
        ExpertClient client = clientService.requireOwned(clientId);
        ExpertClientResponse response = clientService.toDetailResponse(client);
        auditService.record(client.getExpertId(), clientId,
                ClientAccessAuditService.ACTION_VIEW_CLIENT, null,
                "expert_client", clientId, request.getRemoteAddr());
        return Result.success(response);
    }

    @Operation(summary = "发起授权（返回一次性确认令牌）")
    @PostMapping("/{clientId}/authorize")
    public Result<AuthorizeResponse> authorize(@PathVariable Long clientId,
            @Valid @RequestBody AuthorizeRequest request) {
        return Result.success(authorizationService.authorize(clientId, request.getScope()));
    }

    @Operation(summary = "撤回客户当前的授权")
    @DeleteMapping("/{clientId}/authorization")
    public Result<Void> revokeAuthorization(@PathVariable Long clientId) {
        authorizationService.revokeActiveForClient(clientId);
        return Result.success();
    }

    @Operation(summary = "客户访问审计")
    @GetMapping("/{clientId}/access-logs")
    public Result<PageResult<ClientAccessLogResponse>> accessLogs(@PathVariable Long clientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        clientService.requireOwned(clientId);
        return Result.success(auditService.list(clientId, page, pageSize));
    }

    @Operation(summary = "归档客户")
    @DeleteMapping("/{clientId}")
    public Result<Void> archive(@PathVariable Long clientId) {
        clientService.archive(clientId);
        return Result.success();
    }
}
