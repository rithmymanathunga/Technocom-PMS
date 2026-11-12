package lk.technocom.workflow;

import lk.technocom.util.Db;
import lk.technocom.util.Session;
import lk.technocom.util.TenantScope;
import lk.technocom.ui.ActionPermission;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Purchase requisitions with atomic header + line-item creation and approval. */
public final class ProcurementService {
    private ProcurementService() {}

    public static List<Map<String,Object>> list() {
        ActionPermission.require(ActionPermission.Action.VIEW_PROCUREMENT);
        requireStaff();
        int org=TenantScope.requireOrganization();
        String sql="SELECT r.*,p.project_code,p.name AS project_name,u.full_name AS requester_name,rv.full_name AS reviewer_name,(SELECT COUNT(*) FROM procurement_request_items i WHERE i.procurement_request_id=r.procurement_request_id) AS item_count FROM procurement_requests r JOIN projects p ON p.project_id=r.project_id JOIN users u ON u.user_id=r.requested_by LEFT JOIN users rv ON rv.user_id=r.reviewed_by WHERE p.organization_id="+org;
        if(!Session.isAdmin()&&!Session.hasRole("Project Manager")) sql+=" AND (p.manager_id=? OR EXISTS(SELECT 1 FROM project_staff ps WHERE ps.project_id=p.project_id AND ps.user_id=?))";
        sql+=" ORDER BY r.created_at DESC";
        return Session.isAdmin()||Session.hasRole("Project Manager")?Db.rows(sql):Db.rows(sql,Session.getUserId(),Session.getUserId());
    }

    public static long create(int projectId, LocalDate requiredBy, String purpose, List<Item> items) {
        ActionPermission.require(ActionPermission.Action.CREATE_PROCUREMENT_REQUEST);
        requireStaff();
        assertProject(projectId);
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Add at least one procurement item.");
        if (requiredBy != null && requiredBy.isBefore(LocalDate.now())) throw new IllegalArgumentException("Required-by date cannot be in the past.");
        for (Item item : items) {
            if (item == null) throw new IllegalArgumentException("Procurement item cannot be empty.");
            WorkflowValidation.fail(WorkflowValidation.required(item.name(), "Item name", 180));
            WorkflowValidation.fail(WorkflowValidation.required(item.unit(), "Unit", 30));
            if (item.specification() != null && item.specification().length() > 2000)
                throw new IllegalArgumentException("Item specification must be 2000 characters or fewer.");
            if (item.quantity() == null || item.quantity().signum() <= 0 || item.quantity().stripTrailingZeros().scale()>3 || item.quantity().precision()>14)
                throw new IllegalArgumentException("Item quantity must be positive and support up to three decimal places.");
            if (item.estimatedUnitCost() != null && (item.estimatedUnitCost().signum() < 0 || item.estimatedUnitCost().stripTrailingZeros().scale()>2 || item.estimatedUnitCost().precision()>14))
                throw new IllegalArgumentException("Estimated unit cost must be non-negative and support up to two decimal places.");
        }
        final long[] id = new long[1];
        Db.tx(c -> {
            if(Db.row(c,"SELECT project_id FROM projects WHERE project_id=? AND organization_id=? AND is_active=1 FOR UPDATE",projectId,Session.getOrganizationId())==null)throw new IllegalArgumentException("Select an active project.");
            assertProject(c,projectId);
            id[0] = Db.insert(c, "INSERT INTO procurement_requests(project_id,requested_by,request_date,required_by,purpose,status) VALUES(?,?,CURDATE(),?,?,'PENDING')",
                    projectId, Session.getUserId(), requiredBy, purpose == null || purpose.isBlank() ? null : purpose.trim());
            for (Item item : items) Db.exec(c, "INSERT INTO procurement_request_items(procurement_request_id,item_name,specification,quantity,unit,estimated_unit_cost) VALUES(?,?,?,?,?,?)",
                    id[0], item.name().trim(), item.specification(), item.quantity(), item.unit().trim(), item.estimatedUnitCost());
        });
        return id[0];
    }

