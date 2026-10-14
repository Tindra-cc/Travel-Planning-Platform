package com.travel.modules.guide.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 规划模块下的一条推荐（住宿/吃饭/景点/交通 通用）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuidePlanItem {
    private String name;
    /** 一句话点评 */
    private String note;
    /** 价格文字，如「¥420/晚」「人均 ¥80」，非数值 */
    private String price;
}
