package com.travel.modules.chat.dto;

import java.util.List;

import lombok.Data;

/**
 * AI 意图分析结果（IntentService 内部使用）。
 */
@Data
public class IntentResult {
    /** 面向用户的自然语言回复 */
    private String reply;
    private IntentDto intent;
    private List<String> missingFields;
    private List<PendingQuestion> questions;
}
