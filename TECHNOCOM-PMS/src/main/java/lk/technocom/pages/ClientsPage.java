package lk.technocom.pages;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lk.technocom.ui.*;
import lk.technocom.ui.Form.F;
import lk.technocom.ui.ListPage.K;
import lk.technocom.util.*;

import java.util.List;
import java.util.Map;

public final class ClientsPage {

    private ClientsPage() {}

    private static ListPage page;

    public static Node build() {
        page = new ListPage(Page.CLIENTS.text(), LanguageManager.x("Client List - Add/Edit and Detail open as pop-ups"))
                .sql("SELECT c.client_id, c.client_code, c.name, c.client_type, c.contact_person, c.phone, c.email, c.is_active, "
                        + "(SELECT COUNT(*) FROM projects p WHERE p.client_id=c.client_id) AS projects, "
                        + "IF(c.is_active,'ACTIVE','INACTIVE') AS status FROM clients c WHERE c.organization_id=" + Session.getOrganizationId())
                .orderBy("t.client_code")
                .stat("Clients", "SELECT COUNT(*) FROM clients WHERE is_active=TRUE AND organization_id=" + Session.getOrganizationId())
                .stat("Developers", "SELECT COUNT(*) FROM clients WHERE client_type='DEVELOPER' AND is_active=TRUE AND organization_id=" + Session.getOrganizationId())
                .stat("Building Owners", "SELECT COUNT(*) FROM clients WHERE client_type='BUILDING_OWNER' AND is_active=TRUE AND organization_id=" + Session.getOrganizationId())
                .search("client_code", "name", "contact_person", "email", "phone")
                .filterEnum("client_type", "Type", "DEVELOPER", "BUILDING_OWNER")
                .filterEnum("status", "Status", "ACTIVE", "INACTIVE")
                .col("client_code", "Code", K.TEXT, 0.7).col("name", "Client", K.TEXT, 1.4)
                .col("client_type", "Type", K.NICE, 1).col("contact_person", "Contact", K.TEXT, 1.1)
                .col("phone", "Phone", K.TEXT, 0.9).col("projects", "Projects", K.NUM, 0.6)
                .col("status", "Status", K.BADGE, 0.7)
                .act("View", ClientsPage::detail)
                .actIf("Deactivate", ClientsPage::toggle, r -> Boolean.TRUE.equals(r.get("is_active")) || asInt(r.get("is_active")) == 1)
                .actIf("Activate", ClientsPage::toggle, r -> !(Boolean.TRUE.equals(r.get("is_active")) || asInt(r.get("is_active")) == 1))
                .crud(() -> addEdit(null, ClientsPage::reload), r -> addEdit(r, ClientsPage::reload),
                        r -> { int changed=Db.exec("UPDATE clients SET is_active=FALSE WHERE client_id=? AND organization_id=?",r.get("client_id"),Session.getOrganizationId()); if(changed==1)Audit.log("DEACTIVATE","Clients","Deactivated client "+r.get("client_code")); reload(); });
        return page.build();
    }

    private static int asInt(Object o) { return o instanceof Number n ? n.intValue() : 0; }

    private static void reload() { page.reload(); }

    private static void toggle(Map<String, Object> r) {
        boolean active = Boolean.TRUE.equals(r.get("is_active")) || asInt(r.get("is_active")) == 1;
        if (!Dialogs.confirm((active ? LanguageManager.x("Deactivate") : LanguageManager.x("Activate")) + " " + r.get("name") + "?")) return;
        Db.exec("UPDATE clients SET is_active=? WHERE client_id=? AND organization_id=?", !active, r.get("client_id"), Session.getOrganizationId());
        Audit.log("UPDATE", "Clients", (active ? "Deactivated " : "Activated ") + r.get("client_code"));
        reload();
    }

    public static void addEdit(Map<String, Object> row, Runnable done) {
        boolean add = row == null;
        Map<String, Object> init = add ? null : Db.row("SELECT * FROM clients WHERE client_id=? AND organization_id=?", row.get("client_id"), Session.getOrganizationId());
        List<F> f = List.of(
                add ? F.ro("client_code", "Client code").def(Db.nextCode("clients", "client_code", "CL")) : F.ro("client_code", "Client code"),
                F.text("name", "Client name").req(),
                F.en("client_type", "Type", "DEVELOPER", "BUILDING_OWNER").req(),
                F.text("contact_person", "Contact person").req(),
                F.text("phone", "Phone").req(), F.text("email", "Email").req(),
                F.text("address", "Address").full());
        Form.open(add ? LanguageManager.x("Add Client") : LanguageManager.x("Edit Client"), f, init, v -> {
            String err = Val.first(Val.phone((String) v.get("phone")), Val.email((String) v.get("email")));
            if (err != null) return err;
            if (add) {
                v.put("client_code", Db.nextCode("clients", "client_code", "CL"));
                v.put("organization_id", Session.getOrganizationId());
                long id = Db.insertMap("clients", v);
                Audit.log("CREATE", "Clients", "Created client " + v.get("client_code") + " (id " + id + ")");
            } else {
                if(Db.num("SELECT COUNT(*) FROM clients WHERE client_id=? AND organization_id=?",row.get("client_id"),Session.getOrganizationId())!=1)throw new SecurityException("Client is outside the current organisation.");
                Db.updateMap("clients", "client_id", row.get("client_id"), v);
                Audit.log("UPDATE", "Clients", "Updated client " + row.get("client_code"));
            }
            return null;
        }, done);
    }

    public static void detail(Map<String, Object> r) {
        Map<String, Object> c = Db.row("SELECT * FROM clients WHERE client_id=? AND organization_id=?", r.get("client_id"), Session.getOrganizationId());
        VBox body = new VBox(14, Detail.grid("Client code", c.get("client_code"), "Name", c.get("name"),
                "Type", Fmt.nice(c.get("client_type")), "Contact person", c.get("contact_person"),
                "Phone", c.get("phone"), "Email", c.get("email"), "Address", c.get("address"),
                "Status", Fmt.nice(Boolean.TRUE.equals(c.get("is_active")) ? "ACTIVE" : "INACTIVE"), "Created", Fmt.str(c.get("created_at"))));
        body.getChildren().add(UI.lbl(LanguageManager.x("Projects of this client"), "section-title"));
        List<Map<String, Object>> ps = Db.rows("SELECT project_code, name, status, progress, contract_value FROM projects "
                + "WHERE client_id=? ORDER BY project_code", c.get("client_id"));
        if (ps.isEmpty()) body.getChildren().add(UI.muted(LanguageManager.x("No projects yet")));
        for (Map<String, Object> p : ps) {
            HBox h = UI.row(UI.lbl(p.get("project_code") + "  " + p.get("name"), "kv-value"), UI.grow(),
                    UI.muted(p.get("progress") + "%"), UI.badge(String.valueOf(p.get("status"))),
                    UI.muted(Fmt.money(p.get("contract_value"))));
            body.getChildren().add(h);
        }
        Stage[] st = new Stage[1];
        body.getChildren().add(new HBox(10, UI.grow(), UI.primary(LanguageManager.x("Close"), () -> st[0].close())));
        body.setPadding(new Insets(18, 22, 20, 22));
        st[0] = Dialogs.show(LanguageManager.x("Client Detail"), body, 720, 540);
    }
}
