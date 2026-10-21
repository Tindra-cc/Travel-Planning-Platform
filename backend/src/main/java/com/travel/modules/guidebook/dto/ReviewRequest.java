package com.travel.modules.guidebook.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class ReviewRequest {

    @NotNull(message = "guidebookId 不能为空")
    private Long guidebookId;

    @NotNull(message = "总体评分不能为空")
    @Min(value = 1, message = "总体评分需在 1-5 之间")
    @Max(value = 5, message = "总体评分需在 1-5 之间")
    private Integer overallScore;

    @Min(value = 1, message = "AI 准确度评分需在 1-5 之间")
    @Max(value = 5, message = "AI 准确度评分需在 1-5 之间")
    private Integer aiAccuracyScore;

    @Min(value = 1, message = "执行贴合度评分需在 1-5 之间")
    @Max(value = 5, message = "执行贴合度评分需在 1-5 之间")
    private Integer nodeExecutionScore;

    @Size(max = 1000, message = "评价内容不能超过 1000 字")
    private String content;

    @Size(max = 9, message = "最多上传 9 张图片")
    private List<@Size(max = 128, message = "图片标识过长") String> images;
}
