package com.travel.modules.guidebook.offline;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;

import lombok.extern.slf4j.Slf4j;

/**
 * 离线 PDF：内置中文字体子集嵌入，手动换行与分页（PDFBox 不自动排版）。
 * 任何一段内容过长都不会抛异常，最坏情况是多几页。
 */
@Slf4j
@Component
public class PdfPackRenderer {

    private static final String FONT_PATH = "fonts/NotoSansSC-Regular.ttf";
    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;
    private static final float LEADING = 6f;

    public byte[] render(OfflinePackContent content) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType0Font font = loadFont(doc);
            Writer w = new Writer(doc, font);
            writeTitle(w, content);
            writeDays(w, content);
            writeBudget(w, content.getBudget());
            writeTips(w, "行前准备", content.getPackingList());
            writeTips(w, "文化贴士", content.getCulturalTips());
            writeTips(w, "避坑提示", content.getPitfallTips());
            writeEmergency(w, content);
            if (StringUtils.hasText(content.getDisclaimer())) {
                w.paragraph(content.getDisclaimer(), 9f, 0x94, 0xa3, 0xb8);
            }
            w.close();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("离线 PDF 渲染失败", e);
            throw new IllegalStateException("离线 PDF 渲染失败", e);
        }
    }

    private PDType0Font loadFont(PDDocument doc) throws IOException {
        try (InputStream in = new ClassPathResource(FONT_PATH).getInputStream()) {
            return PDType0Font.load(doc, in, true);
        }
    }

    private void writeTitle(Writer w, OfflinePackContent content) throws IOException {
        w.heading1(content.getTitle());
        StringBuilder meta = new StringBuilder();
        IntentDto intent = content.getIntent();
        if (intent != null) {
            if (StringUtils.hasText(intent.getDestination())) {
                meta.append("目的地：").append(intent.getDestination()).append(" · ");
            }
            if (intent.getDateWindow() != null) {
                meta.append(intent.getDateWindow().getStart()).append(" ~ ")
                        .append(intent.getDateWindow().getEnd()).append(" · ");
            }
            if (intent.getParty() != null && intent.getParty().getAdults() != null) {
                meta.append("成人 ").append(intent.getParty().getAdults()).append(" 人 · ");
            }
            if (intent.getBudget() != null) {
                meta.append("预算 ").append(intent.getBudget().getAmount())
                        .append(' ').append(intent.getBudget().getCurrency()).append(" · ");
            }
        }
        meta.append("版本 v").append(content.getVersion() == null ? "?" : content.getVersion());
        w.paragraph(meta.toString(), 10f, 0x64, 0x74, 0x8b);
        if (content.isMapUnavailable()) {
            w.paragraph("部分日期地图截图暂不可用，已保留地点与坐标，请联网后核实。", 10f, 0xb4, 0x53, 0x09);
        }
    }

    private void writeDays(Writer w, OfflinePackContent content) throws IOException {
        List<OfflinePackContent.DayContent> days = content.getDays();
        if (days == null) {
            return;
        }
        for (OfflinePackContent.DayContent day : days) {
            w.heading2("第 " + (day.getDay() == null ? "" : day.getDay()) + " 天");
            writeMap(w, day);
            if (day.getNodes() == null || day.getNodes().isEmpty()) {
                w.paragraph("本日暂无安排", 10f, 0x94, 0xa3, 0xb8);
                continue;
            }
            for (OfflinePackContent.NodeContent node : day.getNodes()) {
                writeNode(w, node);
            }
        }
    }

    private void writeMap(Writer w, OfflinePackContent.DayContent day) throws IOException {
        if (day.getMapImage() == null) {
            return;
        }
        try {
            PDImageXObject image = PDImageXObject.createFromByteArray(
                    w.doc, day.getMapImage(), "map-" + day.getDay());
            float maxW = CONTENT_WIDTH;
            float maxH = 220f;
            float scale = Math.min(maxW / image.getWidth(), maxH / image.getHeight());
            float drawW = image.getWidth() * scale;
            float drawH = image.getHeight() * scale;
            w.ensureSpace(drawH + LEADING);
            w.stream.drawImage(image, MARGIN, w.y - drawH, drawW, drawH);
            w.y -= drawH + LEADING;
        } catch (IOException e) {
            log.info("PDF 内嵌地图失败 day={}: {}", day.getDay(), e.getMessage());
            w.paragraph("（地图图片无法嵌入，请参考坐标）", 9f, 0x94, 0xa3, 0xb8);
        }
    }

    private void writeNode(Writer w, OfflinePackContent.NodeContent node) throws IOException {
        String place = StringUtils.hasText(node.getPlaceName()) ? node.getPlaceName() : "待定地点";
        String time = timeRange(node);
        w.paragraphBold(place + "   " + time, 12f, 0x1f, 0x29, 0x37);

        StringBuilder sub = new StringBuilder(typeLabel(node.getType()));
        if (StringUtils.hasText(node.getOpeningHours())) {
            sub.append(" · 营业时间 ").append(node.getOpeningHours());
        }
        w.paragraph(sub.toString(), 9f, 0x64, 0x74, 0x8b);
        if (StringUtils.hasText(node.getNote())) {
            w.paragraph(node.getNote(), 10f, 0x33, 0x41, 0x55);
        }
        StringBuilder src = new StringBuilder("来源：")
                .append(StringUtils.hasText(node.getSource()) ? node.getSource() : "通用知识");
        if (node.getLat() != null && node.getLng() != null) {
            src.append(" · 坐标 ").append(String.format("%.5f, %.5f", node.getLat(), node.getLng()));
        }
        if (StringUtils.hasText(node.getDataFreshness())) {
            src.append(" · 数据时间 ").append(node.getDataFreshness());
        }
        w.paragraph(src.toString(), 8f, 0x94, 0xa3, 0xb8);
        w.spacer(4f);
    }

    private void writeBudget(Writer w, BudgetBreakdownDto budget) throws IOException {
        if (budget == null || budget.getItems() == null || budget.getItems().isEmpty()) {
            return;
        }
        w.heading2("预算拆解");
        float[][] widths = {{0.5f}, {0.25f}, {0.25f}};
        w.tableRow(widths, new String[] {"类别", "最低", "最高"}, 10f, 0x33, 0x41, 0x55);
        for (BudgetBreakdownDto.Item item : budget.getItems()) {
            w.tableRow(widths, new String[] {
                    item.getCategory(), String.valueOf(item.getMin()), String.valueOf(item.getMax())},
                    10f, 0x33, 0x41, 0x55);
        }
        if (budget.getTotal() != null) {
            w.tableRow(widths, new String[] {
                    "合计", String.valueOf(budget.getTotal().getMin()),
                    String.valueOf(budget.getTotal().getMax())}, 10f, 0x1f, 0x29, 0x37);
        }
        if (StringUtils.hasText(budget.getCurrency())) {
            w.paragraph("币种：" + budget.getCurrency(), 9f, 0x94, 0xa3, 0xb8);
        }
    }

    private void writeTips(Writer w, String title, List<String> tips) throws IOException {
        if (tips == null || tips.isEmpty()) {
            return;
        }
        w.heading2(title);
        for (String tip : tips) {
            w.paragraph("· " + tip, 10f, 0x33, 0x41, 0x55);
        }
    }

    private void writeEmergency(Writer w, OfflinePackContent content) throws IOException {
        w.heading2("紧急信息");
        if (content.getEmergencyContacts() != null) {
            for (OfflinePackContent.Contact contact : content.getEmergencyContacts()) {
                w.paragraph(contact.getLabel() + "：" + contact.getNumber(), 10f, 0x33, 0x41, 0x55);
            }
        }
        if (StringUtils.hasText(content.getEmergencyNote())) {
            w.paragraph(content.getEmergencyNote(), 9f, 0x94, 0xa3, 0xb8);
        }
    }

    private String timeRange(OfflinePackContent.NodeContent node) {
        if (!StringUtils.hasText(node.getStart()) && !StringUtils.hasText(node.getEnd())) {
            return "时间待定";
        }
        return (StringUtils.hasText(node.getStart()) ? node.getStart() : "?")
                + "-" + (StringUtils.hasText(node.getEnd()) ? node.getEnd() : "?");
    }

    private String typeLabel(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "activity" -> "活动";
            case "meal" -> "餐饮";
            case "transport" -> "交通";
            case "stay" -> "住宿";
            case "free" -> "自由";
            default -> type;
        };
    }

    /** 维护当前页与 y 坐标的极简排版器。 */
    private static final class Writer {

        private final PDDocument doc;
        private final PDType0Font font;
        private PDPageContentStream stream;
        private float y;

        Writer(PDDocument doc, PDType0Font font) throws IOException {
            this.doc = doc;
            this.font = font;
            newPage();
        }

        private void newPage() throws IOException {
            closeStream();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        void close() throws IOException {
            closeStream();
        }

        void ensureSpace(float height) throws IOException {
            if (y - height < MARGIN) {
                newPage();
            }
        }

        void spacer(float height) {
            y -= height;
        }

        void heading1(String text) throws IOException {
            paragraphBold(text, 20f, 0x1f, 0x29, 0x37);
            spacer(2f);
        }

        void heading2(String text) throws IOException {
            ensureSpace(28f);
            spacer(6f);
            paragraphBold(text, 14f, 0x1f, 0x29, 0x37);
        }

        void paragraph(String text, float size, int r, int g, int b) throws IOException {
            writeWrapped(text, font, size, r, g, b);
        }

        void paragraphBold(String text, float size, int r, int g, int b) throws IOException {
            // 内置字体只有一个字重；用字号与颜色区分层级，避免依赖不存在的粗体字形。
            writeWrapped(text, font, size, r, g, b);
        }

        void tableRow(float[][] widths, String[] cells, float size, int r, int g, int b)
                throws IOException {
            float maxLines = 1;
            List<List<String>> wrapped = new ArrayList<>();
            for (int i = 0; i < cells.length; i++) {
                List<String> lines = wrap(cells[i] == null ? "" : cells[i], font, size,
                        CONTENT_WIDTH * widths[i][0] - 8f);
                wrapped.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }
            float rowHeight = maxLines * (size + LEADING) + 4f;
            ensureSpace(rowHeight);
            float x = MARGIN;
            for (int i = 0; i < cells.length; i++) {
                float cellW = CONTENT_WIDTH * widths[i][0];
                float lineY = y;
                for (String line : wrapped.get(i)) {
                    drawText(line, x + 4f, lineY, size, r, g, b);
                    lineY -= size + LEADING;
                }
                x += cellW;
            }
            y -= rowHeight;
        }

        private void writeWrapped(String text, PDType0Font font, float size, int r, int g, int b)
                throws IOException {
            if (text == null || text.isEmpty()) {
                return;
            }
            List<String> lines = wrap(text, font, size, CONTENT_WIDTH);
            for (String line : lines) {
                ensureSpace(size + LEADING);
                drawText(line, MARGIN, y, size, r, g, b);
                y -= size + LEADING;
            }
        }

        private void drawText(String line, float x, float y, float size, int r, int g, int b)
                throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(r, g, b);
            stream.newLineAtOffset(x, y);
            stream.showText(line);
            stream.endText();
        }

        private List<String> wrap(String text, PDType0Font font, float size, float maxWidth)
                throws IOException {
            List<String> lines = new ArrayList<>();
            if (text == null || text.isEmpty()) {
                lines.add("");
                return lines;
            }
            for (String raw : text.split("\n", -1)) {
                StringBuilder line = new StringBuilder();
                float width = 0;
                for (int i = 0; i < raw.length(); i++) {
                    char c = raw.charAt(i);
                    float charWidth = widthOf(String.valueOf(c), font, size);
                    if (width + charWidth > maxWidth && line.length() > 0) {
                        lines.add(line.toString());
                        line.setLength(0);
                        width = 0;
                    }
                    line.append(c);
                    width += charWidth;
                }
                lines.add(line.toString());
            }
            return lines;
        }

        private float widthOf(String text, PDType0Font font, float size) {
            try {
                return font.getStringWidth(text) / 1000f * size;
            } catch (IOException e) {
                return size * 0.5f;
            }
        }
    }
}
