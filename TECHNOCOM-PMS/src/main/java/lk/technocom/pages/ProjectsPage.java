package lk.technocom.pages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lk.technocom.service.FileService;
import lk.technocom.ui.*;
import lk.technocom.ui.Form.F;
import lk.technocom.ui.ListPage.K;
import lk.technocom.util.*;

import java.io.File;
import java.util.List;
import java.util.Map;

public final class ProjectsPage {

    private ProjectsPage() {}

    private static ListPage page;

    public static Node build() {
        page = new ListPage(Page.PROJECTS.text(),
                LanguageManager.x("Project List - Add/Edit, Detail, Site Requirements and Documents open as pop-ups"))
                .sql("SELECT p.project_id, p.project_code, p.name, p.client_id, c.name AS client, p.manager_id, "
                        + "COALESCE(m.full_name,'-') AS manager, p.progress, p.status FROM projects p "
                        + "JOIN clients c ON c.client_id=p.client_id LEFT JOIN users m ON m.user_id=p.manager_id "
                        + "WHERE p.is_active=TRUE AND p.organization_id=" + Session.getOrganizationId())
                .orderBy("t.project_code DESC")
                .stat("Active", "SELECT COUNT(*) FROM projects WHERE is_active=TRUE AND organization_id=" + Session.getOrganizationId() + " AND status IN ('PLANNING','ONGOING')")
                .stat("Completed", "SELECT COUNT(*) FROM projects WHERE is_active=TRUE AND organization_id=" + Session.getOrganizationId() + " AND status IN ('COMPLETED','HANDED_OVER','DLP')")
                .statMoney("Total contract value", "SELECT COALESCE(SUM(contract_value),0) FROM projects WHERE is_active=TRUE AND organization_id=" + Session.getOrganizationId())
                .search("project_code", "name", "client", "manager")
                .filterEnum("status", "Status", "PLANNING", "ONGOING", "COMPLETED", "HANDED_OVER", "DLP")
                .filterLookup("client_id", "Client", Q.CLIENTS)
                .filterLookup("manager_id", "Manager", Q.MANAGERS)
                .col("project_code", "Code", K.TEXT, 0.7).col("name", "Project", K.TEXT, 1.5)
                .col("client", "Client", K.TEXT, 1).col("manager", "Manager", K.TEXT, 1)
                .col("progress", "Progress", K.PROGRESS, 1.2).col("status", "Status", K.BADGE, 0.9)
                .act("Detail", ProjectsPage::detail)
                .act("Site Req.", r -> siteRequirements(r))
                .act("Docs", r -> documents(r))
                .crud(() -> addEdit(null, ProjectsPage::reload), r -> addEdit(r, ProjectsPage::reload),
                        r -> { int changed=Db.exec("UPDATE projects SET is_active=FALSE WHERE project_id=? AND organization_id=?",r.get("project_id"),Session.getOrganizationId()); if(changed==1)Audit.log("DEACTIVATE","Projects","Deactivated project "+r.get("project_code")); reload(); });
        return page.build();
    }

    private static void reload() { page.reload(); }

