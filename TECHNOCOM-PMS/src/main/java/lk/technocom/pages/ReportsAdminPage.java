package lk.technocom.pages;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.input.MouseEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import lk.technocom.ui.*;
import lk.technocom.ui.ListPage.K;
import lk.technocom.util.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReportsAdminPage {

    private ReportsAdminPage() {}

    /** One report: SQL + the columns to show. flags: P=project filter, C=client filter, D=date filter, S=status filter. */
    private record Rep(String name, String flags, String sql, String[][] cols) {}

    private static Rep selected;
    private static ComboBox<Form.Item> project, client, range, status;
    private static final List<VBox> tiles = new ArrayList<>();

    private static final List<Rep> REPORTS = List.of(
            new Rep("Project", "PCDS",
                    "SELECT p.project_id, p.client_id, p.start_date AS rep_date, p.status, p.project_code AS code, p.name, c.name AS client, "
                            + "COALESCE(m.full_name,'-') AS manager, p.contract_value, p.progress FROM projects p JOIN clients c ON c.client_id=p.client_id "
                            + "LEFT JOIN users m ON m.user_id=p.manager_id",
                    cols("Code:code", "Project:name", "Client:client", "Manager:manager", "Contract value:contract_value:MONEY", "Progress %:progress:NUM", "Status:status:NICE")),
            new Rep("Client", "S",
                    "SELECT IF(is_active,'ACTIVE','INACTIVE') AS status, client_code, name, client_type, contact_person, phone, email FROM clients",
                    cols("Code:client_code", "Client:name", "Type:client_type:NICE", "Contact:contact_person", "Phone:phone", "Email:email", "Status:status:NICE")),
            new Rep("User", "S",
                    "SELECT u.status, u.full_name, u.email, r.role_name AS role, u.last_login FROM users u JOIN roles r ON r.role_id=u.role_id",
                    cols("Name:full_name", "Email:email", "Role:role", "Status:status:NICE", "Last login:last_login")),
            new Rep("DPR", "PCDS",
                    "SELECT d.project_id, p.client_id, d.report_date AS rep_date, d.status, d.dpr_no, p.project_code AS code, d.weather, d.workers_count, d.progress_today "
                            + "FROM daily_progress_reports d JOIN projects p ON p.project_id=d.project_id",
                    cols("DPR:dpr_no", "Project:code", "Date:rep_date", "Weather:weather:NICE", "Workers:workers_count:NUM", "Progress %:progress_today:NUM", "Status:status:NICE")),
            new Rep("BOQ", "PCDS",
                    "SELECT b.project_id, p.client_id, b.created_at AS rep_date, b.status, b.boq_no, p.project_code AS code, b.version, "
                            + "ROUND(COALESCE((SELECT SUM(quantity*unit_price) FROM boq_items i WHERE i.boq_id=b.boq_id),0)*(1+b.contingency_percent/100),2) AS total "
                            + "FROM boq b JOIN projects p ON p.project_id=b.project_id",
                    cols("BOQ:boq_no", "Project:code", "Version:version:NUM", "Total:total:MONEY", "Status:status:NICE")),
            new Rep("Estimate", "P",
                    "SELECT e.project_id, e.boq_no, p.project_code AS code, e.estimated_cost, e.actual_cost, e.variance FROM v_estimate_vs_actual e "
                            + "JOIN projects p ON p.project_id=e.project_id",
                    cols("BOQ:boq_no", "Project:code", "Estimated:estimated_cost:MONEY", "Actual:actual_cost:MONEY", "Variance:variance:MONEY")),
            new Rep("Drawing Approval", "PS",
                    "SELECT d.project_id, a.status, d.drawing_no, d.title, a.authority, a.submitted_date, a.decision_date, a.comments "
                            + "FROM drawing_approvals a JOIN drawings d ON d.drawing_id=a.drawing_id",
                    cols("Drawing:drawing_no", "Title:title", "Authority:authority", "Status:status:NICE", "Submitted:submitted_date", "Decision:decision_date", "Comments:comments")),
            new Rep("Specifications", "P",
                    "SELECT s.project_id, s.spec_no, s.title, s.category, s.revision, p.project_code AS code FROM specifications s JOIN projects p ON p.project_id=s.project_id",
                    cols("Spec:spec_no", "Title:title", "Project:code", "Category:category:NICE", "Rev.:revision")),
            new Rep("Stock Balance", "",
                    "SELECT item_code, name, category, unit, quantity_on_hand, reorder_level, unit_price, ROUND(quantity_on_hand*unit_price,2) AS value "
                            + "FROM stock_items WHERE is_active=TRUE",
                    cols("Code:item_code", "Item:name", "Category:category:NICE", "Unit:unit:NICE", "Qty:quantity_on_hand:NUM", "Min:reorder_level:NUM", "Value:value:MONEY")),
            new Rep("Low Stock", "",
                    "SELECT item_code, name, category, unit, quantity_on_hand, reorder_level FROM v_low_stock",
                    cols("Code:item_code", "Item:name", "Category:category:NICE", "Unit:unit:NICE", "Qty:quantity_on_hand:NUM", "Reorder level:reorder_level:NUM")),
            new Rep("Stock Movement", "PD",
                    "SELECT t.project_id, t.transaction_date AS rep_date, t.transaction_date, i.item_code, i.name, t.transaction_type, t.quantity, i.unit "
                            + "FROM stock_transactions t JOIN stock_items i ON i.item_id=t.item_id",
                    cols("Date:transaction_date", "Item:name", "Type:transaction_type:NICE", "Qty:quantity:NUM", "Unit:unit:NICE")),
            new Rep("Payment List", "PCDS",
                    "SELECT p.project_id, pr.client_id, p.due_date AS rep_date, p.display_status AS status, p.payment_no, pr.project_code AS code, p.type, p.amount, p.due_date, p.paid_date "
                            + "FROM v_payments_with_status p JOIN projects pr ON pr.project_id=p.project_id",
                    cols("Payment:payment_no", "Project:code", "Type:type:NICE", "Amount:amount:MONEY", "Due:due_date", "Paid:paid_date", "Status:status:NICE")),
            new Rep("Payment Summary", "P",
                    "SELECT project_id, project_code, name, contract_value, total_paid, total_pending, balance FROM v_project_payment_summary",
                    cols("Code:project_code", "Project:name", "Contract:contract_value:MONEY", "Paid:total_paid:MONEY", "Pending:total_pending:MONEY", "Balance:balance:MONEY")),
            new Rep("Handover", "PCDS",
                    "SELECT h.project_id, p.client_id, h.handover_date AS rep_date, h.status, p.project_code AS code, p.name, h.accepted_by, h.dlp_end_date "
                            + "FROM handovers h JOIN projects p ON p.project_id=h.project_id",
                    cols("Project:code", "Name:name", "Handover:rep_date", "Accepted by:accepted_by", "DLP ends:dlp_end_date", "Status:status:NICE")),
            new Rep("DLP", "PDS",
                    "SELECT h.project_id, v.reported_date AS rep_date, v.status, p.project_code AS code, v.defect, v.priority, v.visit_date "
                            + "FROM dlp_visits v JOIN handovers h ON h.handover_id=v.handover_id JOIN projects p ON p.project_id=h.project_id",
                    cols("Project:code", "Defect:defect", "Priority:priority:NICE", "Reported:rep_date", "Visit:visit_date", "Status:status:NICE")));

    private static String[][] cols(String... defs) {
        String[][] out = new String[defs.length][];
        for (int i = 0; i < defs.length; i++) {
            String[] p = defs[i].split(":");
            out[i] = p.length == 2 ? new String[]{p[0], p[1], "TEXT"} : p;
        }
        return out;
    }

    public static Node build() {
        return Views.load("/view/ReportsView.fxml", new View());
    }

    /** Controller of /view/ReportsView.fxml. */
    public static final class View implements Views.Ready {
        @FXML private ComboBox<Form.Item> cbProject, cbClient, cbRange, cbStatus;
        @FXML private GridPane reportGrid;
        @FXML private HBox lower;
        @FXML private VBox adminBox;

        @FXML private void onReportBuilder() {
            try { ReportBuilderPopup.open(); } catch (Exception ex) { Dialogs.error(ex.getMessage() == null ? "Unable to open report builder." : ex.getMessage()); }
        }

        @Override
        public void ready() {
            selected = null;
            tiles.clear();
            project = cbProject;
            client = cbClient;
            range = cbRange;
            status = cbStatus;

            boolean portal = Session.isClient();            // Client actor: "Generate reports" for his own projects only
            int cid = Session.getClientId();
            fill(cbProject, "Project", portal ? "SELECT project_id, CONCAT(project_code,' ',name) FROM projects WHERE client_id=" + cid : Q.PROJECTS);
            fill(cbClient, "Client", portal ? "SELECT client_id, name FROM clients WHERE client_id=" + cid : Q.CLIENTS);
            if (portal && cbClient.getItems().size() > 1) {
                cbClient.getSelectionModel().select(1);     // locked to his own client record
                cbClient.setDisable(true);
            }
            cbRange.getItems().add(new Form.Item(null, LanguageManager.x("Date range")));
            cbRange.getItems().add(new Form.Item("MONTH", LanguageManager.x("This month")));
            cbRange.getItems().add(new Form.Item("30", LanguageManager.x("Last 30 days")));
            cbRange.getItems().add(new Form.Item("YEAR", LanguageManager.x("This year")));
            cbRange.getSelectionModel().selectFirst();
            cbStatus.getItems().add(new Form.Item(null, LanguageManager.x("Status")));
            for (String s : new String[]{"ACTIVE", "ONGOING", "PLANNING", "COMPLETED", "DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "PENDING", "PAID", "OVERDUE", "HANDED_OVER"})
                cbStatus.getItems().add(new Form.Item(s, Fmt.nice(s)));
            cbStatus.getSelectionModel().selectFirst();

            List<Rep> reps = portal ? REPORTS.stream().filter(x -> x.flags().contains("C")).toList() : REPORTS;
            for (int i = 0; i < reps.size(); i++) {
                Rep r = reps.get(i);
                VBox t = UI.card(UI.lbl(LanguageManager.x(r.name()), "tile-title"),
                        UI.muted(LanguageManager.x("Preview") + " - " + LanguageManager.x("Print")));
                t.getStyleClass().add("tile");
                t.setSpacing(2);
                t.setMaxWidth(Double.MAX_VALUE);
                t.setOnMouseClicked(e -> {
                    select(r, t);
                    if (e.getClickCount() == 2) preview(r);
                });
                tiles.add(t);
                GridPane.setHgrow(t, Priority.ALWAYS);
                reportGrid.add(t, i % 3, i / 3);
            }

            if (!portal && (Session.isAdmin() || Session.hasRole("Project Manager"))) {
                javafx.scene.control.Button enterprise = new javafx.scene.control.Button("Open Enterprise Workflows");
                enterprise.getStyleClass().add("btn-primary");
                enterprise.setMaxWidth(Double.MAX_VALUE);
                enterprise.setOnAction(e -> Dialogs.show("Technocom Enterprise Workflows", EnterpriseConsolePage.build(), 1180, 820));
                adminBox.getChildren().add(enterprise);
            }
            if (portal) lower.getChildren().remove(adminBox);
            for (Node n : adminBox.getChildren()) {
                if (n.getId() == null) continue;
                n.getStyleClass().add(adminAllowed(n.getId()) ? "tile" : "tile-locked");
            }
        }

        @FXML private void onPdf() { output(true); }
        @FXML private void onExcel() { output(false); }

        @FXML
        private void onAdminTile(MouseEvent e) {
            String id = ((Node) e.getSource()).getId();
            if (!adminAllowed(id)) {
                Dialogs.error(LanguageManager.x("Only the Admin can open this tool."));
                return;
            }
            switch (id) {
                case "BACKUP" -> UI.run(AdminPopups::backup);
                case "EMAIL" -> UI.run(AdminPopups::emailSettings);
                case "EMAILLOG" -> UI.run(AdminPopups::emailLog);
                case "AUDIT" -> UI.run(AdminPopups::auditLog);
                case "COMPANY" -> UI.run(AdminPopups::company);
                default -> { }
            }
        }

        private static boolean adminAllowed(String id) {
            return Session.isAdmin() || id.equals("AUDIT") || id.equals("EMAILLOG");
        }
    }

    private static void fill(ComboBox<Form.Item> cb, String label, String sql) {
        cb.getItems().add(new Form.Item(null, LanguageManager.x(label)));
        cb.getItems().addAll(Form.items(sql, false));
        cb.getSelectionModel().selectFirst();
    }

    private static void select(Rep r, VBox tile) {
        selected = r;
        for (VBox t : tiles) t.setStyle("");
        tile.setStyle("-fx-background-color: #FBF4DF;");
    }

    private static void output(boolean pdf) {
        if (!Session.isClient()) ActionPermission.require(ActionPermission.Action.EXPORT_REPORTS);
        if (selected == null) {
            Dialogs.info(LanguageManager.x("Reports"), LanguageManager.x("Select a report first."));
            return;
        }
        Result res = run(selected);
        if (pdf) {
            List<String> filters = new ArrayList<>();
            if (project.getValue() != null && project.getValue().id != null) filters.add(LanguageManager.x("Project") + ": " + project.getValue().label);
            if (!Session.isClient() && client.getValue() != null && client.getValue().id != null) filters.add(LanguageManager.x("Client") + ": " + client.getValue().label);
            if (range.getValue() != null && range.getValue().id != null) filters.add(range.getValue().label);
            if (status.getValue() != null && status.getValue().id != null) filters.add(LanguageManager.x("Status") + ": " + status.getValue().label);
            Export.pdf(selected.name().replace(' ', '_'), res.heads, res.text, filters);
        } else {
            Export.xlsx(selected.name().replace(' ', '_'), res.heads, res.text);
        }
    }

    // ------------------------------------------------------------------ run + preview
    private record Result(List<String> heads, List<List<String>> text, int count) {}

    private static Result run(Rep r) {
        // Enforce authorization at the report execution boundary; the disabled client
        // selector is a UI convenience, never the tenant-isolation control.
        if (Session.getUserId() <= 0) throw new SecurityException("Sign in required.");
        ActionPermission.require(ActionPermission.Action.VIEW_REPORTS);
        StringBuilder sb = new StringBuilder("SELECT * FROM (").append(r.sql()).append(") t WHERE 1=1");
        List<Object> a = new ArrayList<>();
        if (Session.isClient()) {
            int ownClientId = Session.getClientId();
            if (ownClientId <= 0) throw new SecurityException("Client account is not linked to a client record.");
            if (!r.flags().contains("C")) throw new SecurityException("This report is not available to client accounts.");
            sb.append(" AND t.client_id=?");
            a.add(ownClientId);
            if (project.getValue() != null && project.getValue().id != null) {
                sb.append(" AND t.project_id=?");
                a.add(project.getValue().id);
            }
        } else {
            if (r.flags().contains("P") && project.getValue() != null && project.getValue().id != null) { sb.append(" AND t.project_id=?"); a.add(project.getValue().id); }
            if (r.flags().contains("C") && client.getValue() != null && client.getValue().id != null) { sb.append(" AND t.client_id=?"); a.add(client.getValue().id); }
        }
        if (r.flags().contains("S") && status.getValue().id != null) { sb.append(" AND t.status=?"); a.add(status.getValue().id); }
        if (r.flags().contains("D") && range.getValue().id != null) {
            LocalDate from = switch (String.valueOf(range.getValue().id)) {
                case "MONTH" -> LocalDate.now().withDayOfMonth(1);
                case "30" -> LocalDate.now().minusDays(30);
                default -> LocalDate.now().withDayOfYear(1);
            };
            sb.append(" AND t.rep_date >= ?");
            a.add(from);
        }
        sb.append(" LIMIT 2000");
        List<Map<String, Object>> rows = Db.rows(sb.toString(), a.toArray());
        List<String> heads = new ArrayList<>();
        for (String[] c : r.cols()) heads.add(LanguageManager.x(c[0]));
        List<List<String>> text = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<String> line = new ArrayList<>();
            for (String[] c : r.cols()) line.add(fmt(row.get(c[1]), K.valueOf(c[2])));
            text.add(line);
        }
        return new Result(heads, text, rows.size());
    }

    private static String fmt(Object v, K k) {
        return switch (k) {
            case MONEY -> Fmt.money(v);
            case NUM -> Fmt.num(v);
            case NICE, BADGE -> Fmt.nice(v);
            default -> Fmt.str(v);
        };
    }

    private static void preview(Rep r) {
        Result res = run(r);
        List<String> f = new ArrayList<>();
        if (project.getValue().id != null) f.add(LanguageManager.x("Project") + ": " + project.getValue().label);
        if (client.getValue().id != null) f.add(LanguageManager.x("Client") + ": " + client.getValue().label);
        if (range.getValue().id != null) f.add(range.getValue().label);
        if (status.getValue().id != null) f.add(LanguageManager.x("Status") + ": " + status.getValue().label);
        VBox sheet = Paper.sheet(LanguageManager.x(r.name()) + " " + LanguageManager.x("Report"),
                f.isEmpty() ? LanguageManager.x("All records") : String.join("   |   ", f));
        sheet.getChildren().add(Paper.table(res.heads, res.text));
        sheet.getChildren().add(UI.lbl(res.count + " " + LanguageManager.x("record(s)"), "paper-cell"));
        Paper.preview(LanguageManager.x("Report Preview"), sheet);
    }
}
