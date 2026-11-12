package lk.technocom.pages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import lk.technocom.ui.*;
import lk.technocom.util.Db;
import lk.technocom.util.Fmt;
import lk.technocom.util.LanguageManager;
import lk.technocom.util.Session;

import java.util.List;
import java.util.Map;

public final class DashboardPage {

    private DashboardPage() {}

    /** Staff dashboard = /view/DashboardView.fxml (Scene Builder). Client users get their own dashboard. */
    public static Node build() {
        if (Session.isClient()) return ClientDashboardPage.build();
        try {
            return DashboardController.load();
        } catch (Exception e) {
            e.printStackTrace();
            return buildInCode();
        }
    }

    /** The old way: the same dashboard built with code. Only used when the FXML cannot be loaded. */
    private static Node buildInCode() {
        VBox titles = new VBox(2, UI.h1(Page.DASHBOARD.text()),
                UI.sub(LanguageManager.x("One dashboard for every role - locked tiles are visible but not clickable")));
        HBox head = new HBox(10, titles, UI.grow(),
                UI.outline(LanguageManager.x("Notifications"), AccountPopups::notifications));
        if (Access.can(Page.PROJECTS)) head.getChildren().add(UI.primary("+ " + LanguageManager.x("New Project"),
                () -> ProjectsPage.addEdit(null, () -> AppShell.get().reload())));
        head.setAlignment(Pos.CENTER_LEFT);

        HBox roles = new HBox(8, UI.lbl(LanguageManager.x("Logged in as"), "section-title"));
        roles.setAlignment(Pos.CENTER_LEFT);
        for (String r : Access.ROLES) {
            Label c = UI.lbl(LanguageManager.x(r), "chip");
            if (r.equals(Session.getRole())) c.getStyleClass().add("chip-active");
            roles.getChildren().add(c);
        }

        HBox stats = new HBox(14,
                stat(Fmt.num(Db.num("SELECT COUNT(*) FROM projects WHERE is_active=TRUE AND status IN ('PLANNING','ONGOING')")), "Active Projects"),
                stat(Fmt.num(Db.num("SELECT (SELECT COUNT(*) FROM boq WHERE status='SUBMITTED') + "
                        + "(SELECT COUNT(*) FROM drawings WHERE status='SUBMITTED')")), "Pending Approvals"),
                stat(Fmt.num(Db.num("SELECT COUNT(*) FROM meetings WHERE status='SCHEDULED' "
                        + "AND YEARWEEK(meeting_date,1)=YEARWEEK(CURDATE(),1)")), "Meetings This Week"),
                stat(Fmt.compact(Db.dbl("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='PENDING'")), "Payments Due"),
                stat(Fmt.num(Db.num("SELECT COUNT(*) FROM v_low_stock")), "Low Stock"));

        GridPane tiles = new GridPane();
        tiles.setHgap(14);
        tiles.setVgap(14);
        Object[][] t = {
                {Page.ACCOUNT, "Profile, password, alerts"}, {Page.USERS, "Accounts and roles"},
                {Page.CLIENTS, "Client records"}, {Page.PROJECTS, "Site requirements, documents"},
                {Page.DPR, "Daily progress reports"}, {Page.BOQ, "Estimates and approval"},
                {Page.DRAWINGS, "Approval tracker"}, {Page.SPECS, "Specifications"},
                {Page.MEETINGS, "Schedule and minutes"}, {Page.STOCK, "Items, in, issue"}};
        for (int i = 0; i < t.length; i++) {
            Page p = (Page) t[i][0];
            boolean ok = Access.can(p);
            VBox tile = UI.card(UI.lbl(p.text() + (ok ? "" : " - " + LanguageManager.x("locked")), "tile-title"),
                    UI.muted(LanguageManager.x((String) t[i][1])));
            tile.getStyleClass().add(ok ? "tile" : "tile-locked");
            tile.setSpacing(2);
            tile.setMaxWidth(Double.MAX_VALUE);
            if (ok) tile.setOnMouseClicked(e -> AppShell.get().go(p));
            else tile.setOnMouseClicked(e -> AppShell.get().go(p));
            GridPane.setHgrow(tile, Priority.ALWAYS);
            tiles.add(tile, i % 5, i / 5);
        }
        for (int i = 0; i < 5; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(20);
            tiles.getColumnConstraints().add(cc);
        }

        HBox lower = new HBox(14, progressCard(), paymentsCard(), meetingsCard(), approvalsCard());
        for (Node n : lower.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        VBox root = new VBox(14, head, roles, stats, UI.lbl(LanguageManager.x("Quick navigation"), "section-title"), tiles, lower);
        root.setPadding(new Insets(20, 26, 26, 26));
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }

    private static VBox stat(String v, String label) {
        VBox s = UI.stat(v, LanguageManager.x(label));
        HBox.setHgrow(s, Priority.ALWAYS);
        s.setMaxWidth(Double.MAX_VALUE);
        return s;
    }

    static VBox progressCard() {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis(0, 100, 25);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(200);
        chart.setVerticalGridLinesVisible(false);
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (Map<String, Object> r : Db.rows("SELECT project_code, progress FROM projects WHERE is_active=TRUE "
                + "AND status IN ('PLANNING','ONGOING','COMPLETED') ORDER BY created_at DESC LIMIT 6")) {
            s.getData().add(new XYChart.Data<>(r.get("project_code").toString(), (Number) r.get("progress")));
        }
        chart.getData().add(s);
        chart.setStyle("-fx-bar-fill: #C9A24B;");
        VBox c = UI.card(UI.lbl(LanguageManager.x("Progress by project"), "section-title"), chart);
        c.setMaxWidth(Double.MAX_VALUE);
        return c;
    }

    static VBox paymentsCard() {
        List<Map<String, Object>> rows = Db.rows("SELECT payment_no, due_date FROM payments WHERE status='PENDING' "
                + "ORDER BY due_date LIMIT 5");
        GridPane g = mini("Pay", "Due");
        int r = 1;
        for (Map<String, Object> m : rows) {
            g.add(new Label(String.valueOf(m.get("payment_no"))), 0, r);
            g.add(new Label(Fmt.relativeDayOrDate(m.get("due_date"))), 1, r);
            r++;
        }
        VBox c = UI.card(UI.lbl(LanguageManager.x("Upcoming payments"), "section-title"), g);
        c.setMaxWidth(Double.MAX_VALUE);
        return c;
    }

    static VBox meetingsCard() {
        List<Map<String, Object>> rows = Db.rows("SELECT title, meeting_date, meeting_time FROM meetings "
                + "WHERE status='SCHEDULED' AND meeting_date >= CURDATE() ORDER BY meeting_date, meeting_time LIMIT 5");
        GridPane g = mini("Meeting", "When");
        int r = 1;
        for (Map<String, Object> m : rows) {
            g.add(new Label(String.valueOf(m.get("title"))), 0, r);
            g.add(new Label(Fmt.relativeDayOrDate(m.get("meeting_date")) + " " + Fmt.str(m.get("meeting_time"))), 1, r);
            r++;
        }
        VBox c = UI.card(UI.lbl(LanguageManager.x("Upcoming meetings"), "section-title"), g);
        c.setMaxWidth(Double.MAX_VALUE);
        return c;
    }

    static VBox approvalsCard() {
        List<Map<String, Object>> rows = Db.rows(
                "SELECT CONCAT('Estimate ', boq_no) AS item, status FROM boq WHERE status='SUBMITTED' "
                        + "UNION ALL SELECT CONCAT('Drawing ', drawing_no), status FROM drawings WHERE status='SUBMITTED' LIMIT 5");
        GridPane g = mini("Item", "Status");
        int r = 1;
        for (Map<String, Object> m : rows) {
            g.add(new Label(String.valueOf(m.get("item"))), 0, r);
            g.add(UI.badge(String.valueOf(m.get("status"))), 1, r);
            r++;
        }
        VBox c = UI.card(UI.lbl(LanguageManager.x("Pending approvals"), "section-title"), g);
        c.setMaxWidth(Double.MAX_VALUE);
        return c;
    }

    private static GridPane mini(String a, String b) {
        GridPane g = new GridPane();
        g.setHgap(20);
        g.setVgap(8);
        g.add(UI.lbl(LanguageManager.x(a).toUpperCase(), "kv-key"), 0, 0);
        g.add(UI.lbl(LanguageManager.x(b).toUpperCase(), "kv-key"), 1, 0);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(55);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(45);
        g.getColumnConstraints().addAll(c1, c2);
        return g;
    }
}
