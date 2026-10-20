package com.travel.modules.guidebook.offline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.travel.modules.guidebook.dto.BudgetBreakdownDto;

class PdfPackRendererTest {

    private final PdfPackRenderer renderer = new PdfPackRenderer();

    @Test
    void rendersChinesePdfWithWrappedLongText() {
        OfflinePackContent content = OfflinePackContent.builder()
                .title("成都三日深度游")
                .version(2)
                .days(List.of(OfflinePackContent.DayContent.builder()
                        .day(1)
                        .nodes(List.of(OfflinePackContent.NodeContent.builder()
                                .start("09:00")
                                .end("11:00")
                                .type("activity")
                                .placeName("宽窄巷子与人民公园的早晨漫步（含一段很长很长的地点名称用于验证自动换行）")
                                .note("早点去人少，顺便吃碗担担面。")
                                .source("[K1] 内置资料")
                                .lat(30.67)
                                .lng(104.06)
                                .build()))
                        .build()))
                .budget(new BudgetBreakdownDto("CNY", List.of(new BudgetBreakdownDto.Item(
                        "交通", new java.math.BigDecimal("100"), new java.math.BigDecimal("200"))),
                        new BudgetBreakdownDto.Total(new java.math.BigDecimal("100"),
                                new java.math.BigDecimal("200"))))
                .packingList(List.of("雨具"))
                .emergencyContacts(List.of(new OfflinePackContent.Contact("急救", "120")))
                .disclaimer("攻略由 AI 辅助生成，请出行前核实。")
                .build();

        byte[] pdf = renderer.render(content);

        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
