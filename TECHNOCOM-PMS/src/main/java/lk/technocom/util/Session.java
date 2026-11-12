package lk.technocom.util;

public final class Session {

    private static int userId;
    private static String fullName;
    private static String username;
    private static String role;
    private static boolean mustChange;
    private static int clientId;
    private static int organizationId;
    private static String sessionToken;

    private Session() {}

    public static void start(int id, String name, String user, String roleName, boolean mustChangePassword) {
        userId = id;
        fullName = name;
        username = user;
        role = roleName;
        mustChange = mustChangePassword;
        // Never carry a previous user's tenant or client scope into a new authenticated session.
        organizationId = 0; // resolved afresh from the authenticated DB row
        clientId = 0;
        sessionToken = java.util.UUID.randomUUID().toString();
    }

    public static void clear() {
        userId = 0;
        fullName = null;
        username = null;
        role = null;
        mustChange = false;
        clientId = 0;
        organizationId = 0;
        sessionToken = null;
    }

    public static int getUserId() { return userId; }
    public static String getSessionToken() { return sessionToken; }
    /** Organisation resolved from the authenticated database account. */
    public static int getOrganizationId() { return organizationId; }
    public static void setOrganizationId(int id) { if (id <= 0) throw new IllegalArgumentException("Valid organisation required."); organizationId = id; }
    public static String getFullName() { return fullName; }
    public static String getUsername() { return username; }
    public static String getRole() { return role; }
    public static boolean isMustChange() { return mustChange; }
    public static void setMustChange(boolean v) { mustChange = v; }
    public static boolean hasRole(String roleName) { return roleName != null && roleName.equals(role); }
    /** The client record linked to a Client-role user (0 = none). */
    public static int getClientId() { return clientId; }
    public static void setClientId(int id) { clientId = id; }
    /** Outside actors (Client, Developer): own projects only, no editing. */
    public static boolean isClient() { return "Client".equals(role) || "Developer".equals(role); }
    public static boolean isDeveloper() { return "Developer".equals(role); }
    public static boolean isAdmin() { return "Admin".equals(role); }
}
