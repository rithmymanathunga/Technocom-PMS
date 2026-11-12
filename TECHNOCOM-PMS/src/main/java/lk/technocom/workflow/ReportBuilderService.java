package lk.technocom.workflow;

import lk.technocom.ui.ActionPermission;
import lk.technocom.util.Db;
import lk.technocom.util.Session;
import lk.technocom.util.TenantScope;

import java.time.LocalDate;
import java.util.*;

/** Parameterised, allow-listed report builder. Arbitrary SQL is deliberately not accepted. */
public final class ReportBuilderService {
    public enum Report { PROJECT_PROGRESS, PAYMENT_STATUS, PROCUREMENT_ORDERS, QUALITY_NCR, HR_TRAINING, HR_CERTIFICATE_EXPIRY, CASHFLOW_FORECAST }
    public record Filter(LocalDate from, LocalDate to, Integer projectId) {
        public Filter {
            if (from != null && to != null && to.isBefore(from)) throw new IllegalArgumentException("End date must not be before start date.");
            if (projectId != null && projectId <= 0) throw new IllegalArgumentException("Project ID must be positive.");
        }
    }
    public record Result(List<String> columns, List<Map<String,Object>> rows) {}
    private record Definition(List<String> columns, String sql, String dateColumn, String projectColumn, ActionPermission.Action permission) {}
    private static final Map<Report,Definition> DEFINITIONS;
    static {
        Map<Report,Definition> d = new EnumMap<>(Report.class);
        d.put(Report.PROJECT_PROGRESS,new Definition(List.of("Project","Name","Client","Manager","Status","Progress %","Start","End"),
            "SELECT p.project_code AS Project,p.name AS Name,c.name AS Client,u.full_name AS Manager,p.status AS Status,p.progress AS `Progress %`,p.start_date AS Start,p.end_date AS End FROM projects p LEFT JOIN clients c ON c.client_id=p.client_id LEFT JOIN users u ON u.user_id=p.manager_id WHERE p.is_active=1", "p.start_date","p.project_id",ActionPermission.Action.VIEW_REPORTS));
        d.put(Report.PAYMENT_STATUS,new Definition(List.of("Payment No","Project","Type","Amount","Due Date","Paid Date","Status","Reference"),
            "SELECT pay.payment_no AS `Payment No`,p.project_code AS Project,pay.type AS Type,pay.amount AS Amount,pay.due_date AS `Due Date`,pay.paid_date AS `Paid Date`,CASE WHEN pay.status='PAID' THEN 'PAID' WHEN pay.due_date<CURDATE() THEN 'OVERDUE' ELSE 'PENDING' END AS Status,pay.reference_no AS Reference FROM payments pay JOIN projects p ON p.project_id=pay.project_id WHERE 1=1", "pay.due_date","pay.project_id",ActionPermission.Action.VIEW_FINANCE));
        d.put(Report.PROCUREMENT_ORDERS,new Definition(List.of("PO No","Project","Supplier","Order Date","Expected Date","Amount","Status","Tracking Ref"),
            "SELECT po.po_no AS `PO No`,p.project_code AS Project,s.name AS Supplier,po.order_date AS `Order Date`,po.expected_date AS `Expected Date`,po.total_amount AS Amount,po.status AS Status,po.tracking_reference AS `Tracking Ref` FROM purchase_orders po JOIN projects p ON p.project_id=po.project_id LEFT JOIN suppliers s ON s.supplier_id=po.supplier_id WHERE 1=1", "po.order_date","po.project_id",ActionPermission.Action.VIEW_PROCUREMENT));
        d.put(Report.QUALITY_NCR,new Definition(List.of("NCR No","Project","Title","Severity","Status","Due Date","Created"),
            "SELECT n.ncr_no AS `NCR No`,p.project_code AS Project,n.title AS Title,n.severity AS Severity,n.status AS Status,n.due_date AS `Due Date`,n.created_at AS Created FROM non_conformance_reports n JOIN projects p ON p.project_id=n.project_id WHERE 1=1", "DATE(n.created_at)","n.project_id",ActionPermission.Action.VIEW_ENGINEERING));
        d.put(Report.HR_TRAINING,new Definition(List.of("Employee No","Employee","Course","Provider","Completed","Expires"),
            "SELECT e.employee_no AS `Employee No`,u.full_name AS Employee,t.course_name AS Course,t.provider AS Provider,t.completed_on AS Completed,t.expires_on AS Expires FROM employee_training t JOIN employees e ON e.employee_id=t.employee_id LEFT JOIN users u ON u.user_id=e.user_id WHERE 1=1", "t.completed_on",null,null));
        d.put(Report.HR_CERTIFICATE_EXPIRY,new Definition(List.of("Employee No","Employee","Department","Certificate / Course","Provider","Expiry Date","Days Remaining"),
            "SELECT e.employee_no AS `Employee No`,u.full_name AS Employee,e.department AS Department,t.course_name AS `Certificate / Course`,t.provider AS Provider,t.expires_on AS `Expiry Date`,DATEDIFF(t.expires_on,CURDATE()) AS `Days Remaining` FROM employee_training t JOIN employees e ON e.employee_id=t.employee_id LEFT JOIN users u ON u.user_id=e.user_id WHERE t.expires_on IS NOT NULL AND e.is_active=1", "t.expires_on",null,null));
        d.put(Report.CASHFLOW_FORECAST,new Definition(List.of("Date","Project","Direction","Category","Description","Amount","Confidence"),
            "SELECT f.forecast_date AS Date,p.project_code AS Project,f.direction AS Direction,f.category AS Category,f.description AS Description,f.amount AS Amount,f.confidence AS Confidence FROM cashflow_forecast_entries f LEFT JOIN projects p ON p.project_id=f.project_id WHERE 1=1", "f.forecast_date","f.project_id",ActionPermission.Action.VIEW_FINANCE));
        DEFINITIONS=Collections.unmodifiableMap(d);
    }
    private ReportBuilderService() {}

