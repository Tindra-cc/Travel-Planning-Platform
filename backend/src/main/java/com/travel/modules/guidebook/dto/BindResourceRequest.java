package com.travel.modules.guidebook.dto;

import lombok.Data;

/** resourceSlotId 为 null 时表示解绑。 */
@Data
public class BindResourceRequest {
    private String resourceSlotId;
}
