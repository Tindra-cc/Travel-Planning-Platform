package com.travel.modules.guidebook.dto;

import lombok.Data;

@Data
public class ReviewGuidePostRequest {
    /** true 通过并入库；false 驳回 */
    private boolean approved;
}
