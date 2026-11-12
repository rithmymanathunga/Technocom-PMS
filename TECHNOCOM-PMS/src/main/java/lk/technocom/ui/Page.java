package lk.technocom.ui;

import lk.technocom.util.LanguageManager;

public enum Page {
    DASHBOARD("Dashboard"), ACCOUNT("Account"),
    USERS("Users"), CLIENTS("Clients"),
    PROJECTS("Projects"), PLANNING("Project Planning"), DPR("DPR"), BOQ("BOQ"), DRAWINGS("Drawings"), SPECS("Specs"), MEETINGS("Meetings"),
    STOCK("Stock"), PAYMENTS("Payments"), PAYSUM("Payment Summary"), HANDOVER("Handover & DLP"),
    REPORTS("Reports & Admin"), ENTERPRISE("Enterprise Workflows"), HELP("Help & About");

    public final String label;

    Page(String label) { this.label = label; }

    public String text() { return LanguageManager.x(label); }
}
