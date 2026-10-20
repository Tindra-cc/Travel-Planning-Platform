package com.travel.modules.guidebook.dto;

import lombok.Data;

/** §9.1 生成离线包请求。 */
@Data
public class OfflinePackRequest {

    /** pdf / html，默认 pdf。 */
    private String format;
    /** 是否包含每日静态地图截图，默认 true。 */
    private Boolean includeMaps;
}
