package com.travel.modules.expert.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.expert.dto.BatchGenerateRequest;
import com.travel.modules.expert.dto.BatchGenerateResponse;
import com.travel.modules.expert.service.ExpertBatchGenerationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "达人工作台-批量生成", description = "FR-13：一次为客户生成 1–5 本攻略")
@RestController
@RequestMapping("/api/expert/clients")
@RequiredArgsConstructor
public class ExpertGenerationController {

    private final ExpertBatchGenerationService batchService;

    @Operation(summary = "为客户批量生成攻略")
    @PostMapping("/{clientId}/guidebooks/generate")
    public Result<BatchGenerateResponse> generate(@PathVariable Long clientId,
            @Valid @RequestBody BatchGenerateRequest request, HttpServletRequest servletRequest) {
        return Result.success(
                batchService.generate(clientId, request, servletRequest.getRemoteAddr()));
    }
}