    public static List<Report> availableReports() {
        if (Session.getUserId() <= 0) throw new SecurityException("Sign in required.");
        List<Report> out=new ArrayList<>();
        for (Report r:Report.values()) if (allowed(r)) out.add(r);
        return List.copyOf(out);
    }

    public static Result run(Report report, Filter filter) {
        if (report==null) throw new IllegalArgumentException("Select a report.");
        if (filter==null) filter=new Filter(null,null,null);
        Definition def=DEFINITIONS.get(report);
        if (!allowed(report)) throw new SecurityException("Your role cannot access this report.");
        StringBuilder sql=new StringBuilder(def.sql());
        List<Object> args=new ArrayList<>();
        int organizationId=TenantScope.requireOrganization();
        if (def.projectColumn()!=null) { sql.append(" AND p.organization_id=?"); args.add(organizationId); }
        else if (report==Report.HR_TRAINING || report==Report.HR_CERTIFICATE_EXPIRY) { sql.append(" AND u.organization_id=?"); args.add(organizationId); }
        if (filter.from()!=null) { sql.append(" AND ").append(def.dateColumn()).append(" >= ?"); args.add(filter.from()); }
        if (filter.to()!=null) { sql.append(" AND ").append(def.dateColumn()).append(" <= ?"); args.add(filter.to()); }
        if (filter.projectId()!=null) {
            if (def.projectColumn()==null) throw new IllegalArgumentException("This report does not support a project filter.");
            TenantScope.requireProject(filter.projectId());
            sql.append(" AND ").append(def.projectColumn()).append(" = ?"); args.add(filter.projectId());
        }
        if (Session.isClient()) {
            if (report!=Report.PROJECT_PROGRESS) throw new SecurityException("Client accounts can only view their own project progress report.");
            sql.append(" AND p.client_id=?"); args.add(Session.getClientId());
            if (Session.getClientId()<=0) throw new SecurityException("Client account is not linked to a client record.");
        } else if (def.projectColumn()!=null && !(Session.isAdmin()||Session.hasRole("Project Manager"))) {
            // Apply the same project assignment boundary to unfiltered reports as to a
            // selected project; otherwise a valid report grant could expose other sites.
            sql.append(" AND ").append(def.projectColumn()).append(" IN (SELECT p_scope.project_id FROM projects p_scope LEFT JOIN project_staff ps_scope ON ps_scope.project_id=p_scope.project_id WHERE p_scope.organization_id=? AND (p_scope.manager_id=? OR ps_scope.user_id=?))");
            args.add(organizationId); args.add(Session.getUserId()); args.add(Session.getUserId());
        }
        sql.append(" ORDER BY ").append(def.dateColumn()).append(" DESC LIMIT 5000");
        return new Result(def.columns(),List.copyOf(Db.rows(sql.toString(),args.toArray())));
    }

    private static boolean allowed(Report r) {
        if (r==Report.HR_TRAINING || r==Report.HR_CERTIFICATE_EXPIRY) {
            // HR reports require both the intended role and the fine-grained HR grant.
            // Keeping this check here makes the report menu and direct run() calls agree.
            return (Session.isAdmin() || Session.hasRole("Project Manager"))
                && ActionPermission.can(ActionPermission.Action.MANAGE_HR);
        }
        ActionPermission.Action action=DEFINITIONS.get(r).permission();
        return action!=null && ActionPermission.can(action);
    }
}
