package lk.technocom.pages;

import javafx.scene.Node;
import lk.technocom.ui.*;
import lk.technocom.ui.ListPage.K;
import lk.technocom.util.*;

import java.util.Map;

public final class PaymentSummaryPage {

    private PaymentSummaryPage() {}

    public static Node build() {
        ListPage p = new ListPage(Page.PAYSUM.text(), LanguageManager.x("Contract value, paid, pending and balance for every project"))
                .sql("SELECT s.project_id, s.project_code, s.name, s.contract_value, s.total_paid, s.total_pending, s.balance, "
                        + "ROUND(100 * s.total_paid / s.contract_value) AS paid_percent FROM v_project_payment_summary s "
                        + "JOIN projects pr ON pr.project_id=s.project_id WHERE pr.is_active=TRUE")
                .orderBy("t.project_code DESC")
                .statMoney("Total contract value", "SELECT COALESCE(SUM(contract_value),0) FROM projects WHERE is_active=TRUE")
                .statMoney("Total paid", "SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='PAID'")
                .statMoney("Total pending", "SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='PENDING'")
                .search("project_code", "name")
                .filterLookup("project_id", "Project", Q.PROJECTS)
                .col("project_code", "Code", K.TEXT, 0.7).col("name", "Project", K.TEXT, 1.6)
                .col("contract_value", "Contract value", K.MONEY, 1.1).col("total_paid", "Paid", K.MONEY, 1)
                .col("total_pending", "Pending", K.MONEY, 1).col("balance", "Balance", K.MONEY, 1)
                .col("paid_percent", "Paid %", K.PROGRESS, 1)
                .act("Payments", PaymentSummaryPage::list);
        return p.build();
    }

    private static void list(Map<String, Object> r) {
        ListPage l = new ListPage(r.get("project_code") + " " + r.get("name"), LanguageManager.x("Payments of this project"))
                .sql("SELECT payment_no, type, amount, due_date, paid_date, method, display_status AS status FROM v_payments_with_status "
                        + "WHERE project_id=" + ((Number) r.get("project_id")).longValue())
                .orderBy("t.due_date")
                .col("payment_no", "Payment", K.TEXT, 0.8).col("type", "Type", K.NICE, 0.8).col("amount", "Amount", K.MONEY, 1)
                .col("due_date", "Due", K.DATE, 0.8).col("paid_date", "Paid on", K.DATE, 0.8)
                .col("method", "Method", K.NICE, 0.8).col("status", "Status", K.BADGE, 0.7);
        Dialogs.show(LanguageManager.x("Payments"), l.build(), 900, 520);
    }
}
