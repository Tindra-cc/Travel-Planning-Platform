package com.travel.modules.guidebook.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_offline_pack")
public class OfflinePack extends BaseEntity {

    public static final String FORMAT_PDF = "pdf";
    public static final String FORMAT_HTML = "html";

    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    private Long guidebookId;
    private Integer guidebookVersion;
    private Long userId;
    private String format;
    private Boolean includeMaps;
    private String status;
    private String filePath;
    private Long fileSize;
    private String errorMessage;
    private LocalDateTime expiresAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
