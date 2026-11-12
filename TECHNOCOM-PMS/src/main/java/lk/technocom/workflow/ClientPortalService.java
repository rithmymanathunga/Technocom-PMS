package lk.technocom.workflow;

import lk.technocom.util.Db;
import lk.technocom.util.Session;

import java.util.List;
import java.util.Map;

/**
 * Service-layer read/write boundary for the client portal. Every project-scoped query is
 * constrained by the authenticated session's client_id in SQL, not by UI filters.
 * The current JavaFX/JDBC deployment still needs a server API before exposing remote clients.
 */
public final class ClientPortalService {
    private ClientPortalService() {}

    private static int clientId() {
        if (!Session.isClient() || Session.getClientId() <= 0)
            throw new SecurityException("A client account linked to a client record is required.");
        return Session.getClientId();
    }

    public static List<Map<String,Object>> projects() {
        int clientId = clientId();
        return Db.rows("SELECT project_id,project_code,name,location,project_type,start_date,end_date,status,progress,description " +
                "FROM projects WHERE client_id=? AND is_active=1 ORDER BY created_at DESC", clientId);
    }

    public static Map<String,Object> project(int projectId) {
        int clientId = clientId();
        Map<String,Object> project = Db.row("SELECT p.project_id,p.project_code,p.name,p.location,p.project_type,p.contract_value, " +
                "p.start_date,p.end_date,p.status,p.progress,p.description,u.full_name AS project_manager,u.email AS manager_email " +
                "FROM projects p LEFT JOIN users u ON u.user_id=p.manager_id " +
                "WHERE p.project_id=? AND p.client_id=? AND p.is_active=1", projectId, clientId);
        if (project == null) throw new SecurityException("Project not found or not accessible to this client.");
        return project;
    }

    public static List<Map<String,Object>> milestones(int projectId) {
        assertOwnProject(projectId);
        return Db.rows("SELECT milestone_id,title,description,planned_start,planned_end,actual_end,status,completion_percent " +
                "FROM project_milestones WHERE project_id=? ORDER BY planned_start,planned_end", projectId);
    }

    public static List<Map<String,Object>> progressReports(int projectId) {
        assertOwnProject(projectId);
        return Db.rows("SELECT report_date,weather,work_completed AS work_done,issues,progress_today AS progress_percent FROM daily_progress_reports " +
                "WHERE project_id=? AND status='SUBMITTED' ORDER BY report_date DESC", projectId);
    }

    public static List<Map<String,Object>> approvedDrawings(int projectId) {
        assertOwnProject(projectId);
        return Db.rows("SELECT drawing_id,drawing_no,title,type,current_revision,status,remarks,created_at " +
                "FROM drawings WHERE project_id=? AND status='APPROVED' ORDER BY drawing_no", projectId);
    }

    public static List<Map<String,Object>> approvedSpecifications(int projectId) {
        assertOwnProject(projectId);
        return Db.rows("SELECT s.spec_id,s.spec_no,s.title,s.category,s.revision,s.description,s.file_path " +
                "FROM specifications s JOIN specification_approvals a ON a.spec_id=s.spec_id AND a.status='APPROVED' " +
                "WHERE s.project_id=? ORDER BY s.spec_no", projectId);
    }

    public static List<Map<String,Object>> payments(int projectId) {
        assertOwnProject(projectId);
        return Db.rows("SELECT payment_id,payment_no,type,amount,due_date,paid_date,method,reference_no,status " +
                "FROM payments WHERE project_id=? ORDER BY due_date DESC", projectId);
    }

    public static List<Map<String,Object>> approvedDocuments(int projectId) {
        assertOwnProject(projectId);
        return Db.rows("SELECT document_id,document_category,title,original_file_name,storage_path,version_no,visibility,uploaded_at " +
                "FROM document_register WHERE project_id=? AND visibility IN ('CLIENT_APPROVED','CLIENT_SUBMITTED') AND is_current=1 " +
                "ORDER BY uploaded_at DESC", projectId);
    }

