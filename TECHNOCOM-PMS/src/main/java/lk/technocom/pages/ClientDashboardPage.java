package lk.technocom.pages;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lk.technocom.ui.Access;
import lk.technocom.ui.Dialogs;
import lk.technocom.ui.AppShell;
import lk.technocom.ui.Page;
import lk.technocom.ui.UI;
import lk.technocom.ui.Views;
import lk.technocom.util.Db;
import lk.technocom.util.Fmt;
import lk.technocom.util.LanguageManager;
import lk.technocom.util.Session;

import java.util.List;
import java.util.Map;

/** Dashboard of Client / Developer users: only the projects, payments and meetings of their own client. */
public final class ClientDashboardPage implements Views.Ready {

    @FXML private Label lblWelcome, statProjects, statProgress, statDue, statMeet;
    @FXML private FlowPane tiles;
    @FXML private VBox projectsBox, payBox, meetBox;

    private ClientDashboardPage() {}

    public static Node build() {
        return Views.load("/view/ClientDashboardView.fxml", new ClientDashboardPage());
    }

    @Override
    public void ready() {
        int cid = Session.getClientId();
        String clientName = String.valueOf(Db.scalar("SELECT COALESCE((SELECT name FROM clients WHERE client_id=?),'-')", cid));
        lblWelcome.setText(LanguageManager.x("Welcome") + ", " + Session.getFullName() + "  ·  " + clientName);
        if(Session.hasRole("Client") && cid>0){
            javafx.scene.control.Button portal = new javafx.scene.control.Button("Open Full Client Portal  →");
            portal.getStyleClass().add("btn-primary");
            portal.setOnAction(e -> Dialogs.show("Technocom Client Portal", ClientPortalWorkspace.build(), 1220, 840));
            projectsBox.getChildren().add(portal);
        }

        long projects = Db.num("SELECT COUNT(*) FROM projects WHERE client_id=? AND is_active=TRUE", cid);
        statProjects.setText(Fmt.num(projects));
        statProgress.setText(projects == 0 ? "0 %" : Math.round(Db.dbl(
                "SELECT COALESCE(AVG(progress),0) FROM projects WHERE client_id=? AND is_active=TRUE", cid)) + " %");
        statDue.setText(Fmt.compact(Db.dbl("SELECT COALESCE(SUM(pay.amount),0) FROM payments pay JOIN projects p ON p.project_id=pay.project_id "
                + "WHERE p.client_id=? AND pay.status='PENDING'", cid)));
        statMeet.setText(Fmt.num(Db.num("SELECT COUNT(*) FROM meetings m JOIN projects p ON p.project_id=m.project_id "
                + "WHERE p.client_id=? AND m.status='SCHEDULED' AND m.meeting_date>=CURDATE()", cid)));

        tiles.getChildren().removeIf(t -> !Access.can(Page.valueOf(t.getId())));    // Developer and Client see different tiles

        List<Map<String, Object>> ps = Db.rows("SELECT project_code, name, progress, status FROM projects "
                + "WHERE client_id=? AND is_active=TRUE ORDER BY created_at DESC LIMIT 8", cid);
        if (ps.isEmpty()) projectsBox.getChildren().add(UI.muted(LanguageManager.x("No records found")));
        for (Map<String, Object> p : ps) {
            double pr = p.get("progress") instanceof Number n ? n.doubleValue() : 0;
            ProgressBar bar = new ProgressBar(pr / 100.0);
            bar.setPrefWidth(220);
            bar.getStyleClass().add("gold-bar");
            HBox row = new HBox(12, new Label(p.get("project_code") + "  " + p.get("name")), UI.grow(), bar,
                    UI.lbl(Math.round(pr) + "%", "muted"), UI.badge(String.valueOf(p.get("status"))));
            row.setAlignment(Pos.CENTER_LEFT);
            projectsBox.getChildren().add(row);
        }

        for (Map<String, Object> r : Db.rows("SELECT pay.payment_no, pay.amount, pay.due_date FROM payments pay JOIN projects p ON p.project_id=pay.project_id "
                + "WHERE p.client_id=? AND pay.status='PENDING' ORDER BY pay.due_date LIMIT 5", cid)) {
            payBox.getChildren().add(UI.row(new Label(r.get("payment_no") + "  " + Fmt.money(r.get("amount"))), UI.grow(),
                    UI.muted(Fmt.relativeDayOrDate(r.get("due_date")))));
        }
        if (payBox.getChildren().isEmpty()) payBox.getChildren().add(UI.muted(LanguageManager.x("No records found")));

        for (Map<String, Object> r : Db.rows("SELECT m.title, m.meeting_date, m.meeting_time FROM meetings m JOIN projects p ON p.project_id=m.project_id "
                + "WHERE p.client_id=? AND m.status='SCHEDULED' AND m.meeting_date>=CURDATE() ORDER BY m.meeting_date, m.meeting_time LIMIT 5", cid)) {
            meetBox.getChildren().add(UI.row(new Label(String.valueOf(r.get("title"))), UI.grow(),
                    UI.muted(Fmt.relativeDayOrDate(r.get("meeting_date")) + " " + Fmt.str(r.get("meeting_time")))));
        }
        if (meetBox.getChildren().isEmpty()) meetBox.getChildren().add(UI.muted(LanguageManager.x("No records found")));
    }

    @FXML private void onTile(MouseEvent e) { AppShell.get().go(Page.valueOf(((Node) e.getSource()).getId())); }
    @FXML private void onNotifications() { AccountPopups.notifications(); }
}
