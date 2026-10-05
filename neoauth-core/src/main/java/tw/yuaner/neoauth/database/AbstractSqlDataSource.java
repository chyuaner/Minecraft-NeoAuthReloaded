package tw.yuaner.neoauth.database;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.yuaner.neoauth.PasswordManager;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.platform.Services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽象 SQL 資料來源基底類別。
 * <p>
 * 實作 {@link IDataSource} 介面中 90% 共通之 ANSI SQL 業務邏輯（SELECT, INSERT, UPDATE,
 * 密碼加密校驗、帳號查詢等），並將連線池建立與資料庫專屬 DDL 語法差異留給子類別實作。
 */
public abstract class AbstractSqlDataSource implements IDataSource {

    protected static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-Database");
    protected HikariDataSource dataSource;
    protected IAuthConfig config;

    /**
     * 建立特定資料庫實作之 HikariDataSource 連線池。
     *
     * @param config 設定實例
     * @return 已設定好之 HikariDataSource
     * @throws Exception 初始化失敗時拋出
     */
    protected abstract HikariDataSource createDataSource(IAuthConfig config) throws Exception;

    /**
     * 取得特定資料庫方言之自動建立資料表 SQL 語法 (DDL)。
     *
     * @param config 設定實例
     * @return CREATE TABLE 語法字串
     */
    protected abstract String getCreateTableSql(IAuthConfig config);

    /**
     * 取得建立登入日誌資料表 (login_logs) SQL 語法 (DDL)。
     *
     * @return CREATE TABLE 語法字串
     */
    protected abstract String getCreateLoginLogsTableSql();

    @Override
    public synchronized void connect() throws Exception {
        connect(Services.PLATFORM.getConfig());
    }

    @Override
    public synchronized void connect(IAuthConfig config) throws Exception {
        close();
        this.config = config != null ? config : Services.PLATFORM.getConfig();
        this.dataSource = createDataSource(this.config);
        createTableIfNotExists(this.config);
        createLoginLogsTableIfNotExists();
    }

    protected IAuthConfig getConfig() {
        return this.config != null ? this.config : Services.PLATFORM.getConfig();
    }

