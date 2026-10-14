package com.travel.modules.profile.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TravelerResponse {
    private Long travelerId;
    private String name;
    private String idType;
    private String idNumber;
    private LocalDate birthDate;
    private String specialNeeds;
}
