package lk.technocom.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Dependency-free XLSX writer. Report values are emitted as inline text, never formulas. */
public final class XlsxWorkbookWriter {
    private XlsxWorkbookWriter() {}

    public static void write(Path path, List<String> headers, List<List<String>> rows) throws IOException {
        if (path == null || headers == null || rows == null) throw new IllegalArgumentException("Workbook path, headers and rows are required.");
        for (List<String> row : rows) if (row == null || row.size() != headers.size())
            throw new IllegalArgumentException("Every row must have the same number of cells as the header.");
        try (OutputStream out = Files.newOutputStream(path); ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            entry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Report\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            entry(zip, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Aptos\"/></font><font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Aptos\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF174B3A\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/></cellXfs></styleSheet>");
            StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"18\"/><cols>");
            for (int i = 1; i <= Math.max(1, headers.size()); i++) sheet.append("<col min=\"").append(i).append("\" max=\"").append(i).append("\" width=\"22\" customWidth=\"1\"/>");
            sheet.append("</cols><sheetData>");
            appendRow(sheet, 1, headers, true);
            for (int i = 0; i < rows.size(); i++) appendRow(sheet, i + 2, rows.get(i), false);
            sheet.append("</sheetData>");
            if (!headers.isEmpty() && !rows.isEmpty()) sheet.append("<autoFilter ref=\"A1:").append(columnName(headers.size())).append(rows.size() + 1).append("\"/>");
            sheet.append("</worksheet>");
            entry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            zip.finish();
        }
    }

    private static void appendRow(StringBuilder xml, int row, List<String> cells, boolean header) {
        xml.append("<row r=\"").append(row).append("\">");
        for (int c = 0; c < cells.size(); c++) {
            xml.append("<c r=\"").append(columnName(c + 1)).append(row).append("\" t=\"inlineStr\"");
            if (header) xml.append(" s=\"1\"");
            xml.append("><is><t xml:space=\"preserve\">").append(escape(cells.get(c) == null ? "" : cells.get(c))).append("</t></is></c>");
        }
        xml.append("</row>");
    }

    private static String columnName(int n) {
        StringBuilder b = new StringBuilder();
        while (n > 0) { int r = (n - 1) % 26; b.append((char) ('A' + r)); n = (n - 1) / 26; }
        return b.reverse().toString();
    }

    private static String escape(String value) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '&') b.append("&amp;"); else if (c == '<') b.append("&lt;"); else if (c == '>') b.append("&gt;");
            else if (c == '"') b.append("&quot;"); else if (c == '\'') b.append("&apos;");
            else if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20) b.append(c);
        }
        return b.toString();
    }

    private static void entry(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