    @Override
    public synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            LOGGER.info("NeoAuth: 正在關閉資料庫連線池...");
            dataSource.close();
            dataSource = null;
        }
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    protected void ensureConnected() {
        if (dataSource == null) {
            throw new DatabaseConnectionException("NeoAuth: 資料庫尚未建立連線");
        }
    }

    /**
     * 檢查並自動建立 AuthMe 相容之資料表結構。
     */
    protected void createTableIfNotExists(IAuthConfig config) {
        if (dataSource == null) return;
        String table = config.getDbTable();
        // 先檢查資料表是否已經存在且可存取，避免使用者無 CREATE 權限 (僅有 DML 權限) 時觸發權限拒絕例外
        try (Connection conn = dataSource.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM " + table + " LIMIT 1")) {
            checkStmt.executeQuery();
            LOGGER.info("NeoAuth: 資料表 {} 已存在且可正常存取。", table);
            return;
        } catch (SQLException checkEx) {
            // 資料表可能尚未建立或無存取權限，嘗試執行建立資料表語法
            LOGGER.debug("NeoAuth: 資料表 {} 尚未存在或無法直接查詢，嘗試自動建立...", table);
        }

        String sql = getCreateTableSql(config);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            // 若建立失敗，再次確認資料表是否已存在 (例如雖然無 CREATE 權限但資料表已由管理員建置好)
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM " + table + " LIMIT 1")) {
                checkStmt.executeQuery();
                LOGGER.warn("NeoAuth: 無法執行建立資料表指令 (可能缺乏 CREATE 權限)，但資料表 {} 已存在，繼續使用既有資料表。", table);
                return;
            } catch (SQLException ignored) {
            }
            LOGGER.error("NeoAuth: 建立資料表 {} 失敗", config.getDbTable(), e);
            throw new DatabaseConnectionException("NeoAuth: 建立資料表失敗", e);
        }
    }

    /**
     * 檢查並自動建立 login_logs 資料表。
     */
    protected void createLoginLogsTableIfNotExists() {
        if (dataSource == null) return;
        tw.yuaner.neoauth.config.LoginLogsConfig logCfg = ConfigManager.getInstance().getLoginLogsConfig();
        String table = logCfg != null ? logCfg.getTableName() : "login_logs";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM " + table + " LIMIT 1")) {
            checkStmt.executeQuery();
            LOGGER.info("NeoAuth: 資料表 {} 已存在且可正常存取。", table);
            migrateLoginLogsTable(conn, logCfg);
            return;
        } catch (SQLException checkEx) {
            LOGGER.debug("NeoAuth: 資料表 {} 尚未存在或無法直接查詢，嘗試自動建立...", table);
        }

        String sql = getCreateLoginLogsTableSql();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
            LOGGER.info("NeoAuth: 成功建立資料表 {}。", table);
        } catch (SQLException e) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM " + table + " LIMIT 1")) {
                checkStmt.executeQuery();
                LOGGER.warn("NeoAuth: 無法執行建立資料表指令 (可能缺乏 CREATE 權限)，但資料表 {} 已存在，繼續使用既有資料表。", table);
                migrateLoginLogsTable(conn, logCfg);
                return;
            } catch (SQLException ignored) {
            }
            LOGGER.error("NeoAuth: 建立資料表 {} 失敗", table, e);
            // Non-critical, do not throw exception, just log it.
        }
    }

    /**
     * 取得新增欄位的 SQL 語法 (DDL)。
     * 允許子類別覆寫以支援特定方言 (例如 MySQL 的 AFTER 語法)。
     *
     * @param table 資料表名稱
     * @param colName 欄位名稱
     * @param colType 欄位型態
     * @param afterCol 預期接在在哪個欄位之後 (支援的資料庫方言可用)
     * @return ALTER TABLE 語法字串
     */
    protected String getAddColumnSql(String table, String colName, String colType, String afterCol) {
        return "ALTER TABLE " + table + " ADD COLUMN " + colName + " " + colType;
    }

    private void migrateLoginLogsTable(Connection conn, tw.yuaner.neoauth.config.LoginLogsConfig logCfg) {
        if (logCfg == null) return;
        String table = logCfg.getTableName();
        String colServerHost = logCfg.getColumnServerHost();
        String colServerIp = logCfg.getColumnServerIp();
        String colServerPort = logCfg.getColumnServerPort();
        String colConnChannel = logCfg.getColumnConnectionChannel();
        String colCdnPop = logCfg.getColumnCdnPop();
        String colLoginOp = logCfg.getColumnLoginOpLevel();
        String colLogoutOp = logCfg.getColumnLogoutOpLevel();
        
        String colLoginMethod = logCfg.getColumnLoginMethod();
        String colUuid = logCfg.getColumnUuid();

        try {
            boolean hasServerHost = false;
            boolean hasServerIp = false;
            boolean hasServerPort = false;
            boolean hasConnChannel = false;
            boolean hasCdnPop = false;
            boolean hasLoginOpLevel = false;
            boolean hasLogoutOpLevel = false;

            java.sql.DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, null)) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (colServerHost.equalsIgnoreCase(col)) {
                        hasServerHost = true;
                    } else if (colCdnPop.equalsIgnoreCase(col)) {
                        hasCdnPop = true;
                    } else if (colServerIp.equalsIgnoreCase(col)) {
                        hasServerIp = true;
                    } else if (colServerPort.equalsIgnoreCase(col)) {
                        hasServerPort = true;
                    } else if (colConnChannel.equalsIgnoreCase(col)) {
                        hasConnChannel = true;
                    } else if (colLoginOp.equalsIgnoreCase(col)) {
                        hasLoginOpLevel = true;
                    } else if (colLogoutOp.equalsIgnoreCase(col)) {
                        hasLogoutOpLevel = true;
                    }
                }
            }
            if (!hasConnChannel) {
                String sql = getAddColumnSql(table, colConnChannel, "VARCHAR(50)", colLoginMethod);
                try (PreparedStatement alter = conn.prepareStatement(sql)) {
                    alter.execute();
                    LOGGER.info("NeoAuth: {} 資料表已自動擴充 {} 欄位。", table, colConnChannel);
                }
            }
            if (!hasCdnPop) {
                String sql = getAddColumnSql(table, colCdnPop, "VARCHAR(16)", colUuid);
                try (PreparedStatement alter = conn.prepareStatement(sql)) {
                    alter.execute();
                    LOGGER.info("NeoAuth: {} 資料表已自動擴充 {} 欄位。", table, colCdnPop);
                }
            }
            if (!hasServerHost) {
                String sql = getAddColumnSql(table, colServerHost, "VARCHAR(255)", colCdnPop);
                try (PreparedStatement alter = conn.prepareStatement(sql)) {
                    alter.execute();
                    LOGGER.info("NeoAuth: {} 資料表已自動擴充 {} 欄位。", table, colServerHost);
                }
            }
            if (!hasServerIp) {
                String sql = getAddColumnSql(table, colServerIp, "VARCHAR(45)", colServerHost);
                try (PreparedStatement alter = conn.prepareStatement(sql)) {
                    alter.execute();
                    LOGGER.info("NeoAuth: {} 資料表已自動擴充 {} 欄位。", table, colServerIp);
                }
            }
            if (!hasServerPort) {
                String sql = getAddColumnSql(table, colServerPort, "INT", colServerIp);
                try (PreparedStatement alter = conn.prepareStatement(sql)) {
                    alter.execute();
                    LOGGER.info("NeoAuth: {} 資料表已自動擴充 {} 欄位。", table, colServerPort);
                }
            }
            if (!hasLoginOpLevel) {
                String sql = getAddColumnSql(table, colLoginOp, "INT DEFAULT 0", colServerPort);
                try (PreparedStatement alter = conn.prepareStatement(sql)) {
                    alter.execute();
                    LOGGER.info("NeoAuth: {} 資料表已自動擴充 {} 欄位。", table, colLoginOp);
                }
            }
            if (!hasLogoutOpLevel) {
                String sql = getAddColumnSql(table, colLogoutOp, "INT DEFAULT NULL", colLoginOp);
                try (PreparedStatement alter = conn.prepareStatement(sql)) {
                    alter.execute();
                    LOGGER.info("NeoAuth: {} 資料表已自動擴充 {} 欄位。", table, colLogoutOp);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("NeoAuth: 檢查或遷移 {} 資料表欄位時出現警告 (可忽略): {}", table, e.getMessage());
        }
    }

    @Override
    public boolean isRegistered(String username) {
        if (username == null) return false;
        ensureConnected();
        IAuthConfig config = getConfig();
        String sql = "SELECT " + config.getMySqlColumnId() + " FROM " + config.getDbTable() + " WHERE " + config.getMySqlColumnName() + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 查詢玩家註冊狀態時發生資料庫錯誤", e);
            throw new DatabaseConnectionException("NeoAuth: 查詢玩家註冊狀態時發生資料庫錯誤", e);
        }
    }

    @Override
    public boolean hasPassword(String username) {
        if (username == null) return false;
        ensureConnected();
        IAuthConfig config = getConfig();
        String sql = "SELECT " + config.getMySqlColumnPassword() + " FROM " + config.getDbTable() + " WHERE " + config.getMySqlColumnName() + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String pwd = rs.getString(config.getMySqlColumnPassword());
                    return pwd != null && !pwd.isBlank();
                }
            }
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 檢查玩家是否有密碼時發生資料庫錯誤", e);
            throw new DatabaseConnectionException("NeoAuth: 檢查玩家是否有密碼時發生資料庫錯誤", e);
        }
        return false;
    }

    @Override
    public boolean checkPassword(String username, String password) {
        if (username == null || password == null) return false;
        ensureConnected();
        IAuthConfig config = getConfig();
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
            throw new DatabaseConnectionException("NeoAuth: 驗證密碼時發生資料庫錯誤", e);
        }
        return false;
    }

    @Override
    public boolean registerPlayer(String username, String password, String ip) {
        ensureConnected();
        if (isRegistered(username)) return false;

        IAuthConfig config = getConfig();
        String colSalt = config.getMySqlColumnSalt();
        boolean hasSaltCol = colSalt != null && !colSalt.isBlank();
        long now = System.currentTimeMillis();

        boolean isEmptyPassword = password == null || password.isEmpty();
        String hash;
        String salt = null;

        if (hasSaltCol) {
            if (isEmptyPassword) {
                salt = "";
                hash = "";
            } else {
                int saltLen = config.getDoubleMD5SaltLength() > 0 ? config.getDoubleMD5SaltLength() : 6;
                salt = PasswordManager.generateRandomSalt(saltLen);
                hash = PasswordManager.hashPasswordWithSalt(password, salt, config.getPasswordHash());
            }

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
                throw new DatabaseConnectionException("NeoAuth: 註冊玩家時發生資料庫錯誤", e);
            }
        } else {
            if (isEmptyPassword) {
                hash = "";
            } else {
                hash = PasswordManager.hashPassword(password);
            }

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
                throw new DatabaseConnectionException("NeoAuth: 註冊玩家時發生資料庫錯誤", e);
            }
        }
    }

    @Override
    public void updateLogin(String username, String ip) {
        if (username == null) return;
        ensureConnected();
        IAuthConfig config = getConfig();
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
            throw new DatabaseConnectionException("NeoAuth: 更新登入資訊時發生資料庫錯誤", e);
        }
    }

    @Override
    public void updateQuit(String username) {
        if (username == null) return;
        ensureConnected();
        IAuthConfig config = getConfig();
        String sql = "UPDATE " + config.getDbTable() + " SET " +
                config.getMySqlColumnLogged() + " = 0 WHERE " +
                config.getMySqlColumnName() + " = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 更新登出資訊時發生資料庫錯誤", e);
            throw new DatabaseConnectionException("NeoAuth: 更新登出資訊時發生資料庫錯誤", e);
        }
    }

    @Override
    public boolean changePassword(String username, String newPassword) {
        if (username == null || newPassword == null) return false;
        ensureConnected();
        if (!isRegistered(username)) return false;

        IAuthConfig config = getConfig();
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
                throw new DatabaseConnectionException("NeoAuth: 修改密碼時發生資料庫錯誤", e);
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
                throw new DatabaseConnectionException("NeoAuth: 修改密碼時發生資料庫錯誤", e);
            }
        }
    }

    @Override
    public PlayerAuthData getPlayerData(String username) {
        if (username == null) return null;
        ensureConnected();
        IAuthConfig config = getConfig();
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
            throw new DatabaseConnectionException("NeoAuth: 查詢玩家資料時發生資料庫錯誤", e);
        }
        return null;
    }

    @Override
    public String getEmail(String username) {
        PlayerAuthData data = getPlayerData(username);
        return data != null ? data.getEmail() : null;
    }

    @Override
    public boolean setEmail(String username, String email) {
        if (username == null) return false;
        ensureConnected();
        if (!isRegistered(username)) return false;
        IAuthConfig config = getConfig();
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
            throw new DatabaseConnectionException("NeoAuth: 更新 Email 時發生資料庫錯誤", e);
        }
    }

    @Override
    public String getIp(String username) {
        PlayerAuthData data = getPlayerData(username);
        return data != null ? data.getIp() : null;
    }

    @Override
    public List<String> getAccounts(String usernameOrIp) {
        List<String> accounts = new ArrayList<>();
        if (usernameOrIp == null || usernameOrIp.isBlank()) return accounts;
        ensureConnected();
        IAuthConfig config = getConfig();

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
        List<String> params = new ArrayList<>();
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
            throw new DatabaseConnectionException("NeoAuth: 查詢關聯帳號時發生資料庫錯誤", e);
        }
        return accounts;
    }

    @Override
    public List<PlayerAuthData> getRecentPlayers(int limit) {
        List<PlayerAuthData> list = new ArrayList<>();
        ensureConnected();
        IAuthConfig config = getConfig();
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
            throw new DatabaseConnectionException("NeoAuth: 查詢最近登入玩家時發生資料庫錯誤", e);
        }
        return list;
    }

    @Override
    public long insertLoginLog(String username, String uuid, String ip, String cdnPop, String serverHost, String serverIp, int serverPort, String loginMethod, String connectionChannel, String serverName, int loginOpLevel) {
        if (!isConnected()) return -1;
        tw.yuaner.neoauth.config.LoginLogsConfig logCfg = ConfigManager.getInstance().getLoginLogsConfig();
        String table = logCfg != null ? logCfg.getTableName() : "login_logs";
        String colId = logCfg != null ? logCfg.getColumnId() : "id";
        String colName = logCfg != null ? logCfg.getColumnName() : "username";
        String colUuid = logCfg != null ? logCfg.getColumnUuid() : "uuid";
        String colCdnPop = logCfg != null ? logCfg.getColumnCdnPop() : "cdn_pop";
        String colLoginTime = logCfg != null ? logCfg.getColumnLoginTime() : "login_time";
        String colIp = logCfg != null ? logCfg.getColumnIp() : "ip";
        String colServerHost = logCfg != null ? logCfg.getColumnServerHost() : "server_host";
        String colServerIp = logCfg != null ? logCfg.getColumnServerIp() : "server_ip";
        String colServerPort = logCfg != null ? logCfg.getColumnServerPort() : "server_port";
        String colLoginMethod = logCfg != null ? logCfg.getColumnLoginMethod() : "login_method";
        String colConnChannel = logCfg != null ? logCfg.getColumnConnectionChannel() : "connection_channel";
        String colServerName = logCfg != null ? logCfg.getColumnServerName() : "server_name";
        String colLoginOp = logCfg != null ? logCfg.getColumnLoginOpLevel() : "login_op_level";

        long id = tw.yuaner.neoauth.util.SnowflakeIdGenerator.getInstance().nextId();
        String sql = "INSERT INTO " + table + " (" +
                colId + ", " +
                colServerName + ", " +
                colName + ", " +
                colLoginTime + ", " +
                colIp + ", " +
                colLoginMethod + ", " +
                colConnChannel + ", " +
                colUuid + ", " +
                colCdnPop + ", " +
                colServerHost + ", " +
                colServerIp + ", " +
                colServerPort + ", " +
                colLoginOp + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.setString(2, serverName);
            stmt.setString(3, username);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.setString(5, ip);
            stmt.setString(6, loginMethod);
            stmt.setString(7, connectionChannel);
            stmt.setString(8, uuid);
            stmt.setString(9, cdnPop);
            stmt.setString(10, serverHost != null && !serverHost.isBlank() ? serverHost : (serverIp != null ? serverIp : "127.0.0.1"));
            stmt.setString(11, serverIp);
            stmt.setInt(12, serverPort);
            stmt.setInt(13, loginOpLevel);
            stmt.executeUpdate();
            return id;
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 新增登入日誌時發生資料庫錯誤", e);
        }
        return -1;
    }

    @Override
    public long insertLoginLog(String username, String uuid, String ip, String serverHost, String serverIp, int serverPort, String loginMethod, String connectionChannel, String serverName, int loginOpLevel) {
        return insertLoginLog(username, uuid, ip, null, serverHost, serverIp, serverPort, loginMethod, connectionChannel, serverName, loginOpLevel);
    }

    @Override
    public long insertLoginLog(String username, String uuid, String ip, String serverIp, int serverPort, String loginMethod, String connectionChannel, String serverName, int loginOpLevel) {
        return insertLoginLog(username, uuid, ip, serverIp, serverIp, serverPort, loginMethod, connectionChannel, serverName, loginOpLevel);
    }

    @Override
    public void updateLoginLogLogout(long logId, int logoutOpLevel) {
        if (!isConnected() || logId <= 0) return;
        tw.yuaner.neoauth.config.LoginLogsConfig logCfg = ConfigManager.getInstance().getLoginLogsConfig();
        String table = logCfg != null ? logCfg.getTableName() : "login_logs";
        String colId = logCfg != null ? logCfg.getColumnId() : "id";
        String colLogoutTime = logCfg != null ? logCfg.getColumnLogoutTime() : "logout_time";
        String colLogoutOp = logCfg != null ? logCfg.getColumnLogoutOpLevel() : "logout_op_level";

        String sql = "UPDATE " + table + " SET " + colLogoutTime + " = ?, " + colLogoutOp + " = ? WHERE " + colId + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setInt(2, logoutOpLevel);
            stmt.setLong(3, logId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 更新登入日誌登出資訊時發生資料庫錯誤", e);
        }
    }

    @Override
    public void updateLoginLogLogoutTime(long logId) {
        updateLoginLogLogout(logId, 0);
    }

    @Override
    public List<LoginLogRecord> getLoginLogs(String serverName, String username, long sinceTimestamp, int limit) {
        if (!isConnected()) {
            return new ArrayList<>();
        }
        tw.yuaner.neoauth.config.LoginLogsConfig logCfg = ConfigManager.getInstance().getLoginLogsConfig();
        String table = logCfg != null ? logCfg.getTableName() : "login_logs";
        String colId = logCfg != null ? logCfg.getColumnId() : "id";
        String colServerName = logCfg != null ? logCfg.getColumnServerName() : "server_name";
        String colName = logCfg != null ? logCfg.getColumnName() : "username";
        String colLoginTime = logCfg != null ? logCfg.getColumnLoginTime() : "login_time";
        String colLogoutTime = logCfg != null ? logCfg.getColumnLogoutTime() : "logout_time";
        String colIp = logCfg != null ? logCfg.getColumnIp() : "ip";
        String colLoginMethod = logCfg != null ? logCfg.getColumnLoginMethod() : "login_method";
        String colConnChannel = logCfg != null ? logCfg.getColumnConnectionChannel() : "connection_channel";
        String colUuid = logCfg != null ? logCfg.getColumnUuid() : "uuid";
        String colCdnPop = logCfg != null ? logCfg.getColumnCdnPop() : "cdn_pop";
        String colServerHost = logCfg != null ? logCfg.getColumnServerHost() : "server_host";
        String colServerIp = logCfg != null ? logCfg.getColumnServerIp() : "server_ip";
        String colServerPort = logCfg != null ? logCfg.getColumnServerPort() : "server_port";
        String colLoginOp = logCfg != null ? logCfg.getColumnLoginOpLevel() : "login_op_level";
        String colLogoutOp = logCfg != null ? logCfg.getColumnLogoutOpLevel() : "logout_op_level";

        int max = limit > 0 ? limit : 6;
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
        List<Object> params = new ArrayList<>();
        boolean hasWhere = false;

        if (serverName != null && !serverName.isBlank() && !serverName.equals("*") && !serverName.equalsIgnoreCase("all")) {
            sql.append(" WHERE LOWER(").append(colServerName).append(") = LOWER(?)");
            params.add(serverName);
            hasWhere = true;
        }

        if (username != null && !username.isBlank() && !username.equals("*") && !username.equalsIgnoreCase("all")) {
            sql.append(hasWhere ? " AND " : " WHERE ");
            sql.append("LOWER(").append(colName).append(") = LOWER(?)");
            params.add(username);
            hasWhere = true;
        }

        if (sinceTimestamp > 0) {
            sql.append(hasWhere ? " AND " : " WHERE ");
            sql.append(colLoginTime).append(" >= ?");
            params.add(sinceTimestamp);
            hasWhere = true;
        }

        sql.append(" ORDER BY ").append(colLoginTime).append(" DESC LIMIT ?");
        params.add(max);

        List<LoginLogRecord> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String s) {
                    stmt.setString(i + 1, s);
                } else if (p instanceof Long l) {
                    stmt.setLong(i + 1, l);
                } else if (p instanceof Integer integer) {
                    stmt.setInt(i + 1, integer);
                }
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(colId);
                    String srvName = getSafeString(rs, colServerName);
                    String uName = getSafeString(rs, colName);
                    long lTime = rs.getLong(colLoginTime);
                    long oTimeVal = rs.getLong(colLogoutTime);
                    Long oTime = rs.wasNull() ? null : oTimeVal;
                    String ipVal = getSafeString(rs, colIp);
                    String method = getSafeString(rs, colLoginMethod);
                    String connCh = getSafeString(rs, colConnChannel);
                    String uuid = getSafeString(rs, colUuid);
                    String cdn = getSafeString(rs, colCdnPop);
                    String host = getSafeString(rs, colServerHost);
                    String sIp = getSafeString(rs, colServerIp);
                    int sPort = getSafeInt(rs, colServerPort);
                    int lOp = getSafeInt(rs, colLoginOp);
                    int oOp = getSafeInt(rs, colLogoutOp);

                    results.add(new LoginLogRecord(id, srvName, uName, uuid, lTime, oTime, ipVal, method, connCh, cdn, host, sIp, sPort, lOp, oOp));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 查詢登入日誌失敗", e);
            throw new DatabaseConnectionException("NeoAuth: 查詢登入日誌失敗", e);
        }
        return results;
    }

    @Override
    public List<LoginLogRecord> getLoginLogs(String username, long sinceTimestamp, int limit) {
        return getLoginLogs(null, username, sinceTimestamp, limit);
    }

    private String getSafeString(ResultSet rs, String colName) {
        try {
            return rs.getString(colName);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private int getSafeInt(ResultSet rs, String colName) {
        try {
            return rs.getInt(colName);
        } catch (SQLException ignored) {
            return 0;
        }
    }
}
