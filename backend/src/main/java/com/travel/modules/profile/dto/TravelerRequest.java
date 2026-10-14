package com.travel.modules.profile.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TravelerRequest {

    @NotBlank(message = "姓名不能为空")
    private String name;

    private String idType;
    private String idNumber;
    private LocalDate birthDate;
    private String specialNeeds;
}