    // ------------------------------------------------------------------ Add / Edit project
    public static void addEdit(Map<String, Object> row, Runnable done) {
        boolean add = row == null;
        Map<String, Object> init = add ? null : Db.row("SELECT * FROM projects WHERE project_id=? AND organization_id=?", row.get("project_id"), Session.getOrganizationId());
        List<F> f = List.of(
                F.ro("project_code", "Project code").def(add ? Db.nextCode("projects", "project_code", "PRJ") : null),
                F.text("name", "Project name").req(),
                F.lookup("client_id", "Client", Q.CLIENTS).req(),
                F.lookup("manager_id", "Project manager", Q.MANAGERS),
                F.text("location", "Location").req(),
                F.en("project_type", "Type", "FACTORY", "HOSPITAL", "VILLA", "APARTMENT", "COMMERCIAL").req(),
                F.num("contract_value", "Contract value (Rs)").req(),
                F.en("status", "Status", "PLANNING", "ONGOING", "COMPLETED", "HANDED_OVER", "DLP").req().def("PLANNING"),
                F.date("start_date", "Start date").req(), F.date("end_date", "End date").req(),
                F.integer("progress", "Progress %").def(0),
                F.area("description", "Description"));
        Form.open(add ? LanguageManager.x("Add Project") : LanguageManager.x("Edit Project"), f, init, v -> {
            if (((java.math.BigDecimal) v.get("contract_value")).signum() <= 0) return "Contract value must be above 0.";
            if (((java.time.LocalDate) v.get("end_date")).isBefore((java.time.LocalDate) v.get("start_date")))
                return "End date cannot be before the start date.";
            Integer pr = (Integer) v.get("progress");
            if (pr == null) pr = 0;
            if (pr < 0 || pr > 100) return "Progress must be between 0 and 100.";
            v.put("progress", pr);
            if (add) {
                v.put("project_code", Db.nextCode("projects", "project_code", "PRJ"));
                v.put("created_by", Session.getUserId());
                v.put("organization_id", Session.getOrganizationId());
                long id = Db.insertMap("projects", v);
                Audit.log("CREATE", "Projects", "Created project " + v.get("project_code") + " (id " + id + ")");
            } else {
                if(Db.num("SELECT COUNT(*) FROM projects WHERE project_id=? AND organization_id=?",row.get("project_id"),Session.getOrganizationId())!=1)throw new SecurityException("Project is outside the current organisation.");
                Db.updateMap("projects", "project_id", row.get("project_id"), v);
                Audit.log("UPDATE", "Projects", "Updated project " + row.get("project_code"));
            }
            return null;
        }, done);
    }