    public static List<Map<String,Object>> items(long requestId) {
        ActionPermission.require(ActionPermission.Action.VIEW_PROCUREMENT);
        requireStaff();
        Map<String,Object> request=Db.row("SELECT project_id FROM procurement_requests WHERE procurement_request_id=?",requestId);
        if(request==null)throw new IllegalArgumentException("Procurement request not found.");assertProject(((Number)request.get("project_id")).intValue());
        return Db.rows("SELECT * FROM procurement_request_items WHERE procurement_request_id=? ORDER BY request_item_id", requestId);
    }

    public static void review(long requestId, boolean approve) {
        ActionPermission.require(ActionPermission.Action.REVIEW_PROCUREMENT);
        requireApprover();
        Map<String,Object> req=Db.row("SELECT project_id FROM procurement_requests WHERE procurement_request_id=?",requestId);
        if(req==null)throw new IllegalStateException("Request was not found or has already been reviewed.");
        assertProject(((Number)req.get("project_id")).intValue());
        int changed = Db.exec("UPDATE procurement_requests r JOIN projects p ON p.project_id=r.project_id SET r.status=?,r.reviewed_by=?,r.reviewed_at=NOW() WHERE r.procurement_request_id=? AND r.status='PENDING' AND p.organization_id=?",
                approve ? "APPROVED" : "REJECTED", Session.getUserId(), requestId, TenantScope.requireOrganization());
        if (changed == 0) throw new IllegalStateException("Request was not found or has already been reviewed.");
    }

    private static void assertProject(int projectId){
        TenantScope.requireProject(projectId);
        if(projectId<=0||Db.num("SELECT COUNT(*) FROM projects WHERE project_id=? AND organization_id=? AND is_active=1",projectId,Session.getOrganizationId())!=1)throw new IllegalArgumentException("Select an active project.");
        if(!Session.isAdmin()&&!Session.hasRole("Project Manager")&&Db.num("SELECT COUNT(*) FROM projects p LEFT JOIN project_staff ps ON ps.project_id=p.project_id AND ps.user_id=? WHERE p.project_id=? AND (p.manager_id=? OR ps.user_id IS NOT NULL)",Session.getUserId(),projectId,Session.getUserId())==0)
            throw new SecurityException("You are not assigned to this project.");
    }
    private static void assertProject(java.sql.Connection c,int projectId) throws java.sql.SQLException {
        Map<String,Object> p=Db.row(c,"SELECT manager_id FROM projects WHERE project_id=? AND organization_id=? FOR UPDATE",projectId,Session.getOrganizationId());
        if(p==null)throw new SecurityException("Project is not available in the current organisation.");
        if(Session.isAdmin()||Session.hasRole("Project Manager"))return;
        if(p!=null&&p.get("manager_id")!=null&&((Number)p.get("manager_id")).intValue()==Session.getUserId())return;
        if(Db.row(c,"SELECT project_id FROM project_staff WHERE project_id=? AND user_id=? FOR UPDATE",projectId,Session.getUserId())==null)
            throw new SecurityException("You are not assigned to this project.");
    }
    private static void requireStaff() {
        if (Session.getUserId() <= 0 || !(Session.isAdmin() || Session.hasRole("Project Manager")
                || Session.hasRole("Site Engineer") || Session.hasRole("QS Engineer") || Session.hasRole("Stock Keeper")))
            throw new SecurityException("Only authorised internal staff can access procurement.");
    }
    private static void requireApprover() {
        requireStaff();
        if (!Session.isAdmin() && !Session.hasRole("Project Manager")) throw new SecurityException("Only Admin or Project Manager can approve procurement requests.");
    }

    public record Item(String name, String specification, BigDecimal quantity, String unit, BigDecimal estimatedUnitCost) {}
}
