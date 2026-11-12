package lk.technocom.ui;

import lk.technocom.util.Session;

import java.util.EnumSet;
import java.util.Set;

/** Which role can open which page. Locked pages stay visible in the menu but are not clickable. */
public final class Access {

    private Access() {}

    public static final String[] ROLES = {"Admin", "Project Manager", "Site Engineer", "QS Engineer", "Stock Keeper", "Developer", "Client"};

    private static Set<Page> pagesFor(String role) {
        if (role == null) return EnumSet.noneOf(Page.class);
        switch (role) {
            case "Admin":
                return EnumSet.allOf(Page.class);
            case "Project Manager": {
                Set<Page> s = EnumSet.allOf(Page.class);
                s.remove(Page.USERS);
                return s;
            }
            case "Site Engineer":
                return EnumSet.of(Page.DASHBOARD, Page.ACCOUNT, Page.PROJECTS, Page.PLANNING, Page.DPR, Page.DRAWINGS,
                        Page.SPECS, Page.MEETINGS, Page.STOCK, Page.HANDOVER, Page.ENTERPRISE, Page.HELP);
            case "QS Engineer":
                return EnumSet.of(Page.DASHBOARD, Page.ACCOUNT, Page.CLIENTS, Page.PROJECTS, Page.PLANNING, Page.BOQ,
                        Page.DRAWINGS, Page.SPECS, Page.MEETINGS, Page.PAYMENTS, Page.PAYSUM, Page.ENTERPRISE, Page.HELP);
            case "Stock Keeper":
                return EnumSet.of(Page.DASHBOARD, Page.ACCOUNT, Page.PROJECTS, Page.SPECS, Page.MEETINGS,
                        Page.STOCK, Page.ENTERPRISE, Page.HELP);
            // ---- use case diagram: Developer = meetings, payments, handover, DLP service
            case "Developer":
                return EnumSet.of(Page.DASHBOARD, Page.ACCOUNT, Page.PROJECTS, Page.PLANNING, Page.MEETINGS, Page.PAYMENTS,
                        Page.PAYSUM, Page.HANDOVER, Page.HELP);
            // ---- use case diagram: Client = reports, drawing approvals (+ follows DPR progress)
            case "Client":
                return EnumSet.of(Page.DASHBOARD, Page.ACCOUNT, Page.PROJECTS, Page.PLANNING, Page.DPR, Page.DRAWINGS,
                        Page.REPORTS, Page.HELP);
            default:
                return EnumSet.of(Page.DASHBOARD, Page.ACCOUNT, Page.HELP);
        }
    }

    public static boolean can(Page p) {
        return p != null && pagesFor(Session.getRole()).contains(p);
    }

    /** Enforce page authorization again at the service/controller entry point. */
    public static void require(Page page) {
        if (!can(page)) throw new SecurityException("Your role is not allowed to access " + page + ".");
    }

    /** User administration is deliberately restricted to the Admin role. */
    public static void requireAdmin() {
        if (!Session.isAdmin()) throw new SecurityException("Only an Admin can manage user accounts.");
    }

    /** Admin and Project Manager may approve estimates, drawings and handovers. */
    public static boolean canApprove() {
        return ActionPermission.can(ActionPermission.Action.APPROVE_BOQ)
                || ActionPermission.can(ActionPermission.Action.REVIEW_DRAWING)
                || ActionPermission.can(ActionPermission.Action.REVIEW_PAYMENT);
    }
}
