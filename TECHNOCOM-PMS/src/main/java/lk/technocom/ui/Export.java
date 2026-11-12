package lk.technocom.ui;

import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.FileChooser;
import lk.technocom.util.LanguageManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Native XLSX/CSV export and multi-page printing (choose a PDF printer for PDF output). */
public final class Export {

    private Export() {}

    public static void csv(String name, List<String> headers, List<List<String>> rows) {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(name + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV / Excel", "*.csv"));
        File f = fc.showSaveDialog(Dialogs.primary());
        if (f == null) return;
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(line(headers));
        for (List<String> r : rows) sb.append(line(r));
        try {
            Files.writeString(f.toPath(), sb.toString(), StandardCharsets.UTF_8);
            Dialogs.info(LanguageManager.x("Export"), LanguageManager.x("Saved") + ": " + f.getAbsolutePath());
        } catch (IOException e) {
            Dialogs.error(e.getMessage());
        }
    }

    /** Writes a dependency-free Office Open XML workbook. All report values are stored as text,
     * never formulas, preventing spreadsheet formula injection from database/user-controlled data. */
    public static void xlsx(String name, List<String> headers, List<List<String>> rows) {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(name + ".xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        File f = fc.showSaveDialog(Dialogs.primary());
        if (f == null) return;
        try {
            lk.technocom.util.XlsxWorkbookWriter.write(f.toPath(), headers, rows);
            Dialogs.info(LanguageManager.x("Export"), LanguageManager.x("Saved") + ": " + f.getAbsolutePath());
        } catch (IOException e) { Dialogs.error(e.getMessage()); }
    }

    private static String line(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            String s = cells.get(i) == null ? "" : cells.get(i);
            sb.append('"').append(s.replace("\"", "\"\"")).append('"');
        }
        return sb.append("\r\n").toString();
    }

    /** Saves a paginated A4-landscape PDF report. Configure TECHNOCOM_PDF_FONT to use a Unicode TTF font. */
    public static void pdf(String name, List<String> headers, List<List<String>> rows, List<String> filters) {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(name + ".pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Document", "*.pdf"));
        File f = fc.showSaveDialog(Dialogs.primary());
        if (f == null) return;
        try {
            lk.technocom.util.PdfReportWriter.write(f.toPath(), name.replace('_', ' '), headers, rows, filters);
            Dialogs.info(LanguageManager.x("Export"), LanguageManager.x("Saved") + ": " + f.getAbsolutePath());
        } catch (IOException e) { Dialogs.error(e.getMessage()); }
    }

    /** Prints a node over as many A4 pages as needed. */
    public static void print(Node paper) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            Dialogs.error("No printer is available.");
            return;
        }
        if (!job.showPrintDialog(Dialogs.primary())) return;
        PageLayout pl = job.getPrinter().createPageLayout(Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);
        double pw = pl.getPrintableWidth(), ph = pl.getPrintableHeight();
        double nw = paper.getBoundsInLocal().getWidth(), nh = paper.getBoundsInLocal().getHeight();
        double scale = Math.min(1.0, pw / nw);
        double pageNodeH = ph / scale;
        int pages = Math.max(1, (int) Math.ceil(nh / pageNodeH));

        Scale sc = new Scale(scale, scale);
        Translate tr = new Translate();
        paper.getTransforms().setAll(sc, tr);
        boolean ok = true;
        for (int i = 0; i < pages && ok; i++) {
            tr.setY(-i * pageNodeH);
            paper.setClip(new Rectangle(0, i * pageNodeH, nw, pageNodeH));
            ok = job.printPage(pl, paper);
        }
        paper.setClip(null);
        paper.getTransforms().clear();
        if (ok) job.endJob();
    }
}
