package com.travel.modules.guidebook.companion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 每 15 分钟扫描一次出行中的攻略；刷新接口仍可手动触发。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.trip-companion", name = "scheduling-enabled",
        havingValue = "true", matchIfMissing = true)
public class TripCompanionScheduler {

    private final TripCompanionScanService scanService;

    @Scheduled(cron = "${app.trip-companion.scan-cron:0 */15 * * * *}")
    public void scan() {
        try {
            int created = scanService.scanAll();
            if (created > 0) {
                log.info("行程伴侣扫描完成，新增建议 {} 条", created);
            }
        } catch (Exception e) {
            log.warn("行程伴侣定时扫描失败: {}", e.getMessage());
        }
    }
}
