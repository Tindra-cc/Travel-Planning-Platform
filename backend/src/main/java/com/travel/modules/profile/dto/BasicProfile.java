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
    /** 常规行程天数 */
    private Integer tripDays;
    /** 人均预算金额，单位元 */
    private Integer budgetAmount;
}
