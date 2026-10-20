package com.travel.modules.guidebook.controller;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.PageResult;
import com.travel.common.Result;
import com.travel.modules.guidebook.dto.BindResourceRequest;
import com.travel.modules.guidebook.dto.GuidebookEditResponse;
import com.travel.modules.guidebook.dto.ResourceItemDto;
import com.travel.modules.guidebook.service.GuidebookEditService;
import com.travel.modules.guidebook.service.ResourceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "资源与预订", description = "FR-8：资源搜索、节点绑定")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final GuidebookEditService editService;

    @Operation(summary = "搜索资源")
    @GetMapping("/resources")
    public Result<PageResult<ResourceItemDto>> search(@RequestParam String type,
            @RequestParam String destination,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) BigDecimal budgetMin,
            @RequestParam(required = false) BigDecimal budgetMax,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(
                resourceService.search(type, destination, budgetMin, budgetMax, page, pageSize));
    }

    @Operation(summary = "绑定资源到节点（resourceSlotId 为空表示解绑）")
    @PostMapping("/guidebooks/{guidebookId}/nodes/{nodeId}/resource")
    public Result<GuidebookEditResponse> bind(@PathVariable Long guidebookId,
            @PathVariable String nodeId,
            @RequestBody BindResourceRequest request) {
        return Result.success(editService.bindResource(guidebookId, nodeId,
                request == null ? null : request.getResourceSlotId()));
    }
}
