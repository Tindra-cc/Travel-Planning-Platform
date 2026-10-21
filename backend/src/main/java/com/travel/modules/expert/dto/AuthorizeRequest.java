package com.travel.modules.expert.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class AuthorizeRequest {

    @NotEmpty(message = "scope 不能为空")
    private List<String> scope;
}
