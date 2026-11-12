package lk.technocom.service;

import lk.technocom.util.Audit;
import lk.technocom.util.Db;
import lk.technocom.util.Session;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

public final class PasswordService {

    private static final SecureRandom RND = new SecureRandom();
    private static final String ALPHA = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private PasswordService() {}

    public static String hash(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(10));
    }

    public static String randomPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(ALPHA.charAt(RND.nextInt(ALPHA.length())));
        return sb + "@" + (10 + RND.nextInt(90));
    }

    /** Returns an error message, or null when the password is acceptable. */
    public static String validate(String pw) {
        if (pw == null || pw.length() < 12) return "Password must be at least 12 characters.";
        if (!pw.matches(".*[A-Z].*")) return "Password needs an upper-case letter.";
        if (!pw.matches(".*[a-z].*")) return "Password needs a lower-case letter.";
        if (!pw.matches(".*\\d.*")) return "Password needs a number.";
        if (!pw.matches(".*[^A-Za-z0-9].*")) return "Password needs a symbol (for example @ or #).";
        return null;
    }

    public static boolean verify(int userId, String plain) {
        Object h = Db.scalar("SELECT password_hash FROM users WHERE user_id=?", userId);
        try {
            return plain != null && h != null && BCrypt.checkpw(plain, h.toString());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static void set(int userId, String plain, boolean mustChange) {
        // Keep the previous hash so the last five passwords cannot be reused.
        String newHash = hash(plain);
        Db.tx(connection -> {
            Db.exec(connection, "INSERT INTO password_history (user_id, password_hash) " +
                    "SELECT user_id, password_hash FROM users WHERE user_id=?", userId);
            Db.exec(connection, "UPDATE users SET password_hash=?, must_change_password=?, password_changed_at=NOW(), " +
                    "failed_attempts=0 WHERE user_id=?", newHash, mustChange, userId);
        });
    }

    /** True when the new password matches one of the last five stored passwords. */
    public static boolean wasUsedRecently(int userId, String candidate) {
        Object current = Db.scalar("SELECT password_hash FROM users WHERE user_id=?", userId);
        if (matches(candidate, current)) return true;
        for (Map<String, Object> row : Db.rows(
                "SELECT password_hash FROM password_history WHERE user_id=? ORDER BY history_id DESC LIMIT 5", userId)) {
            if (matches(candidate, row.get("password_hash"))) return true;
        }
        return false;
    }

    private static boolean matches(String candidate, Object storedHash) {
        if (candidate == null || storedHash == null) return false;
        try { return BCrypt.checkpw(candidate, storedHash.toString()); }
        catch (IllegalArgumentException e) { return false; }
    }

    /** Admin reset: returns the temporary password. */
    public static String adminReset(int userId) {
        if (!Session.isAdmin()) throw new SecurityException("Only an organisation administrator can reset another user's password.");
        int org=Session.getOrganizationId();
        if (org<=0 || Db.num("SELECT COUNT(*) FROM users WHERE user_id=? AND organization_id=?",userId,org)!=1)
            throw new SecurityException("User is outside the current organisation.");
        String tmp = randomPassword();
        set(userId, tmp, true);
        Db.exec("UPDATE user_sessions SET revoked_at=NOW(),revoke_reason='ADMIN_PASSWORD_RESET' WHERE user_id=? AND revoked_at IS NULL", userId);
        Db.exec("UPDATE users SET status='ACTIVE' WHERE user_id=? AND organization_id=? AND status='LOCKED'", userId, org);
        Audit.log("UPDATE", "Users", "Password reset for user id " + userId);
        return tmp;
    }

    public static String changeOwn(String current, String next) {
        if (!verify(Session.getUserId(), current)) return "Current password is wrong.";
        String err = validate(next);
        if (err != null) return err;
        if (wasUsedRecently(Session.getUserId(), next)) return "Choose a password you have not used recently.";
        set(Session.getUserId(), next, false);
        Session.setMustChange(false);
        Audit.log("UPDATE", "Account", "Password changed");
        return null;
    }

    // ---------- forgot-password flow (email code) ----------

    /** Sends a six-digit code. Returns 0 for unknown, throttled, or unavailable accounts. */
    public static int requestCode(String emailOrUser) {
        if (emailOrUser == null || emailOrUser.isBlank() || emailOrUser.length() > 254) return 0;
        Map<String, Object> u = Db.row("SELECT user_id, email, full_name FROM users "
                + "WHERE (email=? OR username=?) AND status='ACTIVE'", emailOrUser.trim(), emailOrUser.trim());
        if (u == null || u.get("email") == null || u.get("email").toString().isBlank()) return 0;
        int id = ((Number) u.get("user_id")).intValue();
        // At most one code per 30 seconds and three requests per rolling hour.
        if (Db.num("SELECT COUNT(*) FROM password_resets WHERE user_id=? AND created_at>DATE_SUB(NOW(),INTERVAL 30 SECOND)", id) > 0
                || Db.num("SELECT COUNT(*) FROM password_resets WHERE user_id=? AND created_at>DATE_SUB(NOW(),INTERVAL 1 HOUR)", id) >= 3) return 0;
        String code = String.format("%06d", RND.nextInt(1_000_000));
        Db.exec("UPDATE password_resets SET used=TRUE WHERE user_id=? AND used=FALSE", id);
        Db.exec("INSERT INTO password_resets (user_id, code_hash, expires_at, attempt_count, locked_until) VALUES (?,?,?,0,NULL)",
                id, BCrypt.hashpw(code, BCrypt.gensalt(10)), LocalDateTime.now().plusMinutes(10));
        boolean sent = EmailService.send(u.get("email").toString(), "Technocom PMS password reset code",
                "Hello " + u.get("full_name") + ",\n\nYour password reset code is: " + code
                        + "\nIt is valid for 10 minutes.\n\nIf you did not request this, ignore this email.",
                "RESET_CODE");
        if (!sent) {
            Db.exec("UPDATE password_resets SET used=TRUE WHERE user_id=? AND used=FALSE", id);
            return 0;
        }
        return id;
    }

    /** Checks a code and counts failures; five wrong attempts lock that code until expiry. */
    public static boolean checkCode(int userId, String code) {
        if (userId <= 0 || code == null || !code.matches("\\d{6}")) return false;
        Map<String, Object> r = Db.row("SELECT reset_id,code_hash,attempt_count FROM password_resets WHERE user_id=? AND used=FALSE "
                + "AND expires_at>NOW() AND (locked_until IS NULL OR locked_until<=NOW()) ORDER BY reset_id DESC LIMIT 1", userId);
        if (r == null) return false;
        boolean valid;
        try { valid = BCrypt.checkpw(code, r.get("code_hash").toString()); }
        catch (IllegalArgumentException e) { valid = false; }
        if (!valid) {
            long resetId = ((Number) r.get("reset_id")).longValue();
            Db.exec("UPDATE password_resets SET attempt_count=attempt_count+1, "
                    + "locked_until=CASE WHEN attempt_count+1>=5 THEN expires_at ELSE NULL END "
                    + "WHERE reset_id=? AND used=FALSE AND expires_at>NOW()", resetId);
        }
        return valid;
    }

    public static String resetWithCode(int userId, String code, String newPassword) {
        if (userId <= 0 || code == null || !code.matches("\\d{6}")) return "The code is wrong or has expired.";
        String err = validate(newPassword);
        if (err != null) return err;
        final String[] result = {null};
        Db.tx(connection -> {
            Map<String,Object> reset = Db.row(connection,
                    "SELECT reset_id,code_hash,attempt_count FROM password_resets WHERE user_id=? AND used=FALSE "
                            + "AND expires_at>NOW() AND (locked_until IS NULL OR locked_until<=NOW()) "
                            + "ORDER BY reset_id DESC LIMIT 1 FOR UPDATE", userId);
            if (reset == null) { result[0] = "The code is wrong or has expired."; return; }
            boolean valid;
            try { valid = BCrypt.checkpw(code, String.valueOf(reset.get("code_hash"))); }
            catch (IllegalArgumentException ex) { valid = false; }
            long resetId = ((Number) reset.get("reset_id")).longValue();
            if (!valid) {
                Db.exec(connection, "UPDATE password_resets SET attempt_count=attempt_count+1, "
                        + "locked_until=CASE WHEN attempt_count+1>=5 THEN expires_at ELSE NULL END WHERE reset_id=?", resetId);
                result[0] = "The code is wrong or has expired.";
                return;
            }
            Map<String,Object> user = Db.row(connection, "SELECT password_hash FROM users WHERE user_id=? FOR UPDATE", userId);
            if (user == null) { result[0] = "The account is unavailable."; return; }
            if (matches(newPassword, user.get("password_hash"))) {
                result[0] = "Choose a password you have not used recently."; return;
            }
            for (Map<String,Object> previous : Db.rows(connection,
                    "SELECT password_hash FROM password_history WHERE user_id=? ORDER BY history_id DESC LIMIT 5", userId)) {
                if (matches(newPassword, previous.get("password_hash"))) {
                    result[0] = "Choose a password you have not used recently."; return;
                }
            }
            Db.exec(connection, "INSERT INTO password_history(user_id,password_hash) SELECT user_id,password_hash FROM users WHERE user_id=?", userId);
            Db.exec(connection, "UPDATE users SET password_hash=?,must_change_password=FALSE,password_changed_at=NOW(),failed_attempts=0,status=CASE WHEN status='LOCKED' THEN 'ACTIVE' ELSE status END WHERE user_id=?",
                    hash(newPassword), userId);
            Db.exec(connection, "UPDATE password_resets SET used=TRUE WHERE reset_id=? AND used=FALSE", resetId);
        });
        return result[0];
    }
}
