package com.travel.modules.guidebook.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * §7.8 版本列表条目。
 * 只暴露用户关心的字段：内容不会通过列表返回，避免响应过大；
 * 回溯需要内容时由服务端按版本号读取。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuidebookVersionItem {

    private Integer version;
    private String title;
    /** generate / manual / adjust / restore */
    private String changeType;
    private String changeSummary;
    private LocalDateTime createdAt;
}
