package com.travel.modules.expert.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BatchTaskResponse {

    /** 即 guidebookId，可直接用于查询进度与重试。 */
    private String taskId;
    /** generating / failed */
    private String status;
}
