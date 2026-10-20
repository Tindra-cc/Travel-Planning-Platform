package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddNodeRequest {

    // 插入位置；不传则追加到当天末尾
    private Integer index;
    //activity/meal/transport/stay/free
    private String type;
    private TimeSlotDto timeSlot;
    private PlaceDto place;
    private String note;
}
