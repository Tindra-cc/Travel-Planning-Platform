package com.travel.modules.guidebook.data;

/** 逐日天气；规则引擎只读这个结构，不再解析 summary 文案。 */
public record WeatherDay(String date, int code, String text, double min, double max) {
}
