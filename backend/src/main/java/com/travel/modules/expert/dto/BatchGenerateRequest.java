package com.travel.modules.expert.dto;

import java.util.List;

import com.travel.modules.chat.dto.IntentDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BatchGenerateRequest {

    @NotNull(message = "intents 不能为空")
    @Size(min = 1, max = 5, message = "一次最多生成 5 本攻略")
    private List<@Valid IntentDto> intents;
}
