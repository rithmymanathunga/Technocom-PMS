package lk.technocom.ui;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import lk.technocom.pages.*;
import lk.technocom.service.AuthService;
import lk.technocom.service.AutoMailer;
import lk.technocom.service.NotificationService;
import lk.technocom.util.Db;
import lk.technocom.util.LanguageManager;
import lk.technocom.util.Navigator;
import lk.technocom.util.Session;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Main window after login: sidebar + top bar + the active page. */
public final class AppShell {

    private static AppShell cur;

    private final Stage stage;
    // filled from /view/ShellView.fxml (the "= new ..." values are only used by the code fallback)
    @FXML private BorderPane root = new BorderPane();
    @FXML private StackPane content = new StackPane();
    @FXML private VBox navBox;
    @FXML private TextField globalSearch;
    @FXML private ComboBox<String> langBox;
    @FXML private Label roleLabel;
    @FXML private MenuButton userMenu;
    @FXML private MenuItem miProfile, miPassword, miHelp, miLogout;
    @FXML private Button alertsBtn = new Button();
    private final Map<Page, Button> navButtons = new EnumMap<>(Page.class);
    private Page current = Page.DASHBOARD;
    // Automatically end unattended desktop sessions after 30 minutes.
    private static final Duration IDLE_LIMIT = Duration.minutes(30);
    private PauseTransition idleTimer;
    private Timeline sessionMonitor;

    private AppShell(Stage stage) {
        this.stage = stage;
    }

    public static AppShell get() { return cur; }

    public static void open(Stage stage) { open(stage, Page.DASHBOARD); }

