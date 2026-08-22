package tw.yuaner.neoauth;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.platform.Services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MariaDB / MySQL 資料庫連線池與 SQL 查詢操作管理員。
 * <p>
 * 使用高效能的 {@link HikariDataSource} 連線池連線至資料庫，
 * 支援自訂欄位名稱並與 Bukkit 的 AuthMeReloaded 資料表結構保持 100% 相容。
 */
public class DatabaseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-Database");
    private static HikariDataSource dataSource;

    /**
     * 初始化資料庫連線池並建立必要資料表。
     * <p>
     * 讀取當前設定檔中的主機、埠號、帳密、SSL 設定與自訂欄位名稱。
     */
    public static synchronized void init() {
        close();

        IAuthConfig config = Services.PLATFORM.getConfig();
        String host = config.getDbHost();
        String port = config.getDbPort();
        String dbName = config.getDbName();
        String username = config.getDbUsername();
        String password = config.getDbPassword();
        int poolSize = config.getDbPoolSize();
        int maxLifetime = config.getDbMaxLifetime();
        String backend = config.getDbBackend();

        HikariConfig hikariConfig = new HikariConfig();
        String protocol = "MARIADB".equalsIgnoreCase(backend) ? "mariadb" : "mysql";
        hikariConfig.setJdbcUrl("jdbc:" + protocol + "://" + host + ":" + port + "/" + dbName);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(poolSize > 0 ? poolSize : 10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setIdleTimeout(30000);
        hikariConfig.setMaxLifetime(maxLifetime > 0 ? maxLifetime * 1000L : 1800000L);
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        // SSL 與 RSA 公鑰檢索設定
        hikariConfig.addDataSourceProperty("useSSL", String.valueOf(config.isMySqlUseSSL()));
        hikariConfig.addDataSourceProperty("verifyServerCertificate", String.valueOf(config.isMySqlCheckServerCertificate()));
        hikariConfig.addDataSourceProperty("allowPublicKeyRetrieval", String.valueOf(config.isMySqlAllowPublicKeyRetrieval()));

        try {
            dataSource = new HikariDataSource(hikariConfig);
            createTableIfNotExists();
            LOGGER.info("NeoAuth: 成功連線至 {} 資料庫 [{}:{}/{}]！", backend, host, port, dbName);
        } catch (Exception e) {
            LOGGER.error("NeoAuth: 無法連線至資料庫！請檢查 config/neoauth/config.yml 之資料庫設定。錯誤原因: {}", e.getMessage());
            dataSource = null;
        }
    }

    /**
     * 檢查並自動建立 AuthMe 相容之使用者資料表。
     */
    private static void createTableIfNotExists() {
        IAuthConfig config = Services.PLATFORM.getConfig();
        String table = config.getDbTable();
        String colId = config.getMySqlColumnId();
        String colName = config.getMySqlColumnName();
        String colPassword = config.getMySqlColumnPassword();
        String colTotp = config.getMySqlTotpKey();
        String colIp = config.getMySqlColumnIp();
        String colLastLogin = config.getMySqlColumnLastLogin();
        String colRegDate = config.getMySqlColumnRegisterDate();
        String colRegIp = config.getMySqlColumnRegisterIp();
        String colX = config.getMySqlLastLocX();
        String colY = config.getMySqlLastLocY();
        String colZ = config.getMySqlLastLocZ();
        String colWorld = config.getMySqlLastLocWorld();
        String colYaw = config.getMySqlLastLocYaw();
        String colPitch = config.getMySqlLastLocPitch();
        String colEmail = config.getMySqlColumnEmail();
        String colLogged = config.getMySqlColumnLogged();
        String colRealName = config.getMySqlRealName();
        String colSalt = config.getMySqlColumnSalt();
        String colHasSession = config.getMySqlColumnHasSession();
        String colPlayerUUID = config.getMySqlPlayerUUID();

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(table).append(" (");
        sb.append(colId).append(" INTEGER AUTO_INCREMENT PRIMARY KEY, ");
        sb.append(colName).append(" VARCHAR(255) NOT NULL UNIQUE, ");
        sb.append(colPassword).append(" VARCHAR(255) NOT NULL, ");
        if (colTotp != null && !colTotp.isBlank()) {
            sb.append(colTotp).append(" VARCHAR(16), ");
        }
        sb.append(colIp).append(" VARCHAR(40), ");
        sb.append(colLastLogin).append(" BIGINT, ");
        sb.append(colRegDate).append(" BIGINT NOT NULL, ");
        sb.append(colRegIp).append(" VARCHAR(40), ");
        sb.append(colX).append(" DOUBLE NOT NULL DEFAULT '0.0', ");
        sb.append(colY).append(" DOUBLE NOT NULL DEFAULT '0.0', ");
        sb.append(colZ).append(" DOUBLE NOT NULL DEFAULT '0.0', ");
        sb.append(colWorld).append(" VARCHAR(255) NOT NULL DEFAULT 'world', ");
        sb.append(colYaw).append(" FLOAT, ");
        sb.append(colPitch).append(" FLOAT, ");
        sb.append(colEmail).append(" VARCHAR(255), ");
        sb.append(colLogged).append(" INT DEFAULT '0', ");
        sb.append(colRealName).append(" VARCHAR(255) NOT NULL DEFAULT 'Player', ");
        if (colSalt != null && !colSalt.isBlank()) {
            sb.append(colSalt).append(" VARCHAR(255), ");
        }
        sb.append(colHasSession).append(" INT NOT NULL DEFAULT '0', ");
        if (colPlayerUUID != null && !colPlayerUUID.isBlank()) {
            sb.append(colPlayerUUID).append(" VARCHAR(36)");
        } else {
            sb.append("playerUUID VARCHAR(36)");
        }
        sb.append(");");

        String sql = sb.toString();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 建立資料表 {} 失敗", table, e);
        }
    }

    /**
     * 檢查指定的使用者名稱是否已經註冊。
     *
     * @param username 玩家名稱
     * @return true 若已註冊，否則為 false
     */
    public static boolean isRegistered(String username) {
        if (dataSource == null || username == null) return false;
        IAuthConfig config = Services.PLATFORM.getConfig();
        String sql = "SELECT " + config.getMySqlColumnId() + " FROM " + config.getDbTable() + " WHERE " + config.getMySqlColumnName() + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 查詢玩家註冊狀態時發生資料庫錯誤", e);
            return false;
        }
    }

    /**
     * 檢查玩家輸入的密碼是否正確。
     *
     * @param username 玩家名稱
     * @param password 輸入的密碼
     * @return true 若密碼正確，否則為 false
     */
    public static boolean checkPassword(String username, String password) {
        if (dataSource == null || username == null || password == null) return false;
        IAuthConfig config = Services.PLATFORM.getConfig();
        String sql = "SELECT " + config.getMySqlColumnPassword() + " FROM " + config.getDbTable() + " WHERE " + config.getMySqlColumnName() + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString(config.getMySqlColumnPassword());
                    return PasswordManager.checkPassword(password, hash);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 驗證密碼時發生資料庫錯誤", e);
        }
        return false;
    }

    /**
     * 註冊新玩家資料至資料庫中。
     *
     * @param username 玩家名稱
     * @param password 純文字密碼
     * @param ip       玩家註冊時的 IP 位址
     * @return true 若註冊成功，否則為 false
     */
    public static boolean registerPlayer(String username, String password, String ip) {
        if (dataSource == null) return false;
        if (isRegistered(username)) return false;

        IAuthConfig config = Services.PLATFORM.getConfig();
        String hash = PasswordManager.hashPassword(password);
        long now = System.currentTimeMillis();

        String sql = "INSERT INTO " + config.getDbTable() + " (" +
                config.getMySqlColumnName() + ", " +
                config.getMySqlRealName() + ", " +
                config.getMySqlColumnPassword() + ", " +
                config.getMySqlColumnIp() + ", " +
                config.getMySqlColumnRegisterIp() + ", " +
                config.getMySqlColumnRegisterDate() + ", " +
                config.getMySqlColumnLastLogin() +
                ") VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            stmt.setString(2, username);
            stmt.setString(3, hash);
            stmt.setString(4, ip != null ? ip : "127.0.0.1");
            stmt.setString(5, ip != null ? ip : "127.0.0.1");
            stmt.setLong(6, now);
            stmt.setLong(7, now);

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 註冊玩家時發生資料庫錯誤", e);
            return false;
        }
    }

    /**
     * 更新玩家登入時間與登入 IP 位址。
     *
     * @param username 玩家名稱
     * @param ip       玩家當前 IP 位址
     */
    public static void updateLogin(String username, String ip) {
        if (dataSource == null || username == null) return;
        IAuthConfig config = Services.PLATFORM.getConfig();
        String sql = "UPDATE " + config.getDbTable() + " SET " +
                config.getMySqlColumnLastLogin() + " = ?, " +
                config.getMySqlColumnIp() + " = ?, " +
                config.getMySqlColumnLogged() + " = 1 WHERE " +
                config.getMySqlColumnName() + " = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, ip != null ? ip : "127.0.0.1");
            stmt.setString(3, username.toLowerCase());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 更新登入資訊時發生資料庫錯誤", e);
        }
    }

    /**
     * 關閉資料庫連線池（伺服器關閉時呼叫）。
     */
    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            LOGGER.info("NeoAuth: 正在關閉資料庫連線池...");
            dataSource.close();
            dataSource = null;
        }
    }
}
