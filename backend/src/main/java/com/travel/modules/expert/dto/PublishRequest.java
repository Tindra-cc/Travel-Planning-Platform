package com.travel.modules.expert.dto;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublishRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 120, message = "标题不能超过 120 字")
    private String title;

    @Size(max = 500, message = "描述不能超过 500 字")
    private String description;

    @Size(max = 500, message = "封面地址过长")
    private String coverUrl;

    @Min(value = 0, message = "价格不能为负数")
    private Integer priceCents;

    @Size(max = 10, message = "标签最多 10 个")
    private List<String> tags;
}