    public static void open(Stage stage, Page start) {
        Dialogs.setPrimary(stage);
        double w = stage.getScene() == null ? 1440 : Math.max(1280, stage.getScene().getWidth());
        double h = stage.getScene() == null ? 900 : Math.max(800, stage.getScene().getHeight());
        AppShell s = new AppShell(stage);
        cur = s;
        AutoMailer.start();
        try {
            NotificationService.refresh();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        s.build();
        Scene sc = new Scene(s.root, w, h);
        sc.getStylesheets().add(Dialogs.css());
        stage.setScene(sc);
        stage.setTitle("Technocom PMS");
        s.installIdleSessionTimeout(sc);
        s.installSessionMonitor();
        s.go(start);
        if (Session.isMustChange()) {
            Platform.runLater(() -> {
                Dialogs.info(LanguageManager.x("Change password"),
                        LanguageManager.x("For security you must set a new password before you continue."));
                AccountPopups.changePassword(true);
            });
        }
    }

    // ------------------------------------------------------------------ layout

    /** Loads the window from /view/ShellView.fxml; if that fails the old code version is used. */
    private void build() {
        try {
            FXMLLoader loader = new FXMLLoader(AppShell.class.getResource("/view/ShellView.fxml"));
            loader.setControllerFactory(c -> this);
            loader.load();
            fillFromFxml();
        } catch (Exception e) {
            e.printStackTrace();
            navButtons.clear();
            buildInCode();
        }
    }

    private void fillFromFxml() {
        // sidebar: translate, lock and wire the buttons (each button's Id is a Page name)
        boolean client = Session.isClient();
        Label group = null;
        boolean groupHasButton = false;
        for (Node n : navBox.getChildren()) {
            if (n instanceof Label l && l.getStyleClass().contains("nav-group")) {
                if (group != null && !groupHasButton) { group.setVisible(false); group.setManaged(false); }
                group = l;
                groupHasButton = false;
                l.setText(LanguageManager.x(l.getText()));
            } else if (n instanceof Button b && b.getId() != null) {
                Page p = Page.valueOf(b.getId());
                boolean ok = Access.can(p);
                b.setText(p.text() + (ok ? "" : "   \uD83D\uDD12"));
                if (!ok) b.getStyleClass().add("nav-locked");
                b.setOnAction(e -> go(p));
                navButtons.put(p, b);
                if (!ok && client) {                          // client portal: hide what they cannot open
                    b.setVisible(false);
                    b.setManaged(false);
                } else {
                    groupHasButton = true;
                }
            }
        }
        if (group != null && !groupHasButton) { group.setVisible(false); group.setManaged(false); }

        // top bar
        globalSearch.setPromptText(LanguageManager.x("Search projects, drawings, payments..."));
        globalSearch.setVisible(!client);                     // the search looks in every client's data
        globalSearch.setManaged(!client);
        globalSearch.setOnAction(e -> {
            String q = globalSearch.getText().trim();
            if (!q.isEmpty()) globalSearch(q);
        });

        langBox.getItems().setAll("EN", "SI", "TA");
        langBox.getSelectionModel().select(LanguageManager.getDbCode());
        langBox.setOnAction(e -> {
            String c = langBox.getValue();
            if (c == null || c.equalsIgnoreCase(LanguageManager.getCode())) return;
            LanguageManager.setLanguage(c.toLowerCase());
            UI.run(() -> Db.exec("UPDATE users SET language=? WHERE user_id=?", c, Session.getUserId()));
            open(stage, current);
        });

        alertsBtn.setOnAction(e -> AccountPopups.notifications());
        refreshAlerts();
        roleLabel.setText("· " + Fmt2.role());
        userMenu.setText(Session.getFullName() == null ? "User" : firstName(Session.getFullName()));
        miProfile.setText(LanguageManager.x("My profile"));
        miPassword.setText(LanguageManager.x("Change password"));
        miHelp.setText(Page.HELP.text());
        miLogout.setText(LanguageManager.x("Logout"));
    }

    @FXML private void onProfile() { AccountPopups.profile(); }
    @FXML private void onPassword() { AccountPopups.changePassword(false); }
    @FXML private void onHelp() { go(Page.HELP); }
    @FXML private void onLogout() { logout(); }

    /** The old way: the same window built with code. Only used when the FXML cannot be loaded. */
    private void buildInCode() {
        root.getStyleClass().add("shell");
        root.setLeft(sidebar());
        VBox right = new VBox(topbar(), content);
        VBox.setVgrow(content, Priority.ALWAYS);
        root.setCenter(right);
    }

    private Node sidebar() {
        VBox v = new VBox(2);
        v.setPadding(new Insets(18, 12, 18, 12));
        Label brand = UI.lbl("TECHNOCOM", "brand");
        brand.setPadding(new Insets(0, 0, 8, 14));
        v.getChildren().add(brand);

        group(v, "MAIN", Page.DASHBOARD, Page.ACCOUNT);
        group(v, "MASTER DATA", Page.USERS, Page.CLIENTS);
        group(v, "PROJECTS", Page.PROJECTS, Page.PLANNING, Page.DPR, Page.BOQ, Page.DRAWINGS, Page.SPECS, Page.MEETINGS);
        group(v, "STORES & FINANCE", Page.STOCK, Page.PAYMENTS, Page.PAYSUM, Page.HANDOVER);
        group(v, "OPERATIONS & CONTROL", Page.ENTERPRISE);
        group(v, "INSIGHTS", Page.REPORTS);

        ScrollPane sp = new ScrollPane(v);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("sidebar-scroll");
        VBox box = new VBox(sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        box.getStyleClass().add("sidebar");
        return box;
    }

    private void group(VBox v, String title, Page... pages) {
        boolean any = false;
        for (Page p : pages) any |= Access.can(p) || !Session.isClient();
        if (!any) return;                                   // client portal: hide menu groups they cannot use
        v.getChildren().add(UI.lbl(LanguageManager.x(title), "nav-group"));
        for (Page p : pages) {
            boolean ok = Access.can(p);
            if (!ok && Session.isClient()) continue;
            Button b = new Button(p.text() + (ok ? "" : "   \uD83D\uDD12"));
            b.getStyleClass().add("nav-btn");
            if (!ok) b.getStyleClass().add("nav-locked");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> go(p));
            navButtons.put(p, b);
            v.getChildren().add(b);
        }
    }

    private Node topbar() {
        TextField search = new TextField();
        search.setPromptText(LanguageManager.x("Search projects, drawings, payments..."));
        search.getStyleClass().add("global-search");
        search.setVisible(!Session.isClient());             // the search looks in every client's data
        search.setManaged(!Session.isClient());
        search.setOnAction(e -> {
            String q = search.getText().trim();
            if (!q.isEmpty()) globalSearch(q);
        });

        ComboBox<String> lang = new ComboBox<>();
        lang.getItems().addAll("EN", "SI", "TA");
        lang.getSelectionModel().select(LanguageManager.getDbCode());
        lang.getStyleClass().add("lang-box");
        lang.setOnAction(e -> {
            String c = lang.getValue();
            if (c == null || c.equalsIgnoreCase(LanguageManager.getCode())) return;
            LanguageManager.setLanguage(c.toLowerCase());
            UI.run(() -> Db.exec("UPDATE users SET language=? WHERE user_id=?", c, Session.getUserId()));
            open(stage, current);
        });

        alertsBtn.getStyleClass().add("top-link");
        alertsBtn.setOnAction(e -> AccountPopups.notifications());
        refreshAlerts();

        MenuButton user = new MenuButton(Session.getFullName() == null ? "User" : firstName(Session.getFullName()));
        user.getStyleClass().add("user-menu");
        MenuItem profile = new MenuItem(LanguageManager.x("My profile"));
        profile.setOnAction(e -> AccountPopups.profile());
        MenuItem pw = new MenuItem(LanguageManager.x("Change password"));
        pw.setOnAction(e -> AccountPopups.changePassword(false));
        MenuItem help = new MenuItem(Page.HELP.text());
        help.setOnAction(e -> go(Page.HELP));
        MenuItem out = new MenuItem(LanguageManager.x("Logout"));
        out.setOnAction(e -> logout());
        user.getItems().addAll(profile, pw, help, new SeparatorMenuItem(), out);

        Label role = UI.lbl("· " + Fmt2.role(), "muted");
        HBox bar = new HBox(14, search, UI.grow(), lang, alertsBtn, role, user);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("topbar");
        return bar;
    }

    private static String firstName(String full) {
        return full.trim().split("\\s+")[0];
    }

    /** Small inner helper so the role label is translated. */
    private static final class Fmt2 {
        static String role() { return Session.getRole() == null ? "" : LanguageManager.x(Session.getRole()); }
    }

    public void refreshAlerts() {
        long n = 0;
        try {
            n = NotificationService.unread();
        } catch (RuntimeException ignored) {
            // ignore
        }
        alertsBtn.setText(n + " " + LanguageManager.x("alerts"));
    }

    // ------------------------------------------------------------------ navigation

    public void go(Page p) {
        if (!Access.can(p)) {
            show(SystemPages.accessDenied(p));
            mark(p);
            return;
        }
        current = p;
        mark(p);
        Node n;
        try {
            n = switch (p) {
                case DASHBOARD -> DashboardPage.build();
                case ACCOUNT -> AccountPage.build();
                case USERS -> UsersPage.build();
                case CLIENTS -> ClientsPage.build();
                case PROJECTS -> ProjectsPage.build();
                case PLANNING -> ProjectPlanningPage.build();
                case DPR -> DprPage.build();
                case BOQ -> BoqPage.build();
                case DRAWINGS -> DrawingsPage.build();
                case SPECS -> SpecsPage.build();
                case MEETINGS -> MeetingsPage.build();
                case STOCK -> StockPage.build();
                case PAYMENTS -> PaymentsPage.build();
                case PAYSUM -> PaymentSummaryPage.build();
                case HANDOVER -> HandoverPage.build();
                case REPORTS -> ReportsAdminPage.build();
                case ENTERPRISE -> EnterpriseConsolePage.build();
                case HELP -> HelpPage.build();
            };
        } catch (RuntimeException e) {
            e.printStackTrace();
            n = SystemPages.error(e.getMessage());
        }
        show(n);
    }

    public void reload() { go(current); }

    private void show(Node n) {
        content.getChildren().setAll(n);
    }

    private void mark(Page p) {
        for (Map.Entry<Page, Button> e : navButtons.entrySet()) {
            e.getValue().getStyleClass().remove("nav-active");
            if (e.getKey() == p) e.getValue().getStyleClass().add("nav-active");
        }
    }

    /**
     * Starts a fresh inactivity countdown whenever the signed-in user interacts
     * with the application. This is a desktop safety control, not a replacement
     * for database-side permissions or server-side session expiry.
     */
    private void installIdleSessionTimeout(Scene scene) {
        if (idleTimer != null) idleTimer.stop();
        idleTimer = new PauseTransition(IDLE_LIMIT);
        idleTimer.setOnFinished(event -> {
            if (Session.getUserId() <= 0) return;
            AuthService.logout();
            Dialogs.info("Session ended",
                    "You were signed out after 30 minutes without activity. Please sign in again.");
            Navigator.show(content, "/view/LoginView.fxml");
        });
        javafx.event.EventHandler<MouseEvent> mouseActivity = event -> resetIdleTimer();
        javafx.event.EventHandler<KeyEvent> keyActivity = event -> resetIdleTimer();
        scene.addEventFilter(MouseEvent.ANY, mouseActivity);
        scene.addEventFilter(KeyEvent.ANY, keyActivity);
        resetIdleTimer();
    }

    /** Check server-side revocation and update the session heartbeat once per minute. */
    private void installSessionMonitor() {
        if (sessionMonitor != null) sessionMonitor.stop();
        sessionMonitor = new Timeline(new KeyFrame(Duration.minutes(1), event -> {
            if (Session.getUserId() <= 0) return;
            try {
                if (!lk.technocom.service.SessionManagementService.touchCurrent()) {
                    if (idleTimer != null) idleTimer.stop();
                    sessionMonitor.stop();
                    AuthService.logout();
                    Dialogs.info("Session revoked", "Your session was ended by an administrator. Please sign in again.");
                    Navigator.show(content, "/view/LoginView.fxml");
                }
            } catch (RuntimeException ex) {
                // A temporary database outage must not silently discard a valid local session.
                ex.printStackTrace();
            }
        }));
        sessionMonitor.setCycleCount(Timeline.INDEFINITE);
        sessionMonitor.play();
    }

    private void resetIdleTimer() {
        if (idleTimer != null && Session.getUserId() > 0) idleTimer.playFromStart();
    }

    public void logout() {
        if (idleTimer != null) idleTimer.stop();
        if (sessionMonitor != null) sessionMonitor.stop();
        AuthService.logout();
        Navigator.show(content, "/view/LoginView.fxml");
    }

    // ------------------------------------------------------------------ global search

    private void globalSearch(String q) {
        String like = "%" + q + "%";
        List<Map<String, Object>> rows = Db.rows(
                "SELECT 'Project' AS kind, project_code AS code, name AS title, 'PROJECTS' AS page FROM projects "
                        + "WHERE project_code LIKE ? OR name LIKE ? "
                        + "UNION ALL SELECT 'Drawing', drawing_no, title, 'DRAWINGS' FROM drawings WHERE drawing_no LIKE ? OR title LIKE ? "
                        + "UNION ALL SELECT 'Payment', payment_no, CONCAT(type,' - ',amount), 'PAYMENTS' FROM payments WHERE payment_no LIKE ? "
                        + "UNION ALL SELECT 'Client', client_code, name, 'CLIENTS' FROM clients WHERE client_code LIKE ? OR name LIKE ? "
                        + "UNION ALL SELECT 'Stock', item_code, name, 'STOCK' FROM stock_items WHERE item_code LIKE ? OR name LIKE ? "
                        + "LIMIT 60",
                like, like, like, like, like, like, like, like, like);
        TableView<Map<String, Object>> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.getStyleClass().add("grid");
        for (String[] c : new String[][]{{"kind", "Type"}, {"code", "Code"}, {"title", "Title"}}) {
            TableColumn<Map<String, Object>, String> tc = new TableColumn<>(LanguageManager.x(c[1]).toUpperCase());
            tc.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(String.valueOf(cd.getValue().get(c[0]))));
            t.getColumns().add(tc);
        }
        t.getItems().addAll(rows);
        t.setPlaceholder(new Label(LanguageManager.x("No records found")));
        VBox box = new VBox(10, UI.sub(LanguageManager.x("Double-click a result to open its page.")), t);
        box.setPadding(new Insets(16));
        VBox.setVgrow(t, Priority.ALWAYS);
        Stage[] st = new Stage[1];
        t.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2 && t.getSelectionModel().getSelectedItem() != null) {
                Page target = Page.valueOf(t.getSelectionModel().getSelectedItem().get("page").toString());
                st[0].close();
                go(target);
            }
        });
        st[0] = Dialogs.show(LanguageManager.x("Search results") + ": " + q, box, 620, 460);
    }
}
