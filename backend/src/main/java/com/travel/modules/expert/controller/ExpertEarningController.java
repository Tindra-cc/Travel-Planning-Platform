package com.travel.modules.expert.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.expert.dto.EarningsPageResponse;
import com.travel.modules.expert.service.ExpertEarningService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "达人-收益", description = "FR-13：收益只读占位，无订单与支付链路")
@RestController
@RequestMapping("/api/expert/earnings")
@RequiredArgsConstructor
public class ExpertEarningController {

    private final ExpertEarningService earningService;

    @Operation(summary = "达人收益列表（占位）")
    @GetMapping
    public Result<EarningsPageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(earningService.list(page, pageSize));
    }
}
