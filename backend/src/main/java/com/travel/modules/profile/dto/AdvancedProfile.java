package com.travel.modules.profile.dto;

import java.util.List;

import lombok.Data;

@Data
public class AdvancedProfile {
    private List<String> specialPreferences;
    private Boolean acceptRedEye;
    private String transportPreference;
    private String accommodationPreference;
}
