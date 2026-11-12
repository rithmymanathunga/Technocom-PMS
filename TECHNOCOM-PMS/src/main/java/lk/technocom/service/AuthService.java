package lk.technocom.service;

import lk.technocom.util.Audit;
import lk.technocom.util.DBConnection;
import lk.technocom.util.LanguageManager;
import lk.technocom.util.Session;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class AuthService {

    private static final int MAX_ATTEMPTS = 5;

    private AuthService() {}

    /** Message of this exception is a key in messages_xx.properties. */
    public static class AuthException extends Exception {
        public AuthException(String messageKey) {
            super(messageKey);
        }
    }

    public static void login(String loginName, String password) throws AuthException, SQLException {
        // A failed re-authentication must not leave an earlier user's session active.
        Session.clear();
        if (loginName == null || loginName.isBlank() || password == null) {
            throw new AuthException("login.error.invalid");
        }
        loginName = loginName.trim();
        String sql = "SELECT u.user_id, u.full_name, u.username, u.password_hash, u.status, u.must_change_password, "
                + "u.failed_attempts, u.client_id, u.organization_id, o.status AS organization_status, u.password_changed_at, r.role_name "
                + "FROM users u JOIN roles r ON r.role_id = u.role_id JOIN organizations o ON o.organization_id=u.organization_id "
                + "WHERE u.username = ? OR u.email = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, loginName);
            ps.setString(2, loginName);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    recordLoginEvent(con, null, loginName, "FAILED", "UNKNOWN_ACCOUNT");
                    throw new AuthException("login.error.invalid");
                }

                int userId = rs.getInt("user_id");
                if (!"ACTIVE".equals(rs.getString("organization_status"))) {
                    recordLoginEvent(con, userId, loginName, "BLOCKED", "ORGANIZATION_SUSPENDED");
                    throw new AuthException("login.error.inactive");
                }
                String status = rs.getString("status");

                if ("LOCKED".equals(status)) {
                    recordLoginEvent(con, userId, loginName, "BLOCKED", "ACCOUNT_LOCKED");
                    throw new AuthException("login.error.locked");
                }
                if ("INACTIVE".equals(status)) {
                    recordLoginEvent(con, userId, loginName, "BLOCKED", "ACCOUNT_INACTIVE");
                    throw new AuthException("login.error.inactive");
                }

                if (!passwordMatches(password, rs.getString("password_hash"))) {
                    int attempts = rs.getInt("failed_attempts") + 1;
                    registerFailedAttempt(con, userId, attempts);
                    recordLoginEvent(con, userId, loginName,
                            attempts >= MAX_ATTEMPTS ? "LOCKED" : "FAILED",
                            attempts >= MAX_ATTEMPTS ? "TOO_MANY_FAILED_ATTEMPTS" : "INVALID_PASSWORD");
                    throw new AuthException("login.error.invalid");
                }

                try (PreparedStatement ok = con.prepareStatement(
                        "UPDATE users SET failed_attempts = 0, last_login = NOW(), language = ? WHERE user_id = ?")) {
                    ok.setString(1, LanguageManager.getDbCode());
                    ok.setInt(2, userId);
                    ok.executeUpdate();
                }

                java.sql.Timestamp passwordChangedAt = rs.getTimestamp("password_changed_at");
                boolean passwordExpired = passwordChangedAt == null
                        || passwordChangedAt.toLocalDateTime().isBefore(java.time.LocalDateTime.now().minusDays(90));
                boolean mustChangePassword = rs.getBoolean("must_change_password") || passwordExpired;
                Session.start(userId, rs.getString("full_name"), rs.getString("username"),
                        rs.getString("role_name"), mustChangePassword);
                Session.setOrganizationId(rs.getInt("organization_id"));
                registerActiveSession(con, userId, Session.getSessionToken());
                recordLoginEvent(con, userId, loginName, "SUCCESS", passwordExpired ? "PASSWORD_EXPIRED" : "OK");
                if ("Client".equals(rs.getString("role_name")) || "Developer".equals(rs.getString("role_name"))) {
                    int cid = rs.getInt("client_id");
                    Session.setClientId(rs.wasNull() ? 0 : cid);   // a client only ever sees the projects of this client
                }
                Audit.log("LOGIN", "Authentication", "User logged in: " + rs.getString("username"));
            }
        }
    }

    public static void logout() {
        if (Session.getSessionToken() != null) {
            try {
                lk.technocom.util.Db.exec("UPDATE user_sessions SET revoked_at=NOW(), revoke_reason='LOGOUT' WHERE session_token=? AND revoked_at IS NULL", Session.getSessionToken());
            } catch (RuntimeException ignored) { /* logout must still clear local credentials if the database is unavailable */ }
        }
        Audit.log("LOGOUT", "Authentication", "User logged out: " + Session.getUsername());
        Session.clear();
    }

    private static void registerActiveSession(Connection con, int userId, String token) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO user_sessions(session_token,user_id,source,created_at,last_seen_at) VALUES(?,?,'DESKTOP',NOW(),NOW())")) {
            ps.setString(1, token);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private static boolean passwordMatches(String plain, String hash) {
        try {
            return BCrypt.checkpw(plain, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Stores a small, auditable record for every login decision. */
    private static void recordLoginEvent(Connection con, Integer userId, String loginName,
                                         String eventType, String reason) throws SQLException {
        String sql = "INSERT INTO login_events (user_id, login_name, event_type, reason, source) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (userId == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setInt(1, userId);
            ps.setString(2, loginName == null ? "" : loginName.substring(0, Math.min(100, loginName.length())));
            ps.setString(3, eventType);
            ps.setString(4, reason);
            ps.setString(5, "DESKTOP");
            ps.executeUpdate();
        }
    }

    private static void registerFailedAttempt(Connection con, int userId, int attempts) throws SQLException {
        String sql = attempts >= MAX_ATTEMPTS
                ? "UPDATE users SET failed_attempts = ?, status = 'LOCKED' WHERE user_id = ?"
                : "UPDATE users SET failed_attempts = ? WHERE user_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, attempts);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }
}
