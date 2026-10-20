package com.travel.modules.guidebook.offline;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "app.offline-pack")
public class OfflinePackProperties {

    private String storageDir = "./data/offline-packs";
    private int expireHours = 72;
    private long maxFileMb = 20;
    /** 静态地图模板；{lat}/{lng} 由每日节点质心替换。留空则跳过地图截图。 */
    private String mapUrlTemplate =
            "https://staticmap.openstreetmap.de/staticmap.php?center={lat},{lng}&zoom=13&size=640x360&maptype=mapnik";
    private int mapTimeoutSeconds = 5;
    /** 启动时超过该分钟数仍为 processing 的任务直接置为 failed。 */
    private int staleMinutes = 10;
}
