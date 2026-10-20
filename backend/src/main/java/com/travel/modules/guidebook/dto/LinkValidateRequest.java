package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.Data;

@Data
public class LinkValidateRequest {
    private List<String> resourceSlotIds;
}
