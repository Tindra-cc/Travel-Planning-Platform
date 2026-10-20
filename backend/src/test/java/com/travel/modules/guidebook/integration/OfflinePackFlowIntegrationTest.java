package com.travel.modules.guidebook.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.travel.modules.guidebook.dto.OfflinePackRequest;
import com.travel.modules.guidebook.dto.OfflinePackStatusResponse;
import com.travel.modules.guidebook.dto.OfflinePackTaskResponse;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.offline.OfflinePackService;

/** FR-9：版本快照 → 异步渲染 → PDF/HTML 下载的完整链路。 */
@Tag("integration")
class OfflinePackFlowIntegrationTest extends IntegrationFlowTestSupport {

    @Autowired
    private OfflinePackService offlinePackService;

    @Test
    void generatesPdfAndHtmlFromVersionSnapshot() throws Exception {
        Guidebook guidebook = createGuidebook(Guidebook.STATUS_DRAFT, true);
        versionService.append(guidebook, GuidebookVersion.CHANGE_TYPE_GENERATE, "集成测试", userId);

        OfflinePackRequest pdfRequest = new OfflinePackRequest();
        pdfRequest.setFormat("pdf");
        pdfRequest.setIncludeMaps(false);

        OfflinePackTaskResponse task = offlinePackService.request(guidebook.getId(), pdfRequest);
        OfflinePackStatusResponse status = awaitTerminal(task.getTaskId());
        assertThat(status.getStatus()).isEqualTo("completed");
        assertThat(status.getDownloadUrl())
                .isEqualTo("/api/offline-packs/" + task.getTaskId() + "/download");

        byte[] pdf = Files.readAllBytes(offlinePackService.download(task.getTaskId()).path());
        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        // 同攻略 + 版本 + 格式：复用未过期任务，不重复渲染
        OfflinePackTaskResponse reused = offlinePackService.request(guidebook.getId(), pdfRequest);
        assertThat(reused.getTaskId()).isEqualTo(task.getTaskId());
        assertThat(reused.getStatus()).isEqualTo("completed");

        OfflinePackRequest htmlRequest = new OfflinePackRequest();
        htmlRequest.setFormat("html");
        htmlRequest.setIncludeMaps(false);
        OfflinePackTaskResponse htmlTask = offlinePackService.request(guidebook.getId(), htmlRequest);
        OfflinePackStatusResponse htmlStatus = awaitTerminal(htmlTask.getTaskId());
        assertThat(htmlStatus.getStatus()).isEqualTo("completed");

        String html = Files.readString(
                offlinePackService.download(htmlTask.getTaskId()).path(), StandardCharsets.UTF_8);
        assertThat(html).contains("宽窄巷子");
        assertThat(html).contains("紧急");
        assertThat(html).doesNotContain("<img");
        assertThat(html).doesNotContain("src=\"http");
    }

    private OfflinePackStatusResponse awaitTerminal(Long taskId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
        OfflinePackStatusResponse status = offlinePackService.status(taskId);
        while (System.currentTimeMillis() < deadline && "processing".equals(status.getStatus())) {
            Thread.sleep(200);
            status = offlinePackService.status(taskId);
        }
        return status;
    }
}
