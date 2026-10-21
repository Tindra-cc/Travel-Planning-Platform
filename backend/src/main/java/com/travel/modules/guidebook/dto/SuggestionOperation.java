package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionOperation {

    public static final String MOVE_NODE = "move_node";
    public static final String SWAP_NODE = "swap_node";

    private String op;
    private String nodeId;
    private Integer toDay;
    private TimeSlotDto timeSlot;
    /** swap_node 使用 */
    private String nodeB;
}
