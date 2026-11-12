package lk.technocom.pages;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lk.technocom.service.PasswordService;
import lk.technocom.ui.*;
import lk.technocom.ui.Form.F;
import lk.technocom.util.*;

import java.util.List;
import java.util.Map;

/** My profile, change password and notifications popups. */
public final class AccountPopups {

    private AccountPopups() {}

    // ------------------------------------------------------------------ My profile (/view/ProfileView.fxml)
    public static void profile() {
        Map<String, Object> u = Db.row("SELECT u.*, r.role_name FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.user_id=?",
                Session.getUserId());
        if (u == null) return;
        Profile p = new Profile(u);
        p.stage = Dialogs.show(LanguageManager.x("My profile"), Views.load("/view/ProfileView.fxml", p), 640, 460);
    }

    public static final class Profile implements Views.Ready {
        @FXML private Label valName, valRole, valUser, valEmail, valPhone, valNic, valAddress, valLang, valLast, valSince;
        private final Map<String, Object> u;
        private Stage stage;

        Profile(Map<String, Object> u) { this.u = u; }

        @Override
        public void ready() {
            valName.setText(Fmt.str(u.get("full_name")));
            valRole.setText(LanguageManager.x(String.valueOf(u.get("role_name"))));
            valUser.setText(Fmt.str(u.get("username")));
            valEmail.setText(Fmt.str(u.get("email")));
            valPhone.setText(Fmt.str(u.get("phone")));
            valNic.setText(Fmt.str(u.get("nic")));
            valAddress.setText(Fmt.str(u.get("address")));
            valLang.setText(Fmt.str(u.get("language")));
            valLast.setText(Fmt.str(u.get("last_login")));
            valSince.setText(Fmt.str(u.get("created_at")));
        }

        @FXML
        private void onEdit() {
            stage.close();
            Form.open(LanguageManager.x("My profile"),
                    List.of(F.text("phone", "Phone").req(), F.text("address", "Address").full()), u,
                    v -> {
                        String err = Val.phone((String) v.get("phone"));
                        if (err != null) return err;
                        Db.updateMap("users", "user_id", Session.getUserId(), v);
                        Audit.log("UPDATE", "Account", "Profile updated");
                        return null;
                    }, AccountPopups::profile);
        }

        @FXML private void onClose() { stage.close(); }
    }

    // ------------------------------------------------------------------ Change password
    public static void changePassword(boolean forced) {
        List<F> f = List.of(F.pass("current", "Current password").req().full(),
                F.pass("next", "New password").req().full(),
                F.pass("confirm", "Confirm new password").req().full());
        Form.open(LanguageManager.x("Change password"), f, null, 520, v -> {
            String next = (String) v.get("next");
            if (!next.equals(v.get("confirm"))) return "The two new passwords do not match.";
            String err = PasswordService.changeOwn((String) v.get("current"), next);
            if (err == null) Dialogs.info(LanguageManager.x("Change password"), LanguageManager.x("Password updated."));
            return err;
        }, null);
    }

    // ------------------------------------------------------------------ Notifications (/view/NotificationsView.fxml)
    public static void notifications() {
        Notifications n = new Notifications();
        n.stage = Dialogs.show(LanguageManager.x("Notifications"), Views.load("/view/NotificationsView.fxml", n), 680, 560);
    }

    public static final class Notifications implements Views.Ready {
        @FXML private VBox list;
        private Stage stage;

        @Override
        public void ready() { fill(); }

        private void fill() {
            list.getChildren().clear();
            List<Map<String, Object>> rows = Db.rows("SELECT notification_id, category, message, is_read, created_at "
                    + "FROM notifications WHERE user_id=? ORDER BY created_at DESC, notification_id DESC LIMIT 60", Session.getUserId());
            if (rows.isEmpty()) list.getChildren().add(UI.muted(LanguageManager.x("No notifications")));
            for (Map<String, Object> r : rows) {
                boolean read = Boolean.TRUE.equals(r.get("is_read"));
                Label msg = new Label(String.valueOf(r.get("message")));
                msg.setWrapText(true);
                if (!read) msg.setStyle("-fx-font-weight: bold;");
                VBox text = new VBox(2, msg, UI.muted(Fmt.str(r.get("created_at"))));
                HBox.setHgrow(text, Priority.ALWAYS);
                HBox row = new HBox(12, UI.badge(String.valueOf(r.get("category"))), text);
                row.setAlignment(Pos.CENTER_LEFT);
                if (!read) {
                    row.getChildren().add(UI.link(LanguageManager.x("Mark read"), () -> {
                        Db.exec("UPDATE notifications SET is_read=TRUE WHERE notification_id=?", r.get("notification_id"));
                        fill();
                        refreshShell();
                    }));
                }
                VBox card = UI.card(row);
                card.setPadding(new Insets(10, 14, 10, 14));
                list.getChildren().add(card);
            }
        }

        @FXML
        private void onMarkAll() {
            Db.exec("UPDATE notifications SET is_read=TRUE WHERE user_id=?", Session.getUserId());
            fill();
            refreshShell();
        }

        @FXML private void onClose() { stage.close(); }
    }

    private static void refreshShell() {
        if (AppShell.get() != null) AppShell.get().refreshAlerts();
    }
}
