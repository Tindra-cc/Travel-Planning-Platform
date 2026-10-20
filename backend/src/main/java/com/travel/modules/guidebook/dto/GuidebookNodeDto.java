package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuidebookNodeDto {
    private String nodeId;
    private TimeSlotDto timeSlot;
    /** activity / meal / transport / stay / free */
    private String type;
    private PlaceDto place;
    private String resourceSlotId;
    /** 溯源依据：AI 理由 + 知识来源编号 */
    private String source;
    /** 实时数据采集时间；未知时为 null */
    private String dataFreshness;
    /** OSM opening_hours 原始值；未查到为 null */
    private String openingHours;
    /** 营业时间来源，目前固定 OSM */
    private String openingHoursSource;
    /** 详情接口按 resourceSlotId 解析出的资源；不随攻略 JSON 落库 */
    private ResourceItemDto resource;
    /** 结构化溯源引用；随节点 JSON 落库，历史版本保留生成时的引用 */
    private List<CitationDto> citations;
    private String note;
}
