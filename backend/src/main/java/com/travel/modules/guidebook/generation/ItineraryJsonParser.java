package com.travel.modules.guidebook.generation;

import com.fasterxml.jackson.databind.ObjectMapper;

/** 从模型输出中提取并解析攻略 JSON；格式不合法时抛 IllegalArgumentException。 */
public final class ItineraryJsonParser {

    private ItineraryJsonParser() {
    }

    public static GeneratedContent parse(ObjectMapper mapper, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("模型输出为空");
        }
        String json = extractJson(raw);
        try {
            GeneratedContent content = mapper.readValue(json, GeneratedContent.class);
            if (content == null || content.getDays() == null || content.getDays().isEmpty()) {
                throw new IllegalArgumentException("缺少 days");
            }
            return content;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage(), e);
        }
    }

    static String extractJson(String raw) {
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("未找到 JSON 对象");
        }
        return trimmed.substring(start, end + 1);
    }
}
