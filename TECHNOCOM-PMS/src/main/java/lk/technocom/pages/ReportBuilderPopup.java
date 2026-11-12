package lk.technocom.pages;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lk.technocom.ui.Dialogs;
import lk.technocom.ui.Export;
import lk.technocom.ui.Form;
import lk.technocom.ui.Paper;
import lk.technocom.ui.UI;
import lk.technocom.util.Session;
import lk.technocom.workflow.ReportBuilderService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** User-facing allow-listed report builder with scoped filters and PDF/XLSX output. */
final class ReportBuilderPopup {
    private ReportBuilderPopup() {}

    static void open() {
        ComboBox<ReportBuilderService.Report> report = new ComboBox<>();
        report.setMaxWidth(Double.MAX_VALUE);
        report.setItems(FXCollections.observableArrayList(ReportBuilderService.availableReports()));
        report.setPromptText("Choose a report");
        report.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ReportBuilderService.Report r) {
                if (r == null) return "";
                return switch (r) {
                    case PROJECT_PROGRESS -> "Project progress";
                    case PAYMENT_STATUS -> "Payment status";
                    case PROCUREMENT_ORDERS -> "Procurement orders";
                    case QUALITY_NCR -> "Quality / NCR";
                    case HR_TRAINING -> "HR training";
                    case HR_CERTIFICATE_EXPIRY -> "HR certificate expiry";
                    case CASHFLOW_FORECAST -> "Cashflow forecast";
                };
            }
            @Override public ReportBuilderService.Report fromString(String s) { return null; }
        });
        DatePicker from = new DatePicker();
        DatePicker to = new DatePicker();
        ComboBox<Form.Item> project = new ComboBox<>();
        project.setMaxWidth(Double.MAX_VALUE);
        project.getItems().add(new Form.Item(null, "All projects"));
        if (!Session.isClient()) project.getItems().addAll(Form.items(Q.PROJECTS, false));
        project.getSelectionModel().selectFirst();
        if (Session.isClient()) project.setDisable(true);
        report.valueProperty().addListener((obs, oldValue, newValue) -> {
            boolean supportsProject = newValue != ReportBuilderService.Report.HR_TRAINING
                    && newValue != ReportBuilderService.Report.HR_CERTIFICATE_EXPIRY;
            project.setDisable(Session.isClient() || !supportsProject);
            if (!supportsProject) project.getSelectionModel().selectFirst();
        });

        Label hint = UI.muted("Reports use predefined, parameterised queries. Results are limited to 5,000 rows.");
        VBox preview = new VBox(8);
        preview.setPadding(new Insets(8, 0, 0, 0));
        final ReportBuilderService.Result[] latest = new ReportBuilderService.Result[1];
        Runnable run = () -> {
            latest[0] = null;
            try {
                if (report.getValue() == null) throw new IllegalArgumentException("Choose a report first.");
                Integer projectId = project.getValue() == null || project.getValue().id == null ? null : ((Number) project.getValue().id).intValue();
                latest[0] = ReportBuilderService.run(report.getValue(), new ReportBuilderService.Filter(from.getValue(), to.getValue(), projectId));
                List<List<String>> rows = stringify(latest[0]);
                preview.getChildren().setAll(UI.lbl("Preview — " + rows.size() + " record(s)", "section-title"), Paper.table(latest[0].columns(), rows));
            } catch (Exception ex) { Dialogs.error(ex.getMessage() == null ? "Unable to generate report." : ex.getMessage()); }
        };
        Button show = UI.primary("Generate / Preview", run);
        Button pdf = UI.outline("PDF", () -> {
            run.run();
            if (latest[0] != null) Export.pdf(report.getValue().name().toLowerCase(), latest[0].columns(), stringify(latest[0]), filters(from, to, project));
        });
        Button excel = UI.primary("Excel", () -> {
            run.run();
            if (latest[0] != null) Export.xlsx(report.getValue().name().toLowerCase(), latest[0].columns(), stringify(latest[0]));
        });
        GridPane fields = new GridPane();
        fields.setHgap(10); fields.setVgap(8);
        fields.add(UI.lbl("Report", "field-label"), 0, 0); fields.add(report, 1, 0);
        fields.add(UI.lbl("Project", "field-label"), 0, 1); fields.add(project, 1, 1);
        fields.add(UI.lbl("From", "field-label"), 0, 2); fields.add(from, 1, 2);
        fields.add(UI.lbl("To", "field-label"), 0, 3); fields.add(to, 1, 3);
        ColumnConstraints c0 = new ColumnConstraints(); c0.setMinWidth(90);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS); c1.setFillWidth(true);
        fields.getColumnConstraints().addAll(c0, c1);
        report.setMaxWidth(Double.MAX_VALUE); from.setMaxWidth(Double.MAX_VALUE); to.setMaxWidth(Double.MAX_VALUE);
        HBox actions = new HBox(8, show, pdf, excel);
        VBox root = new VBox(12, hint, fields, actions, new Separator(), preview);
        root.setPadding(new Insets(14));
        ScrollPane scroll = new ScrollPane(root); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(600);
        Dialogs.show("Advanced Report Builder", scroll, 920, 760);
    }

    private static List<List<String>> stringify(ReportBuilderService.Result result) {
        List<List<String>> out = new ArrayList<>();
        for (Map<String,Object> row : result.rows()) {
            List<String> line = new ArrayList<>();
            for (String col : result.columns()) {
                Object value = row.get(col);
                line.add(value == null ? "" : String.valueOf(value));
            }
            out.add(line);
        }
        return out;
    }

    private static List<String> filters(DatePicker from, DatePicker to, ComboBox<Form.Item> project) {
        List<String> out = new ArrayList<>();
        if (from.getValue() != null) out.add("From: " + from.getValue());
        if (to.getValue() != null) out.add("To: " + to.getValue());
        if (project.getValue() != null && project.getValue().id != null) out.add("Project: " + project.getValue().label);
        return out;
    }
}
