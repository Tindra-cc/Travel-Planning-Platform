package com.travel.modules.guidebook.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.AdminGuard;
import com.travel.common.Result;
import com.travel.modules.guidebook.data.DataBlock;
import com.travel.modules.guidebook.dto.DataSourceConfigItem;
import com.travel.modules.guidebook.dto.UpdateDataSourceRequest;
import com.travel.modules.guidebook.service.AdminDataService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "运营-实时数据", description = "FR-6：数据源配置与内部查询")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDataController {

    private final AdminGuard adminGuard;
    private final AdminDataService adminDataService;

    @Operation(summary = "数据源配置列表")
    @GetMapping("/data-sources")
    public Result<List<DataSourceConfigItem>> listSources() {
        adminGuard.requireAdmin();
        return Result.success(adminDataService.listSources());
    }

    @Operation(summary = "更新数据源配置")
    @PutMapping("/data-sources/{sourceId}")
    public Result<DataSourceConfigItem> updateSource(@PathVariable String sourceId,
            @RequestBody UpdateDataSourceRequest request) {
        adminGuard.requireAdmin();
        return Result.success(adminDataService.updateSource(sourceId, request));
    }

    @Operation(summary = "天气实时数据（内部）")
    @GetMapping("/data/weather")
    public Result<DataBlock> weather(@RequestParam String destination,
            @RequestParam String date) {
        adminGuard.requireAdmin();
        return Result.success(adminDataService.weather(destination, date));
    }
}
