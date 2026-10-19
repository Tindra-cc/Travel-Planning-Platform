package com.travel.modules.guidebook.dto;

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
    private String note;
}