    public static long uploadDocument(int projectId,java.io.File file,String title,String category) {
        int clientId=clientId(); assertOwnProject(projectId);
        WorkflowValidation.fail(WorkflowValidation.required(title,"Document title",200));
        WorkflowValidation.fail(WorkflowValidation.required(category,"Document category",80));
        if(file==null||!file.isFile()||file.length()>25L*1024*1024)throw new IllegalArgumentException("Choose a valid document up to 25 MB.");
        String lower=file.getName().toLowerCase(java.util.Locale.ROOT);
        if(!lower.matches(".*\\.(pdf|doc|docx|xls|xlsx|csv|jpg|jpeg|png)$"))throw new IllegalArgumentException("Allowed document types: PDF, Word, Excel, CSV, JPG and PNG.");
        String path;
        try { path=lk.technocom.service.FileService.store(file,"client_uploads/"+clientId+"/"+projectId); }
        catch(java.io.IOException e){throw new IllegalStateException("Could not store the uploaded document: "+e.getMessage(),e);}
        return Db.insert("INSERT INTO document_register(project_id,document_category,title,original_file_name,storage_path,version_no,visibility,uploaded_by) VALUES(?,?,?,?,?,'1','CLIENT_SUBMITTED',?)",
                projectId,category.trim(),title.trim(),file.getName(),path,Session.getUserId());
    }

    public static List<Map<String,Object>> clientMeetings(int projectId) {
        assertOwnProject(projectId);
        return Db.rows("SELECT meeting_id,title,meeting_date,meeting_time,venue,agenda,status FROM meetings " +
                "WHERE project_id=? AND type='CLIENT' ORDER BY meeting_date DESC,meeting_time DESC", projectId);
    }

    public static Map<String,Object> handover(int projectId) {
        assertOwnProject(projectId);
        return Db.row("SELECT h.handover_id,h.handover_date,h.accepted_by,h.remarks,h.dlp_months,h.dlp_end_date,h.status " +
                "FROM handovers h WHERE h.project_id=?", projectId);
    }

    public static List<Map<String,Object>> handoverChecklist(int projectId) {
        assertOwnProject(projectId);
        return Db.rows("SELECT c.item,c.is_done FROM handover_checklist c JOIN handovers h ON h.handover_id=c.handover_id " +
                "WHERE h.project_id=? ORDER BY c.check_id", projectId);
    }

    public static List<Map<String,Object>> dlpVisits(int projectId) {
        assertOwnProject(projectId);
        return Db.rows("SELECT v.defect,v.reported_date,v.priority,v.status,v.visit_date,v.notes " +
                "FROM dlp_visits v JOIN handovers h ON h.handover_id=v.handover_id " +
                "WHERE h.project_id=? ORDER BY v.reported_date DESC", projectId);
    }

    public static List<Map<String,Object>> requests() {
        int clientId = clientId();
        return Db.rows("SELECT r.request_id,r.project_id,p.project_code,p.name AS project_name,r.request_type,r.subject, " +
                "r.description,r.preferred_date,r.preferred_time,r.status,r.staff_response,r.proposed_date,r.proposed_time,r.created_at " +
                "FROM client_requests r JOIN projects p ON p.project_id=r.project_id " +
                "WHERE r.client_id=? ORDER BY r.created_at DESC", clientId);
    }

    /** Client-facing variation requests are stored in the shared engineering change register. */
    public static List<Map<String,Object>> variations() {
        int clientId=clientId();
        return Db.rows("SELECT c.change_request_id,c.project_id,p.project_code,c.title,c.description,c.cost_impact,c.time_impact_days,c.status,c.review_comment,c.client_consent,c.client_consent_comment,c.created_at,c.reviewed_at,c.client_consent_at " +
                "FROM change_requests c JOIN projects p ON p.project_id=c.project_id WHERE c.submitted_by_client_id=? ORDER BY c.created_at DESC",clientId);
    }

