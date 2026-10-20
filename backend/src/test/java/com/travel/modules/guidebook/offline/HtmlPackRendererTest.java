package com.travel.modules.guidebook.offline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.travel.modules.guidebook.dto.BudgetBreakdownDto;

class HtmlPackRendererTest {

    private final HtmlPackRenderer renderer = new HtmlPackRenderer();

    @Test
    void rendersSelfContainedHtmlWithInlineMapAndEscapedText() {
        OfflinePackContent.DayContent day = OfflinePackContent.DayContent.builder()
                .day(1)
                .mapImage(new byte[] {1, 2, 3})
                .mapImageType("image/png")
                .nodes(List.of(OfflinePackContent.NodeContent.builder()
                        .start("09:00")
                        .end("11:00")
                        .type("activity")
                        .placeName("<script>alert(1)</script>")
                        .lat(30.67)
                        .lng(104.06)
                        .note("记得预约 & 带伞")
                        .source("[K1] 内置资料")
                        .build()))
                .build();
        OfflinePackContent content = OfflinePackContent.builder()
                .title("成都三日")
                .version(2)
                .days(List.of(day))
                .budget(new BudgetBreakdownDto())
                .packingList(List.of("雨具"))
                .emergencyContacts(List.of(new OfflinePackContent.Contact("报警", "110")))
                .emergencyNote("以官方信息为准")
                .disclaimer("仅供出行参考")
                .build();

        String html = renderer.render(content);

        assertThat(html).startsWith("<!doctype html>");
        assertThat(html).contains("data:image/png;base64,");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("记得预约 &amp; 带伞");
        assertThat(html).contains("第 1 天");
        assertThat(html).contains("报警");
        assertThat(html).doesNotContain("http://");
        assertThat(html).doesNotContain("https://");
        assertThat(new String(html.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                .isEqualTo(html);
    }

    @Test
    void warnsWhenMapIsUnavailable() {
        OfflinePackContent content = OfflinePackContent.builder()
                .title("成都三日")
                .version(1)
                .days(List.of(OfflinePackContent.DayContent.builder()
                        .day(1)
                        .nodes(List.of())
                        .build()))
                .mapUnavailable(true)
                .build();

        String html = renderer.render(content);

        assertThat(html).contains("地图截图暂不可用");
        assertThat(html).contains("本日暂无安排");
    }
}