    // ------------------------------------------------------------------ Project detail (+ staff)
    public static void detail(Map<String, Object> r) {
        Object pid = r.get("project_id");
        Map<String, Object> p = Db.row("SELECT p.*, c.name AS client, m.full_name AS manager FROM projects p "
                + "JOIN clients c ON c.client_id=p.client_id LEFT JOIN users m ON m.user_id=p.manager_id WHERE p.project_id=? AND p.organization_id=?", pid, Session.getOrganizationId());
        VBox body = new VBox(14, Detail.grid("Project code", p.get("project_code"), "Name", p.get("name"),
                "Client", p.get("client"), "Manager", p.get("manager"), "Location", p.get("location"),
                "Type", Fmt.nice(p.get("project_type")), "Status", Fmt.nice(p.get("status")),
                "Progress", p.get("progress") + "%", "Contract value", Fmt.money(p.get("contract_value")),
                "Start date", Fmt.str(p.get("start_date")), "End date", Fmt.str(p.get("end_date")),
                "Description", p.get("description")));

        body.getChildren().add(UI.lbl(LanguageManager.x("Site team"), "section-title"));
        VBox staff = new VBox(6);
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            staff.getChildren().clear();
            List<Map<String, Object>> rows = Db.rows("SELECT s.user_id, u.full_name, s.project_role, s.from_date FROM project_staff s "
                    + "JOIN users u ON u.user_id=s.user_id WHERE s.project_id=? ORDER BY s.project_role", pid);
            if (rows.isEmpty()) staff.getChildren().add(UI.muted(LanguageManager.x("No staff assigned")));
            for (Map<String, Object> s : rows) {
                HBox h = UI.row(UI.lbl(String.valueOf(s.get("full_name")), "kv-value"), UI.grow(),
                        UI.badge(String.valueOf(s.get("project_role"))), UI.muted(Fmt.str(s.get("from_date"))),
                        UI.link(LanguageManager.x("Remove"), () -> {
                            Db.exec("DELETE FROM project_staff WHERE project_id=? AND user_id=?", pid, s.get("user_id"));
                            fill[0].run();
                        }));
                staff.getChildren().add(h);
            }
        };
        fill[0].run();
        body.getChildren().add(staff);
        Stage[] st = new Stage[1];
        body.getChildren().add(new HBox(10, UI.grow(),
                UI.outline(LanguageManager.x("Assign staff"), () -> assignStaff(pid, fill[0])),
                UI.outline(LanguageManager.x("Site Requirements"), () -> siteRequirements(p)),
                UI.outline(LanguageManager.x("Documents"), () -> documents(p)),
                UI.primary(LanguageManager.x("Close"), () -> st[0].close())));
        body.setPadding(new Insets(18, 22, 20, 22));
        st[0] = Dialogs.show(LanguageManager.x("Project Detail"), body, 780, 640);
    }

    private static void assignStaff(Object pid, Runnable done) {
        Form.open(LanguageManager.x("Assign staff"), List.of(
                F.lookup("user_id", "Staff member", "SELECT u.user_id, CONCAT(u.full_name,' (',r.role_name,')') FROM users u "
                        + "JOIN roles r ON r.role_id=u.role_id WHERE u.status='ACTIVE' AND r.role_name IN "
                        + "('Site Engineer','QS Engineer','Stock Keeper') ORDER BY u.full_name").req(),
                F.en("project_role", "Role on project", "SITE_ENGINEER", "QS_ENGINEER", "STOCK_KEEPER").req(),
                F.date("from_date", "From date").req().def(java.time.LocalDate.now())), null, 520, v -> {
            long dup = Db.num("SELECT COUNT(*) FROM project_staff WHERE project_id=? AND user_id=?", pid, v.get("user_id"));
            if (dup > 0) return "This person is already on the project.";
            v.put("project_id", pid);
            Db.insertMap("project_staff", v);
            Audit.log("UPDATE", "Projects", "Assigned staff to project id " + pid);
            return null;
        }, done);
    }

    // ------------------------------------------------------------------ Site requirements popup
    public static void siteRequirements(Map<String, Object> p) {
        Object pid = p.get("project_id");
        VBox list = new VBox(8);
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            list.getChildren().clear();
            List<Map<String, Object>> rows = Db.rows("SELECT * FROM site_requirements WHERE project_id=? ORDER BY needed_by, requirement_id", pid);
            if (rows.isEmpty()) list.getChildren().add(UI.muted(LanguageManager.x("No site requirements yet")));
            for (Map<String, Object> s : rows) {
                Label t = new Label(String.valueOf(s.get("requirement")));
                t.getStyleClass().add("kv-value");
                String meta = (s.get("category") == null ? "" : s.get("category") + "  ")
                        + (s.get("quantity") == null ? "" : "Qty " + Fmt.num(s.get("quantity")) + "  ")
                        + (s.get("needed_by") == null ? "" : "Needed by " + Fmt.str(s.get("needed_by")));
                VBox txt = new VBox(2, t, UI.muted(meta));
                HBox h = UI.row(txt, UI.grow(), UI.badge(String.valueOf(s.get("status"))),
                        UI.link(LanguageManager.x("Edit"), () -> reqForm(pid, s, fill[0])),
                        UI.link(LanguageManager.x("Delete"), () -> {
                            if (Dialogs.confirm(LanguageManager.x("Delete this requirement?"))) {
                                Db.exec("DELETE FROM site_requirements WHERE requirement_id=?", s.get("requirement_id"));
                                fill[0].run();
                            }
                        }));
                VBox c = UI.card(h);
                c.setPadding(new Insets(10, 14, 10, 14));
                list.getChildren().add(c);
            }
        };
        fill[0].run();
        Stage[] st = new Stage[1];
        HBox bar = new HBox(10, UI.lbl(String.valueOf(p.get("project_code")) + "  " + p.get("name"), "section-title"), UI.grow(),
                UI.primary("+ " + LanguageManager.x("Add requirement"), () -> reqForm(pid, null, fill[0])),
                UI.outline(LanguageManager.x("Close"), () -> st[0].close()));
        bar.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(12, bar, list);
        body.setPadding(new Insets(16, 20, 20, 20));
        st[0] = Dialogs.show(LanguageManager.x("Site Requirements"), body, 720, 560);
    }

    private static void reqForm(Object pid, Map<String, Object> row, Runnable done) {
        Form.open(row == null ? LanguageManager.x("Add requirement") : LanguageManager.x("Edit requirement"), List.of(
                F.text("requirement", "Requirement").req().full(), F.text("category", "Category"),
                F.num("quantity", "Quantity"), F.date("needed_by", "Needed by"),
                F.en("status", "Status", "OPEN", "ORDERED", "FULFILLED").req().def("OPEN")), row, 600, v -> {
            if (v.get("quantity") != null && ((java.math.BigDecimal) v.get("quantity")).signum() <= 0) return "Quantity must be above 0.";
            if (row == null) {
                v.put("project_id", pid);
                Db.insertMap("site_requirements", v);
            } else {
                Db.updateMap("site_requirements", "requirement_id", row.get("requirement_id"), v);
            }
            return null;
        }, done);
    }

    // ------------------------------------------------------------------ Project documents popup
    public static void documents(Map<String, Object> p) {
        Object pid = p.get("project_id");
        VBox list = new VBox(8);
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            list.getChildren().clear();
            List<Map<String, Object>> rows = Db.rows("SELECT d.*, u.full_name FROM project_documents d "
                    + "JOIN users u ON u.user_id=d.uploaded_by WHERE d.project_id=? ORDER BY d.uploaded_at DESC", pid);
            if (rows.isEmpty()) list.getChildren().add(UI.muted(LanguageManager.x("No documents uploaded")));
            for (Map<String, Object> d : rows) {
                VBox txt = new VBox(2, UI.lbl(String.valueOf(d.get("name")), "kv-value"),
                        UI.muted(d.get("full_name") + " - " + Fmt.str(d.get("uploaded_at"))));
                HBox h = UI.row(txt, UI.grow(), UI.badge(String.valueOf(d.get("category"))),
                        UI.link(LanguageManager.x("Open"), () -> {
                            if (!FileService.open(String.valueOf(d.get("file_path"))))
                                Dialogs.error(LanguageManager.x("The file was not found on this computer."));
                        }),
                        UI.link(LanguageManager.x("Delete"), () -> {
                            if (Dialogs.confirm(LanguageManager.x("Delete this document?"))) {
                                Db.exec("DELETE FROM project_documents WHERE document_id=?", d.get("document_id"));
                                fill[0].run();
                            }
                        }));
                VBox c = UI.card(h);
                c.setPadding(new Insets(10, 14, 10, 14));
                list.getChildren().add(c);
            }
        };
        fill[0].run();
        Stage[] st = new Stage[1];
        HBox bar = new HBox(10, UI.lbl(String.valueOf(p.get("project_code")) + "  " + p.get("name"), "section-title"), UI.grow(),
                UI.primary("+ " + LanguageManager.x("Upload document"), () -> upload(pid, fill[0])),
                UI.outline(LanguageManager.x("Close"), () -> st[0].close()));
        bar.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(12, bar, list);
        body.setPadding(new Insets(16, 20, 20, 20));
        st[0] = Dialogs.show(LanguageManager.x("Project Documents"), body, 720, 560);
    }

    private static void upload(Object pid, Runnable done) {
        Form.open(LanguageManager.x("Upload document"), List.of(
                F.text("name", "Document name").req(),
                F.en("category", "Category", "CONTRACT", "DRAWING", "LETTER", "PHOTO", "OTHER").req(),
                F.file("file", "File").req().full()), null, 560, v -> {
            File file = (File) v.get("file");
            String path = FileService.store(file, "projects/" + pid);
            Db.insertMap("project_documents", new java.util.LinkedHashMap<>(Map.of(
                    "project_id", pid, "name", v.get("name"), "category", v.get("category"),
                    "file_path", path, "uploaded_by", Session.getUserId())));
            Audit.log("CREATE", "Projects", "Uploaded document " + v.get("name") + " to project id " + pid);
            return null;
        }, done);
    }
}
