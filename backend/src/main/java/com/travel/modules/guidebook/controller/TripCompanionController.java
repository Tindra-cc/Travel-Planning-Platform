package com.travel.modules.guidebook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.Result;
import com.travel.modules.guidebook.dto.NodeStatusRequest;
import com.travel.modules.guidebook.dto.NodeStatusResponse;
import com.travel.modules.guidebook.dto.TripCompanionResponse;
import com.travel.modules.guidebook.service.TripCompanionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "行程伴侣", description = "FR-10：出行中状态与节点执行")
@RestController
@RequestMapping("/api/trip-companion")
@RequiredArgsConstructor
public class TripCompanionController {

    private final TripCompanionService companionService;

    @Operation(summary = "获取行程伴侣状态")
    @GetMapping("/{guidebookId}")
    public Result<TripCompanionResponse> get(@PathVariable Long guidebookId) {
        return Result.success(companionService.get(guidebookId));
    }

    @Operation(summary = "标记节点执行状态")
    @PostMapping("/{guidebookId}/nodes/{nodeId}/status")
    public Result<NodeStatusResponse> markNode(@PathVariable Long guidebookId,
            @PathVariable String nodeId, @RequestBody NodeStatusRequest req) {
        return Result.success(companionService.markNode(guidebookId, nodeId, req.getStatus()));
    }
}
