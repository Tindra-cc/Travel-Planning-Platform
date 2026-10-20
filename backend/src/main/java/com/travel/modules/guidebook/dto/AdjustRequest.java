package com.travel.modules.guidebook.dto;

import lombok.Data;

/**
 * §7.5 对话式调整请求。
 */
@Data
public class AdjustRequest {

    /** 用户的自然语言调整指令，例如「Day3 太赶了，帮我调整」。 */
    private String instruction;
}
