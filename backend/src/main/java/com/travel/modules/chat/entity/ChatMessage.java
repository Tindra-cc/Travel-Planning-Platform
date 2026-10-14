package com.travel.modules.chat.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.modules.chat.dto.PendingQuestion;

import lombok.Data;

/**
 * 对话消息只追加不修改，故不继承 BaseEntity（无 update_time / deleted）。
 */
@Data
@TableName(value = "t_chat_message", autoResultMap = true)
public class ChatMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private String role;
    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<PendingQuestion> pendingQuestions;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
