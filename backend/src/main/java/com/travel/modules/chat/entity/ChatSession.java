package com.travel.modules.chat.entity;

import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.chat.dto.PendingQuestion;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_chat_session", autoResultMap = true)
public class ChatSession extends BaseEntity {

    public static final String STATUS_COLLECTING = "collecting";
    public static final String STATUS_READY = "ready_to_generate";
    public static final String STATUS_GENERATING = "generating";

    private Long userId;
    private String title;
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private IntentDto intent;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<PendingQuestion> pendingQuestions;

    private Long guidebookId;
}
