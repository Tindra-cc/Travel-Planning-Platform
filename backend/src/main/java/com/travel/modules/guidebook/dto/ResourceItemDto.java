package com.travel.modules.guidebook.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 资源条目；bookingLink 由服务端签名生成，前端不得自行拼接。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceItemDto {
    private String resourceSlotId;
    private String type;
    private String supplier;
    private PriceRange priceRange;
    private String availability;
    private String refundPolicy;
    private String bookingLink;
    private LocalDateTime freshness;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceRange {
        private BigDecimal min;
        private BigDecimal max;
        private String currency;
    }
}
