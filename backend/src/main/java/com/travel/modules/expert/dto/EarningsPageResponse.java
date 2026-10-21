package com.travel.modules.expert.dto;

import java.util.List;

import lombok.Data;

@Data
public class EarningsPageResponse {

    private List<EarningItem> list;
    private long page;
    private long pageSize;
    private long total;
    /** 当前环境是否具备支付/结算链路；本期恒为 false。 */
    private boolean settlementAvailable;
}
