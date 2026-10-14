package com.travel.modules.guide.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 规划的一个模块（住宿 / 吃饭 / 景点 / 交通）。type 为渲染用的图标键，title 为显示名。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuidePlanSection {
    /** stay / food / spot / transport；前端按此选图标，未知值走默认图标 */
    private String type;
    private String title;
    private List<GuidePlanItem> items;
}
