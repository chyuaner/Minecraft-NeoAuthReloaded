package tw.yuaner.neoauth.database;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.yuaner.neoauth.PasswordManager;
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

    /**
     * 檢查並自動建立 AuthMe 相容之資料表結構。
     */
    protected void createTableIfNotExists(IAuthConfig config) {
        if (dataSource == null) return;
        String sql = getCreateTableSql(config);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            LOGGER.error("NeoAuth: 建立資料表 {} 失敗", config.getDbTable(), e);
        }
    }

    @Override
    public boolean isRegistered(String username) {
        if (dataSource == null || username == null) return false;
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
            return false;
        }
    }

    @Override
    public boolean hasPassword(String username) {
        if (dataSource == null || username == null) return false;
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
        }
        return false;
    }

    @Override
    public boolean checkPassword(String username, String password) {
        if (dataSource == null || username == null || password == null) return false;
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
        }
        return false;
    }

    @Override
    public boolean registerPlayer(String username, String password, String ip) {
        if (dataSource == null) return false;
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
                return false;
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
                return false;
            }
        }
    }

    @Override
    public void updateLogin(String username, String ip) {
        if (dataSource == null || username == null) return;
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
        }
    }

    @Override
    public void updateQuit(String username) {
        if (dataSource == null || username == null) return;
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
        }
    }

    @Override
    public boolean changePassword(String username, String newPassword) {
        if (dataSource == null || username == null || newPassword == null) return false;
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

    @Override
    public PlayerAuthData getPlayerData(String username) {
        if (dataSource == null || username == null) return null;
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
        if (dataSource == null || username == null) return false;
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
            return false;
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
        if (dataSource == null || usernameOrIp == null || usernameOrIp.isBlank()) return accounts;
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
        }
        return accounts;
    }

    @Override
    public List<PlayerAuthData> getRecentPlayers(int limit) {
        List<PlayerAuthData> list = new ArrayList<>();
        if (dataSource == null) return list;
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
        }
        return list;
    }
}
