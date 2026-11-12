package lk.technocom.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Database connection settings.
 * Order of lookup (first one that has the key wins):
 *   1. -Ddb.password=...  (JVM options)
 *   2. <user home>/TechnocomPMS/db.properties   (edit this file to change the password; no rebuild needed)
 *   3. /db.properties inside the application (src/main/resources/db.properties)
 */
public final class DBConnection {

    private static final Properties CONFIG = load();

    private DBConnection() {}

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = DBConnection.class.getResourceAsStream("/db.properties")) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {
            // use defaults
        }
        Path external = externalFile();
        if (Files.exists(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                p.load(in);
            } catch (IOException ignored) {
                // use defaults
            }
        }
        return p;
    }

    public static Path externalFile() {
        return Path.of(System.getProperty("user.home"), "TechnocomPMS", "db.properties");
    }

    public static String get(String key, String def) {
        String sys = System.getProperty(key);
        return sys != null ? sys : CONFIG.getProperty(key, def);
    }

    public static String database() {
        return get("db.name", "technocom_pms");
    }

    private static String base() {
        return "jdbc:mysql://" + get("db.host", "localhost") + ":" + get("db.port", "3306") + "/";
    }

    private static final String OPTIONS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Colombo"
            + "&characterEncoding=UTF-8&connectTimeout=5000";

    /** Connection to the application database. */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(base() + database() + OPTIONS, get("db.user", "root"), get("db.password", ""));
    }

    public static String describe() {
        return get("db.user", "root") + "@" + get("db.host", "localhost") + ":" + get("db.port", "3306");
    }
}
