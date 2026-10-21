package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 参考节点只保留时间、类型与地点名；不带来源、坐标与资源绑定。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceNode {
    private String start;
    /** activity / meal / transport / stay / free */
    private String type;
    private String placeName;
}
