package lk.technocom.pages;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lk.technocom.service.AutoMailer;
import lk.technocom.service.EmailService;
import lk.technocom.ui.*;
import lk.technocom.ui.Form.F;
import lk.technocom.ui.ListPage.K;
import lk.technocom.util.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PaymentsPage {

    private PaymentsPage() {}

    private static ListPage page;

    public static Node build() {
        page = new ListPage(Page.PAYMENTS.text(), LanguageManager.x("Payment List - Add/Edit, Invoice and Receipt open as pop-ups"))
                .sql("SELECT p.payment_id, p.payment_no, p.project_id, CONCAT(pr.project_code,' ',pr.name) AS project, p.type, p.amount, "
                        + "p.due_date, p.paid_date, p.method, p.reference_no, p.display_status AS status, p.status AS raw_status "
                        + "FROM v_payments_with_status p JOIN projects pr ON pr.project_id=p.project_id")
                .orderBy("t.due_date DESC, t.payment_no DESC")
                .stat("Payments", "SELECT COUNT(*) FROM payments")
                .statMoney("Pending amount", "SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='PENDING'")
                .stat("Overdue", "SELECT COUNT(*) FROM payments WHERE status='PENDING' AND due_date < CURDATE()")
                .statMoney("Paid so far", "SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='PAID'")
                .search("payment_no", "project", "reference_no")
                .filterLookup("project_id", "Project", Q.PROJECTS)
                .filterEnum("type", "Type", "ADVANCE", "PROGRESS", "RETENTION", "FINAL")
                .filterEnum("status", "Status", "PENDING", "PAID", "OVERDUE")
                .col("payment_no", "Payment", K.TEXT, 0.8).col("project", "Project", K.TEXT, 1.5)
                .col("type", "Type", K.NICE, 0.8).col("amount", "Amount", K.MONEY, 1)
                .col("due_date", "Due", K.DATE, 0.8).col("status", "Status", K.BADGE, 0.8)
                .actIf("Mark paid", r -> markPaid(r, PaymentsPage::reload), r -> "PENDING".equals(r.get("raw_status")))
                .act("Invoice", r -> invoice(r, PaymentsPage::reload))
                .actIf("Receipt", PaymentsPage::receipt, r -> "PAID".equals(r.get("raw_status")))
                .crud(() -> addEdit(null, PaymentsPage::reload), PaymentsPage::edit, PaymentsPage::delete);
        return page.build();
    }

    private static void reload() { page.reload(); }

    private static void edit(Map<String, Object> r) {
        if (!"PENDING".equals(r.get("raw_status"))) {
            Dialogs.error(LanguageManager.x("Only pending payments can be edited."));
            return;
        }
        addEdit(r, PaymentsPage::reload);
    }

    private static void delete(Map<String, Object> r) {
        if (!"PENDING".equals(r.get("raw_status")) || !Access.canApprove()) {
            Dialogs.error(LanguageManager.x("Only a pending payment can be deleted, by the Admin or Project Manager."));
            return;
        }
        Crud.deleteTree("payments", "payment_id", r.get("payment_id"), "Payments", String.valueOf(r.get("payment_no")), "invoices");
    }

    // ------------------------------------------------------------------ Add / Edit payment
    public static void addEdit(Map<String, Object> row, Runnable done) {
        boolean add = row == null;
        Map<String, Object> init = add ? null : Db.row("SELECT * FROM payments WHERE payment_id=?", row.get("payment_id"));
        List<F> f = List.of(
                F.ro("payment_no", "Payment no").def(add ? Db.nextCode("payments", "payment_no", "PAY") : null),
                F.lookup("project_id", "Project", Q.PROJECTS).req(),
                F.en("type", "Type", "ADVANCE", "PROGRESS", "RETENTION", "FINAL").req(),
                F.num("amount", "Amount (Rs)").req(), F.date("due_date", "Due date").req());
        Form.open(add ? LanguageManager.x("Add Payment") : LanguageManager.x("Edit Payment"), f, init, 620, v -> {
            if (((BigDecimal) v.get("amount")).signum() <= 0) return "Amount must be above 0.";
            if (add) {
                v.put("payment_no", Db.nextCode("payments", "payment_no", "PAY"));
                v.put("created_by", Session.getUserId());
                Db.insertMap("payments", v);
                Audit.log("CREATE", "Payments", "Created " + v.get("payment_no"));
            } else {
                Db.updateMap("payments", "payment_id", row.get("payment_id"), v);
                Audit.log("UPDATE", "Payments", "Updated " + row.get("payment_no"));
            }
            return null;
        }, done);
    }

    private static void markPaid(Map<String, Object> r, Runnable done) {
        Form.open(LanguageManager.x("Mark paid") + " - " + r.get("payment_no"), List.of(
                F.date("paid_date", "Paid date").req().def(LocalDate.now()),
                F.en("method", "Method", "CASH", "CHEQUE", "BANK_TRANSFER").req(),
                F.text("reference_no", "Reference / cheque no").full()), null, 560, v -> {
            if (((LocalDate) v.get("paid_date")).isAfter(LocalDate.now())) return "Paid date cannot be in the future.";
            if (!"CASH".equals(v.get("method")) && v.get("reference_no") == null) return "Reference number is required for cheque / bank transfer.";
            Db.tx(c -> {
                Db.exec(c, "UPDATE payments SET status='PAID', paid_date=?, method=?, reference_no=? WHERE payment_id=?",
                        v.get("paid_date"), v.get("method"), v.get("reference_no"), r.get("payment_id"));
                Db.exec(c, "UPDATE invoices SET status='PAID' WHERE payment_id=? AND status='ISSUED'", r.get("payment_id"));
            });
            Audit.log("UPDATE", "Payments", "Marked paid " + r.get("payment_no"));
            AutoMailer.toClient(r.get("project_id"), "RECEIPT", "Receipt for payment " + r.get("payment_no"),
                    "Thank you. We received " + Fmt.money(r.get("amount")) + " on " + Fmt.str(v.get("paid_date")) + " by "
                            + Fmt.nice(v.get("method")) + ". Payment " + r.get("payment_no") + " for " + r.get("project") + ".");
            return null;
        }, done);
    }

    // ------------------------------------------------------------------ Invoice
    public static void invoice(Map<String, Object> r, Runnable done) {
        Map<String, Object> inv = Db.row("SELECT * FROM invoices WHERE payment_id=?", r.get("payment_id"));
        if (inv == null) {
            Form.open(LanguageManager.x("Create invoice") + " - " + r.get("payment_no"), List.of(
                    F.date("issue_date", "Issue date").req().def(LocalDate.now()),
                    F.num("tax_percent", "Tax %").def(0), F.text("notes", "Notes").full()), null, 560, v -> {
                BigDecimal tax = v.get("tax_percent") == null ? BigDecimal.ZERO : (BigDecimal) v.get("tax_percent");
                if (tax.signum() < 0 || tax.compareTo(BigDecimal.valueOf(100)) > 0) return "Tax must be between 0 and 100.";
                v.put("tax_percent", tax);
                v.put("invoice_no", Db.nextCode("invoices", "invoice_no", "INV"));
                v.put("payment_id", r.get("payment_id"));
                v.put("status", "PAID".equals(r.get("raw_status")) ? "PAID" : "ISSUED");
                v.put("created_by", Session.getUserId());
                Db.insertMap("invoices", v);
                Audit.log("CREATE", "Payments", "Invoice for " + r.get("payment_no"));
                return null;
            }, () -> {
                if (Db.row("SELECT 1 FROM invoices WHERE payment_id=?", r.get("payment_id")) != null) showInvoice(r);
                if (done != null) done.run();
            });
        } else {
            showInvoice(r);
        }
    }

    private static void showInvoice(Map<String, Object> r) {
        Map<String, Object> d = Db.row("SELECT i.*, p.payment_no, p.type, p.amount, p.due_date, pr.project_code, pr.name AS project, "
                + "c.name AS client, c.address AS client_address, c.email AS client_email FROM invoices i "
                + "JOIN payments p ON p.payment_id=i.payment_id JOIN projects pr ON pr.project_id=p.project_id "
                + "JOIN clients c ON c.client_id=pr.client_id WHERE i.payment_id=?", r.get("payment_id"));
        BigDecimal amount = Fmt.dec(d.get("amount"));
        BigDecimal tax = amount.multiply(Fmt.dec(d.get("tax_percent"))).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = amount.add(tax);
        VBox sheet = Paper.sheet("INVOICE " + d.get("invoice_no"), null);
        sheet.getChildren().add(Paper.kv(new String[][]{
                {"Bill to", String.valueOf(d.get("client"))}, {"Address", String.valueOf(d.get("client_address") == null ? "" : d.get("client_address"))},
                {"Project", d.get("project_code") + " " + d.get("project")}, {"Issue date", Fmt.str(d.get("issue_date"))},
                {"Due date", Fmt.str(d.get("due_date"))}, {"Status", Fmt.nice(d.get("status"))}}));
        sheet.getChildren().add(Paper.table(List.of("Description", "Amount"),
                List.of(List.of(Fmt.nice(d.get("type")) + " payment " + d.get("payment_no"), Fmt.money(amount)))));
        sheet.getChildren().add(Paper.kv(new String[][]{
                {"Subtotal", Fmt.money(amount)}, {"Tax (" + Fmt.num(d.get("tax_percent")) + "%)", Fmt.money(tax)},
                {"TOTAL DUE", Fmt.money(total)}, {"Notes", String.valueOf(d.get("notes") == null ? "" : d.get("notes"))}}));
        Stage st = Paper.preview(LanguageManager.x("Invoice"), sheet);
        // email button appended to the preview bar
        if (EmailService.isConfigured() && d.get("client_email") != null) {
            VBox wrap = (VBox) ((javafx.scene.control.ScrollPane) ((javafx.scene.layout.BorderPane) st.getScene().getRoot()).getCenter()).getContent();
            HBox bar = (HBox) wrap.getChildren().get(0);
            bar.getChildren().add(1, UI.outline(LanguageManager.x("Email to client"), () -> {
                String to = String.valueOf(d.get("client_email"));
                String body = "Dear " + d.get("client") + ",\n\nPlease find invoice " + d.get("invoice_no") + " for "
                        + d.get("project") + ".\nTotal due: " + Fmt.money(total) + "\nDue date: " + Fmt.str(d.get("due_date")) + "\n\nThank you.";
                new Thread(() -> {
                    boolean ok = EmailService.send(to, "Invoice " + d.get("invoice_no"), body, "INVOICE");
                    javafx.application.Platform.runLater(() -> Dialogs.info(LanguageManager.x("Email"),
                            ok ? LanguageManager.x("Email sent.") : LanguageManager.x("Email failed") + ": " + EmailService.lastError()));
                }).start();
            }));
        }
    }

    // ------------------------------------------------------------------ Receipt
    public static void receipt(Map<String, Object> r) {
        Map<String, Object> d = Db.row("SELECT p.*, pr.project_code, pr.name AS project, c.name AS client FROM payments p "
                + "JOIN projects pr ON pr.project_id=p.project_id JOIN clients c ON c.client_id=pr.client_id WHERE p.payment_id=?", r.get("payment_id"));
        VBox sheet = Paper.sheet("RECEIPT REC-" + String.valueOf(d.get("payment_no")).replace("PAY-", ""), null);
        sheet.getChildren().add(Paper.kv(new String[][]{
                {"Received from", String.valueOf(d.get("client"))}, {"Project", d.get("project_code") + " " + d.get("project")},
                {"Payment", d.get("payment_no") + " (" + Fmt.nice(d.get("type")) + ")"},
                {"Amount received", Fmt.money(d.get("amount"))}, {"Paid date", Fmt.str(d.get("paid_date"))},
                {"Method", Fmt.nice(d.get("method"))}, {"Reference", String.valueOf(d.get("reference_no") == null ? "-" : d.get("reference_no"))}}));
        sheet.getChildren().add(Paper.spacer(40));
        sheet.getChildren().add(UI.lbl("______________________________          ______________________________", "paper-cell"));
        sheet.getChildren().add(UI.lbl("Received by (Technocom)                              Authorised signature", "paper-head"));
        Paper.preview(LanguageManager.x("Receipt"), sheet);
    }
}
