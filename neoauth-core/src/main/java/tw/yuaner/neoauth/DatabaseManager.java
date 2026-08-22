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
 * 支援自訂欄位名稱 (包含 salt 欄位)、多種雜湊演算法 (BCrypt, SHA256, SALTED2MD5, SALTEDSHA512)
 * 並與 Bukkit 的 AuthMeReloaded / Discuz! / Phpwind / Blessing Skin 資料表結構保持 100% 相容。
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
        String colSalt = config.getMySqlColumnSalt();
        boolean hasSaltCol = colSalt != null && !colSalt.isBlank();

        String selectCols = config.getMySqlColumnPassword() + (hasSaltCol ? ", " + colSalt : "");
        String sql = "SELECT " + selectCols + " FROM " + config.getDbTable() + " WHERE " + config.getMySqlColumnName() + " = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString(config.getMySqlColumnPassword());
                    String salt = hasSaltCol ? rs.getString(colSalt) : null;
                    return PasswordManager.checkPassword(password, hash, salt);
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
        String colSalt = config.getMySqlColumnSalt();
        boolean hasSaltCol = colSalt != null && !colSalt.isBlank();
        long now = System.currentTimeMillis();

        String hash;
        String salt = null;

        if (hasSaltCol) {
            int saltLen = config.getDoubleMD5SaltLength() > 0 ? config.getDoubleMD5SaltLength() : 6;
            salt = PasswordManager.generateRandomSalt(saltLen);
            hash = PasswordManager.hashPasswordWithSalt(password, salt, config.getPasswordHash());

            String sql = "INSERT INTO " + config.getDbTable() + " (" +
                    config.getMySqlColumnName() + ", " +
                    config.getMySqlRealName() + ", " +
                    config.getMySqlColumnPassword() + ", " +
                    colSalt + ", " +
                    config.getMySqlColumnIp() + ", " +
                    config.getMySqlColumnRegisterIp() + ", " +
                    config.getMySqlColumnRegisterDate() + ", " +
                    config.getMySqlColumnLastLogin() +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                stmt.setString(2, username);
                stmt.setString(3, hash);
                stmt.setString(4, salt);
                stmt.setString(5, ip != null ? ip : "127.0.0.1");
                stmt.setString(6, ip != null ? ip : "127.0.0.1");
                stmt.setLong(7, now);
                stmt.setLong(8, now);

                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                LOGGER.error("NeoAuth: 註冊玩家時發生資料庫錯誤", e);
                return false;
            }
        } else {
            hash = PasswordManager.hashPassword(password);

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
     * 修改玩家密碼（管理員或玩家本人修改）。
     *
     * @param username    玩家名稱
     * @param newPassword 新密碼
     * @return true 若更新成功，否則為 false
     */
    public static boolean changePassword(String username, String newPassword) {
        if (dataSource == null || username == null || newPassword == null) return false;
        if (!isRegistered(username)) return false;

        IAuthConfig config = Services.PLATFORM.getConfig();
        String colSalt = config.getMySqlColumnSalt();
        boolean hasSaltCol = colSalt != null && !colSalt.isBlank();

        if (hasSaltCol) {
            int saltLen = config.getDoubleMD5SaltLength() > 0 ? config.getDoubleMD5SaltLength() : 6;
            String salt = PasswordManager.generateRandomSalt(saltLen);
            String hash = PasswordManager.hashPasswordWithSalt(newPassword, salt, config.getPasswordHash());

            String sql = "UPDATE " + config.getDbTable() + " SET " +
                    config.getMySqlColumnPassword() + " = ?, " +
                    colSalt + " = ? WHERE " +
                    config.getMySqlColumnName() + " = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, hash);
                stmt.setString(2, salt);
                stmt.setString(3, username.toLowerCase());
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                LOGGER.error("NeoAuth: 修改密碼時發生資料庫錯誤", e);
                return false;
            }
        } else {
            String hash = PasswordManager.hashPassword(newPassword);
            String sql = "UPDATE " + config.getDbTable() + " SET " +
                    config.getMySqlColumnPassword() + " = ? WHERE " +
                    config.getMySqlColumnName() + " = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, hash);
                stmt.setString(2, username.toLowerCase());
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                LOGGER.error("NeoAuth: 修改密碼時發生資料庫錯誤", e);
                return false;
            }
        }
    }

    /**
     * 查詢指定玩家的詳細帳號資料。
     *
     * @param username 玩家名稱
     * @return {@link PlayerAuthData} 物件，若不存在則回傳 null
     */
    public static PlayerAuthData getPlayerData(String username) {
        if (dataSource == null || username == null) return null;
        IAuthConfig config = Services.PLATFORM.getConfig();
        String sql = "SELECT " +
                config.getMySqlColumnName() + ", " +
                config.getMySqlRealName() + ", " +
                config.getMySqlColumnIp() + ", " +
                config.getMySqlColumnRegisterIp() + ", " +
                config.getMySqlColumnLastLogin() + ", " +
                config.getMySqlColumnRegisterDate() + ", " +
                config.getMySqlColumnEmail() +
                " FROM " + config.getDbTable() +
                " WHERE " + config.getMySqlColumnName() + " = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerAuthData(
                            rs.getString(config.getMySqlColumnName()),
                            rs.getString(config.getMySqlRealName()),
                            rs.getString(config.getMySqlColumnIp()),
                            rs.getString(config.getMySqlColumnRegisterIp()),
                            rs.getLong(config.getMySqlColumnLastLogin()),
                            rs.getLong(config.getMySqlColumnRegisterDate()),
                            rs.getString(config.getMySqlColumnEmail())
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 查詢玩家資料時發生資料庫錯誤", e);
        }
        return null;
    }

    /**
     * 取得指定玩家的 Email 資訊。
     */
    public static String getEmail(String username) {
        PlayerAuthData data = getPlayerData(username);
        return data != null ? data.getEmail() : null;
    }

    /**
     * 更新指定玩家的 Email 資訊。
     */
    public static boolean setEmail(String username, String email) {
        if (dataSource == null || username == null) return false;
        if (!isRegistered(username)) return false;
        IAuthConfig config = Services.PLATFORM.getConfig();
        String sql = "UPDATE " + config.getDbTable() + " SET " +
                config.getMySqlColumnEmail() + " = ? WHERE " +
                config.getMySqlColumnName() + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.setString(2, username.toLowerCase());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 更新 Email 時發生資料庫錯誤", e);
            return false;
        }
    }

    /**
     * 取得指定玩家最後登入的 IP 位址。
     */
    public static String getIp(String username) {
        PlayerAuthData data = getPlayerData(username);
        return data != null ? data.getIp() : null;
    }

    /**
     * 依據玩家名稱或 IP 位址，查詢所有關聯註冊/登入的帳號名稱。
     *
     * @param usernameOrIp 玩家名稱或 IP 字串
     * @return 關聯帳號名稱清單
     */
    public static java.util.List<String> getAccounts(String usernameOrIp) {
        java.util.List<String> accounts = new java.util.ArrayList<>();
        if (dataSource == null || usernameOrIp == null || usernameOrIp.isBlank()) return accounts;
        IAuthConfig config = Services.PLATFORM.getConfig();

        String ip1 = null;
        String ip2 = null;

        if (usernameOrIp.contains(".") || usernameOrIp.contains(":")) {
            ip1 = usernameOrIp.trim();
        } else {
            PlayerAuthData data = getPlayerData(usernameOrIp);
            if (data != null) {
                ip1 = data.getIp();
                ip2 = data.getRegIp();
            }
        }

        if (ip1 == null && ip2 == null) {
            return accounts;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT ").append(config.getMySqlRealName()).append(" FROM ").append(config.getDbTable()).append(" WHERE ");
        java.util.List<String> params = new java.util.ArrayList<>();
        if (ip1 != null && !ip1.isBlank()) {
            sql.append("(").append(config.getMySqlColumnIp()).append(" = ? OR ").append(config.getMySqlColumnRegisterIp()).append(" = ?)");
            params.add(ip1);
            params.add(ip1);
        }
        if (ip2 != null && !ip2.isBlank() && !ip2.equals(ip1)) {
            if (!params.isEmpty()) sql.append(" OR ");
            sql.append("(").append(config.getMySqlColumnIp()).append(" = ? OR ").append(config.getMySqlColumnRegisterIp()).append(" = ?)");
            params.add(ip2);
            params.add(ip2);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(config.getMySqlRealName());
                    if (name != null && !name.isBlank()) {
                        accounts.add(name);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 查詢關聯帳號時發生資料庫錯誤", e);
        }
        return accounts;
    }

    /**
     * 重設指定玩家（或全體玩家）在資料庫中的最後離線座標。
     *
     * @param playerOrWildcard 玩家名稱或 "*"
     * @return 影響的資料筆數
     */
    public static int resetPosition(String playerOrWildcard) {
        if (dataSource == null || playerOrWildcard == null) return 0;
        IAuthConfig config = Services.PLATFORM.getConfig();
        boolean isAll = "*".equals(playerOrWildcard.trim());

        String sql = "UPDATE " + config.getDbTable() + " SET " +
                config.getMySqlLastLocX() + " = 0.0, " +
                config.getMySqlLastLocY() + " = 0.0, " +
                config.getMySqlLastLocZ() + " = 0.0, " +
                config.getMySqlLastLocWorld() + " = 'world', " +
                config.getMySqlLastLocYaw() + " = 0.0, " +
                config.getMySqlLastLocPitch() + " = 0.0" +
                (isAll ? "" : " WHERE " + config.getMySqlColumnName() + " = ?");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (!isAll) {
                stmt.setString(1, playerOrWildcard.trim().toLowerCase());
            }
            return stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 重設玩家座標時發生資料庫錯誤", e);
            return 0;
        }
    }

    /**
     * 取得最近登入伺服器的玩家清單。
     *
     * @param limit 最大回傳數量
     * @return 玩家帳號資料清單
     */
    public static java.util.List<PlayerAuthData> getRecentPlayers(int limit) {
        java.util.List<PlayerAuthData> list = new java.util.ArrayList<>();
        if (dataSource == null) return list;
        IAuthConfig config = Services.PLATFORM.getConfig();
        int max = limit > 0 ? limit : 10;
        String sql = "SELECT " +
                config.getMySqlColumnName() + ", " +
                config.getMySqlRealName() + ", " +
                config.getMySqlColumnIp() + ", " +
                config.getMySqlColumnRegisterIp() + ", " +
                config.getMySqlColumnLastLogin() + ", " +
                config.getMySqlColumnRegisterDate() + ", " +
                config.getMySqlColumnEmail() +
                " FROM " + config.getDbTable() +
                " ORDER BY " + config.getMySqlColumnLastLogin() + " DESC LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, max);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new PlayerAuthData(
                            rs.getString(config.getMySqlColumnName()),
                            rs.getString(config.getMySqlRealName()),
                            rs.getString(config.getMySqlColumnIp()),
                            rs.getString(config.getMySqlColumnRegisterIp()),
                            rs.getLong(config.getMySqlColumnLastLogin()),
                            rs.getLong(config.getMySqlColumnRegisterDate()),
                            rs.getString(config.getMySqlColumnEmail())
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 查詢最近登入玩家時發生資料庫錯誤", e);
        }
        return list;
    }

    /**
     * 玩家驗證與帳號資料模型。
     */
    public static class PlayerAuthData {
        private final String username;
        private final String realName;
        private final String ip;
        private final String regIp;
        private final long lastLogin;
        private final long regDate;
        private final String email;

        public PlayerAuthData(String username, String realName, String ip, String regIp, long lastLogin, long regDate, String email) {
            this.username = username;
            this.realName = realName;
            this.ip = ip;
            this.regIp = regIp;
            this.lastLogin = lastLogin;
            this.regDate = regDate;
            this.email = email;
        }

        public String getUsername() { return username; }
        public String getRealName() { return realName; }
        public String getIp() { return ip; }
        public String getRegIp() { return regIp; }
        public long getLastLogin() { return lastLogin; }
        public long getRegDate() { return regDate; }
        public String getEmail() { return email; }
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
