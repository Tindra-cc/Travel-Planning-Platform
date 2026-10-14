package com.travel.modules.profile.dto;

import java.util.List;

import lombok.Data;

@Data
public class BasicProfile {
    private String budgetLevel;
    private String pace;
    private List<String> interestTags;
    private String dietaryRestrictions;
    private PartyStructure partyStructure;
}
