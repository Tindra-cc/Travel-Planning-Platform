package com.travel.modules.profile.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProfileCheckResponse {
    private boolean completed;
    private List<String> missingFields;
}
