package lk.technocom.pages;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import lk.technocom.ui.*;
import lk.technocom.util.Db;
import lk.technocom.util.Fmt;
import lk.technocom.util.LanguageManager;
import lk.technocom.util.Session;

import java.io.IOException;
import java.util.Map;

/**
 * Staff dashboard. The layout is /view/DashboardView.fxml (open it in Scene Builder):
 * role chips have Id = role name, tiles have Id = Page name (ACCOUNT, USERS ...). Keep those Ids.
 */
public final class DashboardController {

    private static final Map<String, String> TILE_TEXT = Map.of(
            "ACCOUNT", "Profile, password, alerts", "USERS", "Accounts and roles", "CLIENTS", "Client records",
            "PROJECTS", "Site requirements, documents", "DPR", "Daily progress reports", "BOQ", "Estimates and approval",
            "DRAWINGS", "Approval tracker", "SPECS", "Specifications", "MEETINGS", "Schedule and minutes",
            "STOCK", "Items, in, issue");

    @FXML private Label lblTitle, lblSub, lblLoggedAs, lblQuick;
    @FXML private Label lblActive, lblPending, lblMeetings, lblPayments, lblLow;
    @FXML private Label statActive, statPending, statMeetings, statPayments, statLow;
    @FXML private javafx.scene.control.Button btnNotifications, btnNewProject;
    @FXML private HBox roleChips;
    @FXML private Pane tiles;
    @FXML private VBox progressHolder, paymentsHolder, meetingsHolder, approvalsHolder;

    static Node load() throws IOException {
        FXMLLoader loader = new FXMLLoader(DashboardController.class.getResource("/view/DashboardView.fxml"));
        loader.setControllerFactory(c -> new DashboardController());
        return loader.load();
    }

    @FXML
    private void initialize() {
        lblTitle.setText(Page.DASHBOARD.text());
        lblSub.setText(LanguageManager.x("One dashboard for every role - locked tiles are visible but not clickable"));
        lblLoggedAs.setText(LanguageManager.x("Logged in as"));
        lblQuick.setText(LanguageManager.x("Quick navigation"));
        btnNotifications.setText(LanguageManager.x("Notifications"));
        btnNewProject.setText("+ " + LanguageManager.x("New Project"));
        btnNewProject.setVisible(Access.can(Page.PROJECTS));
        btnNewProject.setManaged(Access.can(Page.PROJECTS));

        for (Node n : roleChips.getChildren()) {
            if (n instanceof Label l && l.getId() != null) {
                l.setText(LanguageManager.x(l.getId()));
                if (l.getId().equals(Session.getRole())) l.getStyleClass().add("chip-active");
            }
        }

        lblActive.setText(LanguageManager.x("Active Projects"));
        lblPending.setText(LanguageManager.x("Pending Approvals"));
        lblMeetings.setText(LanguageManager.x("Meetings This Week"));
        lblPayments.setText(LanguageManager.x("Payments Due"));
        lblLow.setText(LanguageManager.x("Low Stock"));
        statActive.setText(Fmt.num(Db.num("SELECT COUNT(*) FROM projects WHERE is_active=TRUE AND status IN ('PLANNING','ONGOING')")));
        statPending.setText(Fmt.num(Db.num("SELECT (SELECT COUNT(*) FROM boq WHERE status='SUBMITTED') + "
                + "(SELECT COUNT(*) FROM drawings WHERE status='SUBMITTED')")));
        statMeetings.setText(Fmt.num(Db.num("SELECT COUNT(*) FROM meetings WHERE status='SCHEDULED' "
                + "AND YEARWEEK(meeting_date,1)=YEARWEEK(CURDATE(),1)")));
        statPayments.setText(Fmt.compact(Db.dbl("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='PENDING'")));
        statLow.setText(Fmt.num(Db.num("SELECT COUNT(*) FROM v_low_stock")));

        for (Node tile : tiles.getChildren()) {
            if (tile.getId() == null) continue;
            Page p = Page.valueOf(tile.getId());
            boolean ok = Access.can(p);
            tile.getStyleClass().add(ok ? "tile" : "tile-locked");
            if (tile instanceof VBox box && box.getChildren().size() >= 2
                    && box.getChildren().get(0) instanceof Label title && box.getChildren().get(1) instanceof Label sub) {
                title.setText(p.text() + (ok ? "" : " - " + LanguageManager.x("locked")));
                sub.setText(LanguageManager.x(TILE_TEXT.getOrDefault(tile.getId(), "")));
            }
            tile.setOnMouseClicked(e -> AppShell.get().go(p));
        }

        progressHolder.getChildren().setAll(DashboardPage.progressCard());
        paymentsHolder.getChildren().setAll(DashboardPage.paymentsCard());
        meetingsHolder.getChildren().setAll(DashboardPage.meetingsCard());
        approvalsHolder.getChildren().setAll(DashboardPage.approvalsCard());
    }

    @FXML
    private void onNotifications() { AccountPopups.notifications(); }

    @FXML
    private void onNewProject() { ProjectsPage.addEdit(null, () -> AppShell.get().reload()); }
}
