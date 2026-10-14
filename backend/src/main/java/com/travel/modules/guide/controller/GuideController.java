package com.travel.modules.guide.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.PageResult;
import com.travel.common.Result;
import com.travel.modules.guide.dto.GuidePostItem;
import com.travel.modules.guide.service.GuideService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "攻略推荐", description = "推荐攻略流：已发布市场攻略的消费侧（对应 §13.5 发布攻略到市场）")
@RestController
@RequestMapping("/api/guides")
@RequiredArgsConstructor
public class GuideController {

    private final GuideService guideService;

    @Operation(summary = "推荐攻略流（分页）")
    @GetMapping("/recommend")
    public Result<PageResult<GuidePostItem>> recommend(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "12") long pageSize) {
        return Result.success(guideService.recommend(page, pageSize));
    }
}
