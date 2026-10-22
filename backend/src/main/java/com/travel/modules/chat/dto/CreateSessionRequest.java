package com.travel.modules.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSessionRequest {

    @NotBlank(message = "首条消息不能为空")
    private String firstMessage;

    /** 从推荐攻略点「按这个生成我的行程」时传入，让模型看到推荐内容。 */
    private Long referencePostId;
}
