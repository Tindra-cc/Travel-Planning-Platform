package com.travel.modules.guidebook.offline;

import java.util.List;

import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 渲染器与内容组装之间的稳定模型；HTML / PDF 共用。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflinePackContent {

    private Long guidebookId;
    private Integer version;
    private String title;
    private IntentDto intent;
    private List<DayContent> days;
    private BudgetBreakdownDto budget;
    private List<String> packingList;
    private List<String> culturalTips;
    private List<String> pitfallTips;
    private List<Contact> emergencyContacts;
    private String emergencyNote;
    private String disclaimer;
    /** 请求了地图但至少有一天无法获取截图 */
    private boolean mapUnavailable;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayContent {
        private Integer day;
        private List<NodeContent> nodes;
        private byte[] mapImage;
        /** image/png 或 image/jpeg；无图时为 null */
        private String mapImageType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeContent {
        private String start;
        private String end;
        private String type;
        private String placeName;
        private Double lat;
        private Double lng;
        private String note;
        private String source;
        private String dataFreshness;
        private String openingHours;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Contact {
        private String label;
        private String number;
    }
}
