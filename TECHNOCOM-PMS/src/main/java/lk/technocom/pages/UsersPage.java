package lk.technocom.pages;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lk.technocom.service.EmailService;
import lk.technocom.service.PasswordService;
import lk.technocom.ui.*;
import lk.technocom.ui.Form.F;
import lk.technocom.ui.ListPage.K;
import lk.technocom.util.*;

import java.util.List;
import java.util.Map;

public final class UsersPage {

    private UsersPage() {}

    private static ListPage page;

    public static Node build() {
        Access.requireAdmin();
        page = new ListPage(Page.USERS.text(), LanguageManager.x("User List - Add/Edit and Detail open as pop-ups"))
                .sql("SELECT u.user_id, u.full_name, u.email, r.role_name AS role, u.role_id, u.status, u.last_login "
                        + "FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.organization_id=" + Session.getOrganizationId())
                .orderBy("t.full_name")
                .stat("Users", "SELECT COUNT(*) FROM users WHERE organization_id=" + Session.getOrganizationId())
                .stat("Roles", "SELECT COUNT(*) FROM roles")
                .stat("Inactive", "SELECT COUNT(*) FROM users WHERE status<>'ACTIVE' AND organization_id=" + Session.getOrganizationId())
                .search("full_name", "email", "role")
                .filterLookup("role_id", "Role", Q.ROLES)
                .filterEnum("status", "Status", "ACTIVE", "INACTIVE", "LOCKED")
                .col("full_name", "Name", K.TEXT, 1.2).col("email", "Email", K.TEXT, 1.4)
                .col("role", "Role", K.TEXT, 1.1).col("status", "Status", K.BADGE, 0.8)
                .col("last_login", "Last login", K.REL, 0.8)
                .act("View", UsersPage::detail)
                .act("Reset", UsersPage::reset)
                .act("End Sessions", UsersPage::endSessions)
                .crud(() -> addEdit(null, UsersPage::reload), r -> addEdit(r, UsersPage::reload), UsersPage::delete);
        return page.build();
    }

    private static void reload() { page.reload(); }

    private static void delete(Map<String, Object> r) {
        Access.requireAdmin();
        if (String.valueOf(r.get("user_id")).equals(String.valueOf(Session.getUserId())))
            throw new IllegalStateException(LanguageManager.x("You cannot delete your own account."));
        Crud.delete("users", "user_id", r.get("user_id"), "Users", "user " + r.get("full_name"));
    }

    public static void addEdit(Map<String, Object> row, Runnable done) {
        Access.requireAdmin();
        boolean add = row == null;
        Map<String, Object> init = add ? null : Db.row("SELECT * FROM users WHERE user_id=? AND organization_id=?", row.get("user_id"), Session.getOrganizationId());
        List<F> f = List.of(
                F.text("full_name", "Full name").req(), F.text("nic", "NIC").req(),
                F.text("phone", "Phone").req(), F.text("email", "Email").req(),
                F.text("username", "Username").req(), F.lookup("role_id", "Role", Q.ROLES).req(),
                F.en("status", "Status", "ACTIVE", "INACTIVE", "LOCKED").req().def("ACTIVE"),
                F.en("language", "Language", "EN", "SI", "TA").req().def("EN"),
                F.lookup("client_id", "Linked client (Client / Developer role only)",
                        "SELECT client_id, name FROM clients WHERE is_active=TRUE AND organization_id=" + Session.getOrganizationId() + " ORDER BY name"),
                F.text("address", "Address").full());
        Form.open(add ? LanguageManager.x("Add User") : LanguageManager.x("Edit User"), f, init, v -> {
            String err = Val.first(Val.nic((String) v.get("nic")), Val.phone((String) v.get("phone")),
                    Val.email((String) v.get("email")));
            if (err != null) return err;
            Object roleName = Db.scalar("SELECT role_name FROM roles WHERE role_id=?", v.get("role_id"));
            boolean clientRole = "Client".equals(roleName) || "Developer".equals(roleName);
            if (clientRole && v.get("client_id") == null) return LanguageManager.x("Select the client this user belongs to.");
            if (!clientRole) v.put("client_id", null);
            if (add) {
                String tmp = PasswordService.randomPassword();
                v.put("password_hash", PasswordService.hash(tmp));
                v.put("must_change_password", true);
                v.put("organization_id", Session.getOrganizationId());
                long id = Db.insertMap("users", v);
                Audit.log("CREATE", "Users", "Created user " + v.get("username") + " (id " + id + ")");
                EmailService.send((String) v.get("email"), "Your Technocom PMS account",
                        "Hello " + v.get("full_name") + ",\n\nYour Technocom PMS account has been created"
                                + (clientRole ? " (client portal: you can follow your projects, progress, drawings, meetings and payments)" : "")
                                + ".\nUsername: " + v.get("username")
                                + "\nTemporary password: " + tmp + "\nYou must change it at first login.", "WELCOME");
                Dialogs.info(LanguageManager.x("User created"), LanguageManager.x("Temporary password") + ": " + tmp
                        + "\n\n" + LanguageManager.x("Give it to the user. They must change it at first login."));
            } else {
                if(Db.num("SELECT COUNT(*) FROM users WHERE user_id=? AND organization_id=?",row.get("user_id"),Session.getOrganizationId())!=1)throw new SecurityException("User is outside the current organisation.");
                if ("ACTIVE".equals(v.get("status"))) v.put("failed_attempts", 0);
                Db.updateMap("users", "user_id", row.get("user_id"), v);
                Audit.log("UPDATE", "Users", "Updated user " + v.get("username"));
            }
            return null;
        }, done);
    }

