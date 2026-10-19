package com.travel.modules.guidebook.dto;

import com.travel.modules.chat.dto.IntentDto;

import lombok.Data;

@Data
public class GenerateGuidebookRequest {
    /** 从会话生成时必填；与 intent 二选一。 */
    private Long sessionId;
    /** 直接传入意图时使用（无 sessionId）。 */
    private IntentDto intent;
    /** 是否结合用户画像，默认 true。 */
    private Boolean useProfile;
}