    public static long submitVariation(int projectId,String title,String description,double costImpact,int timeImpactDays) {
        int clientId=clientId(); assertOwnProject(projectId);
        WorkflowValidation.fail(WorkflowValidation.required(title,"Variation title",180));
        WorkflowValidation.fail(WorkflowValidation.required(description,"Variation scope and justification",5000));
        if(!Double.isFinite(costImpact)||costImpact<0||timeImpactDays<0||timeImpactDays>3650)
            throw new IllegalArgumentException("Cost impact must be finite and non-negative; time impact must be 0–3650 days.");
        long id=Db.insert("INSERT INTO change_requests(project_id,requested_by,submitted_by_client_id,title,description,cost_impact,time_impact_days,client_consent) VALUES(?,?,?,?,?,?,?,'PENDING')",
                projectId,Session.getUserId(),clientId,title.trim(),description.trim(),costImpact,timeImpactDays);
        Db.insert("INSERT INTO change_request_history(change_request_id,event_type,event_note,actor_id) VALUES(?,'SUBMITTED',?,?)",
                id,"Client variation submitted by client account "+clientId,Session.getUserId());
        return id;
    }

    /** Client consent is limited to a variation belonging to one of the client's own projects. */
    public static void respondToVariation(long changeRequestId,String decision,String comment) {
        int clientId=clientId();
        if(!"ACCEPTED".equals(decision)&&!"DECLINED".equals(decision))throw new IllegalArgumentException("Invalid client decision.");
        WorkflowValidation.fail(WorkflowValidation.required(comment,"Client response comment",2000));
        Db.tx(c->{
            Map<String,Object> variation=Db.row(c,"SELECT c.change_request_id,c.status,c.client_consent FROM change_requests c JOIN projects p ON p.project_id=c.project_id WHERE c.change_request_id=? AND c.submitted_by_client_id=? AND p.client_id=? FOR UPDATE",changeRequestId,clientId,clientId);
            if(variation==null||!"APPROVED".equals(variation.get("status"))||!"PENDING".equals(variation.get("client_consent")))
                throw new IllegalStateException("Only an approved variation submitted by your account can receive a one-time consent response.");
            int changed=Db.exec(c,"UPDATE change_requests SET client_consent=?,client_consent_by=?,client_consent_at=NOW(),client_consent_comment=? WHERE change_request_id=? AND status='APPROVED' AND client_consent='PENDING'",decision,Session.getUserId(),comment.trim(),changeRequestId);
            if(changed!=1) throw new IllegalStateException("Variation state changed; refresh and retry.");
            Db.insert(c,"INSERT INTO change_request_history(change_request_id,event_type,event_note,actor_id) VALUES(?,?,?,?)",changeRequestId,"CLIENT_"+decision,comment.trim(),Session.getUserId());
        });
    }

    public static List<Map<String,Object>> warrantyClaims() {
        int clientId=clientId();
        return Db.rows("SELECT claim_id,project_id,claim_no,description,reported_date,status,staff_response,reviewed_at FROM warranty_claims WHERE client_id=? ORDER BY created_at DESC",clientId);
    }

    public static long submitWarrantyClaim(int projectId,String description) {
        int clientId=clientId(); assertOwnProject(projectId);
        WorkflowValidation.fail(WorkflowValidation.required(description,"Warranty claim description",5000));
        String claimNo=Db.nextCode("warranty_claims","claim_no","WAR");
        return Db.insert("INSERT INTO warranty_claims(client_id,project_id,claim_no,description,reported_date) VALUES(?,?,?,?,CURDATE())",clientId,projectId,claimNo,description.trim());
    }

    public static List<Map<String,Object>> feedback() {
        return Db.rows("SELECT f.feedback_id,f.project_id,p.project_code,f.category,f.subject,f.message,f.status,f.response,f.created_at " +
                "FROM client_feedback f JOIN projects p ON p.project_id=f.project_id " +
                "WHERE f.client_id=? ORDER BY f.created_at DESC", clientId());
    }

