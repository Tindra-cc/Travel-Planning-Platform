package com.travel.modules.guidebook.data;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单个数据源的采集结果。status=unavailable 时 summary/data 为空，
 * 前端据此显示「时效未知」，绝不编造数值。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataBlock(String source, String status, String summary,
        Map<String, Object> data, String fetchedAt) {

    public static final String STATUS_OK = "ok";
    public static final String STATUS_UNAVAILABLE = "unavailable";

    public static DataBlock ok(String source, String summary, Map<String, Object> data,
            String fetchedAt) {
        return new DataBlock(source, STATUS_OK, summary, data, fetchedAt);
    }

    public static DataBlock unavailable(String source, String fetchedAt) {
        return new DataBlock(source, STATUS_UNAVAILABLE, null, null, fetchedAt);
    }

    public boolean available() {
        return STATUS_OK.equals(status);
    }
}