    private static void endSessions(Map<String, Object> r) {
        Access.requireAdmin();
        int id = ((Number) r.get("user_id")).intValue();
        if (!Dialogs.confirm("End all active sessions for " + r.get("full_name") + "?")) return;
        int ended = lk.technocom.service.SessionManagementService.revokeAll(id);
        Dialogs.info("Sessions ended", ended + " active session(s) revoked. The user will need to sign in again.");
        reload();
    }

    private static void reset(Map<String, Object> r) {
        Access.requireAdmin();
        if (!Dialogs.confirm(LanguageManager.x("Reset the password for") + " " + r.get("full_name") + "?")) return;
        String tmp = PasswordService.adminReset(((Number) r.get("user_id")).intValue());
        EmailService.send(String.valueOf(r.get("email")), "Technocom PMS password reset",
                "Your temporary password is: " + tmp + "\nYou must change it at next login.", "RESET_CODE");
        Dialogs.info(LanguageManager.x("Password reset"), LanguageManager.x("Temporary password") + ": " + tmp);
        reload();
    }

    public static void detail(Map<String, Object> r) {
        Access.requireAdmin();
        Map<String, Object> u = Db.row("SELECT u.*, r.role_name FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.user_id=? AND u.organization_id=?",
                r.get("user_id"), Session.getOrganizationId());
        VBox body = new VBox(14,
                Detail.grid("Name", u.get("full_name"), "Role", u.get("role_name"), "Username", u.get("username"),
                        "Email", u.get("email"), "Phone", u.get("phone"), "NIC", u.get("nic"),
                        "Status", Fmt.nice(u.get("status")), "Language", u.get("language"),
                        "Failed attempts", u.get("failed_attempts"), "Last login", Fmt.str(u.get("last_login")),
                        "Address", u.get("address"), "Created", Fmt.str(u.get("created_at"))));
        List<Map<String, Object>> log = Db.rows("SELECT action, module, details, created_at FROM audit_logs "
                + "WHERE user_id=? ORDER BY audit_id DESC LIMIT 8", u.get("user_id"));
        body.getChildren().add(UI.lbl(LanguageManager.x("Recent activity"), "section-title"));
        if (log.isEmpty()) body.getChildren().add(UI.muted(LanguageManager.x("No activity yet")));
        for (Map<String, Object> a : log) {
            body.getChildren().add(UI.muted(Fmt.str(a.get("created_at")) + "  " + a.get("action") + "  "
                    + a.get("module") + "  " + (a.get("details") == null ? "" : a.get("details"))));
        }
        Stage[] st = new Stage[1];
        body.getChildren().add(new HBox(10, UI.grow(), UI.primary(LanguageManager.x("Close"), () -> st[0].close())));
        body.setPadding(new Insets(18, 22, 20, 22));
        st[0] = Dialogs.show(LanguageManager.x("User Detail"), body, 700, 560);
    }
}
