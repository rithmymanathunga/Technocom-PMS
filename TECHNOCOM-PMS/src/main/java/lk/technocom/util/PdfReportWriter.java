package lk.technocom.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Paginated A4-landscape PDF report writer. Uses an optional configured Unicode TTF font. */
public final class PdfReportWriter {
    private static final float MARGIN =  thirty();
    private static final float TITLE_SIZE = 15f;
    private static final float BODY_SIZE = 7f;
    private static final float ROW_HEIGHT = 25f;
    private PdfReportWriter() {}
    private static float thirty() { return 30f; }

    public static void write(Path target, String title, List<String> headers,
                             List<List<String>> rows, List<String> filters) throws IOException {
        if (target == null || headers == null || headers.isEmpty()) throw new IllegalArgumentException("A report needs at least one column.");
        try (PDDocument doc = new PDDocument()) {
            PDFont regular = loadFont(doc);
            PDFont bold = regular;
            PDRectangle pageSize = PDRectangle.A4;
            float pageW = pageSize.getWidth(), pageH = pageSize.getHeight();
            float usableW = pageW - 2 * MARGIN;
            float colW = usableW / headers.size();
            int pageNo = 0;
            PDPageContentStream cs = null;
            float y = 0;
            List<List<String>> headerCells = new ArrayList<>();
            for (String h : headers) headerCells.add(wrap(clean(h), regular, BODY_SIZE, Math.max(18f, colW - 8f)));
            float headerHeight = 27f;
            try {
                for (List<String> sourceRow : rows) {
                    List<String> cells = safeRow(sourceRow, headers.size());
                    List<List<String>> wrapped = new ArrayList<>();
                    int lines = 1;
                    for (String cell : cells) {
                        List<String> parts = wrap(clean(cell), regular, BODY_SIZE, Math.max(18f, colW - 8f));
                        wrapped.add(parts);
                        lines = Math.max(lines, parts.size());
                    }
                    float height = Math.max(ROW_HEIGHT, lines * 9f + 8f);
                    if (cs == null || y - height < MARGIN) {
                        if (cs != null) cs.close();
                        PDPage page = new PDPage(pageSize);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        pageNo++;
                        y = pageH - MARGIN;
                        y = drawTitle(cs, regular, title, filters, pageNo, pageW, y);
                        drawHeader(cs, headerCells, headers.size(), colW, pageW, y, headerHeight, regular);
                        y -= headerHeight;
                    }
                    drawRow(cs, wrapped, headers.size(), colW, pageW, y, height, false, regular);
                    y -= height;
                }
                if (cs == null) {
                    PDPage page = new PDPage(pageSize); doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    pageNo++;
                    y = drawTitle(cs, regular, title, filters, pageNo, pageW, pageH - MARGIN);
                    drawHeader(cs, headerCells, headers.size(), colW, pageW, y, headerHeight, regular);
                }
            } finally {
                if (cs != null) cs.close();
            }
            if (doc.getNumberOfPages() == 0) {
                PDPage blank = new PDPage(pageSize); doc.addPage(blank);
            }
            doc.save(target.toFile());
        }
    }

    private static PDFont loadFont(PDDocument doc) throws IOException {
        String configured = System.getenv("TECHNOCOM_PDF_FONT");
        if (configured != null && !configured.isBlank()) {
            Path p = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isRegularFile(p)) return PDType0Font.load(doc, p.toFile());
        }
        String[] candidates = {
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "/System/Library/Fonts/Supplemental/Arial.ttf"
        };
        for (String candidate : candidates) {
            Path p = Path.of(candidate);
            if (Files.isRegularFile(p)) return PDType0Font.load(doc, p.toFile());
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private static float drawTitle(PDPageContentStream cs, PDFont font, String title, List<String> filters,
                                   int pageNo, float pageW, float y) throws IOException {
        text(cs, font, TITLE_SIZE, MARGIN, y, clean(title));
        text(cs, font, 7f, pageW - MARGIN - 42f, y + 2f, "Page " + pageNo);
        y -= 17f;
        if (filters != null && !filters.isEmpty()) {
            text(cs, font, 7f, MARGIN, y, clean(String.join("   |   ", filters)));
            y -= 14f;
        }
        return y;
    }

    private static void drawHeader(PDPageContentStream cs, List<List<String>> wrapped, int count,
                                   float colW, float pageW, float y, float height, PDFont font) throws IOException {
        cs.setNonStrokingColor(22, 75, 58);
        cs.addRect(MARGIN, y - height, pageW - 2 * MARGIN, height);
        cs.fill();
        drawRow(cs, wrapped, count, colW, pageW, y, height, true, font);
    }

    private static void drawRow(PDPageContentStream cs, List<List<String>> cells, int count,
                                float colW, float pageW, float y, float height, boolean header,
                                PDFont font) throws IOException {
        if (!header) {
            cs.setStrokingColor(205, 211, 207);
            cs.addRect(MARGIN, y - height, pageW - 2 * MARGIN, height);
            cs.stroke();
        }
        for (int c = 0; c < count; c++) {
            float x = MARGIN + c * colW;
            if (c > 0) {
                cs.setStrokingColor(220, 224, 221);
                cs.moveTo(x, y); cs.lineTo(x, y - height); cs.stroke();
            }
            List<String> lines = cells.get(c);
            float baseline = y - 11f;
            for (int l = 0; l < lines.size() && baseline > y - height + 3; l++) {
                if (header) cs.setNonStrokingColor(255, 255, 255);
                else cs.setNonStrokingColor(35, 42, 39);
                text(cs, font, BODY_SIZE, x + 4f, baseline, lines.get(l));
                baseline -= 9f;
            }
        }
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String value) throws IOException {
        cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y);
        try { cs.showText(value); } catch (IllegalArgumentException unsupported) { cs.showText(ascii(value)); }
        cs.endText();
    }

    private static List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
        String[] words = value.split("\\s+");
        List<String> lines = new ArrayList<>(); StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            float width;
            try { width = font.getStringWidth(candidate) / 1000f * size; }
            catch (IllegalArgumentException unsupported) { candidate = ascii(candidate); width = font.getStringWidth(candidate) / 1000f * size; }
            if (width > maxWidth && line.length() > 0) { lines.add(line.toString()); line.setLength(0); line.append(word); }
            else { if (line.length() > 0) line.append(' '); line.append(word); }
        }
        if (line.length() > 0) lines.add(line.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private static List<String> safeRow(List<String> row, int size) {
        List<String> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(i < row.size() ? row.get(i) : "");
        return result;
    }
    private static String clean(String value) { return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim(); }
    private static String ascii(String value) { return value.replaceAll("[^\\x20-\\x7E]", "?"); }
}
