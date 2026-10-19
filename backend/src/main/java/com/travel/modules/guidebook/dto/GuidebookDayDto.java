package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuidebookDayDto {
    private Integer day;
    private List<GuidebookNodeDto> nodes;
}
