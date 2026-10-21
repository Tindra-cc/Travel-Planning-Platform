package com.travel.modules.guidebook.edit;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_guidebook_node_status")
public class GuidebookNodeStatus extends  BaseEntity{

    public static final String EXECUTED = "executed";
    public static final String SKIPPED = "skipped";
    public static final String RESCHEDULED = "rescheduled";

    private Long guidebookId;
    private String nodeId;
    private Integer nodeDay;
    private String nodeName;
    private String status;
    private LocalDateTime markedAt;
}