    public static List<Map<String,Object>> notifications() {
        return Db.rows("SELECT notification_id,project_id,event_key,title,message,is_read,created_at " +
                "FROM client_portal_notifications WHERE client_id=? ORDER BY created_at DESC", clientId());
    }

    public static void markNotificationRead(long notificationId) {
        Db.exec("UPDATE client_portal_notifications SET is_read=1 WHERE notification_id=? AND client_id=?",
                notificationId, clientId());
    }

    public static long submitFeedback(int projectId, String category, String subject, String message) {
        int clientId = clientId();
        assertOwnProject(projectId);
        WorkflowValidation.fail(WorkflowValidation.required(category, "Category", 50));
        WorkflowValidation.fail(WorkflowValidation.required(subject, "Subject", 180));
        WorkflowValidation.fail(WorkflowValidation.required(message, "Message", 5000));
        return Db.insert("INSERT INTO client_feedback(client_id,project_id,category,subject,message) VALUES(?,?,?,?,?)",
                clientId, projectId, category.trim(), subject.trim(), message.trim());
    }

    /** Client acceptance is committed atomically only after every checklist item is complete. */
    public static void acceptHandover(int projectId) {
        acceptHandoverForClient(clientId(), Session.getUserId(), projectId);
    }

    /** API entry point; identity values come only from a verified, unexpired client token. */
    static void acceptHandoverForClient(int clientId, int userId, int projectId) {
        if (clientId <= 0 || userId <= 0 || projectId <= 0)
            throw new SecurityException("A valid authenticated client identity is required.");
        Db.tx(c -> {
            Map<String,Object> project = Db.row(c,
                    "SELECT project_id FROM projects WHERE project_id=? AND client_id=? AND is_active=1 FOR UPDATE",
                    projectId, clientId);
            if (project == null) throw new SecurityException("Project not found or not accessible to this client.");
            Map<String,Object> handover = Db.row(c,
                    "SELECT handover_id,status FROM handovers WHERE project_id=? FOR UPDATE", projectId);
            if (handover == null) throw new IllegalStateException("A handover record has not been prepared for this project.");
            if (!"PENDING".equals(String.valueOf(handover.get("status"))))
                throw new IllegalStateException("This handover is not awaiting client acceptance.");
            Map<String,Object> checklist = Db.row(c,
                    "SELECT COUNT(*) AS total_items,COALESCE(SUM(CASE WHEN is_done=1 THEN 1 ELSE 0 END),0) AS completed_items FROM handover_checklist WHERE handover_id=?",
                    handover.get("handover_id"));
            long total = ((Number) checklist.get("total_items")).longValue();
            long completed = ((Number) checklist.get("completed_items")).longValue();
            if (!WorkflowValidation.handoverChecklistComplete(total, completed))
                throw new IllegalStateException("Client acceptance is blocked until every handover checklist item is completed.");
            Map<String,Object> user = Db.row(c,
                    "SELECT full_name FROM users WHERE user_id=? AND status='ACTIVE'", userId);
            if (user == null || user.get("full_name") == null)
                throw new SecurityException("The authenticated client account is not active.");
            int changed = Db.exec(c,
                    "UPDATE handovers SET status='HANDED_OVER',accepted_by=? WHERE handover_id=? AND status='PENDING'",
                    user.get("full_name"), handover.get("handover_id"));
            if (changed != 1) throw new IllegalStateException("Handover state changed; refresh and try again.");
        });
    }

    public static void assertOwnProject(int projectId) {
        int clientId = clientId();
        if (projectId <= 0 || Db.num("SELECT COUNT(*) FROM projects WHERE project_id=? AND client_id=? AND is_active=1",
                projectId, clientId) != 1)
            throw new SecurityException("You can only access your own active projects.");
    }
}
