package com.travel.modules.guidebook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FreshnessDiffItem(
        String nodeId,
        @JsonProperty("old") String oldValue,
        @JsonProperty("new") String newValue) {
}
