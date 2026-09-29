package tw.yuaner.neoauth.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.platform.Services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 資料庫備援代理類別。
 * <p>
 * 將讀取操作代理至主資料庫 (如 MySQL)，若主資料庫發生連線異常，則無縫切換至備援資料庫 (SQLite)。
 * 寫入操作會優先寫入主資料庫，成功後再同步至備援資料庫。
 */
public class FallbackDataSource implements IDataSource {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-Database");
    private final IDataSource primary;
    private final IDataSource fallback;

    private volatile IAuthConfig config;

    public FallbackDataSource(IDataSource primary, IDataSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    private IAuthConfig resolveConfig() {
        if (this.config != null) return this.config;
        try {
            if (tw.yuaner.neoauth.config.ConfigManager.getInstance().getConfig() != null) {
                return tw.yuaner.neoauth.config.ConfigManager.getInstance().getConfig();
            }
        } catch (Throwable ignored) {}
        try {
            return Services.PLATFORM.getConfig();
        } catch (Throwable ignored) {}
        return null;
    }

    @Override
    public void connect() throws Exception {
        connect(resolveConfig());
    }

    @Override
    public void connect(IAuthConfig config) throws Exception {
        this.config = config != null ? config : resolveConfig();

        boolean fallbackOk = false;
        try {
            fallback.connect(this.config);
            fallbackOk = true;
        } catch (Exception e) {
            LOGGER.error("NeoAuth: 本地 SQLite 備援資料庫初始化失敗 (請檢查目錄寫入權限): {}", e.getMessage());
        }

        boolean primaryOk = false;
        try {
            primary.connect(this.config);
            markPrimarySuccess();
            LOGGER.info("NeoAuth: 主資料庫連線成功！");
            primaryOk = true;

            if (fallbackOk && primary instanceof AbstractSqlDataSource pSql && fallback instanceof AbstractSqlDataSource fSql) {
                syncData(pSql, fSql, this.config);
                syncLoginLogs(pSql, fSql);
            }
        } catch (Exception e) {
            markPrimaryFailure();
            if (fallbackOk) {
                LOGGER.warn("NeoAuth: 主資料庫連線失敗，已自動降級為本地 SQLite 備援模式 (唯讀登入): {}", e.getMessage());
            } else {
                LOGGER.error("NeoAuth: 主資料庫與本地備援資料庫皆連線失敗！");
                throw e;
            }
        }

        if (!fallbackOk && !primaryOk) {
            throw new DatabaseConnectionException("NeoAuth: 主資料庫與備援資料庫皆無法連線！");
        }
    }

    private void syncData(AbstractSqlDataSource primary, AbstractSqlDataSource fallback, IAuthConfig config) {
        if (primary.dataSource == null || fallback.dataSource == null) return;
        LOGGER.info("NeoAuth: 正在從主資料庫同步資料至 SQLite 備援資料庫...");
        String table = config.getDbTable();

        try {
            // 取得 fallback SQLite 的所有欄位名稱 (轉為小寫以利大小寫不敏感比對)
            Set<String> fallbackCols = new HashSet<>();
            try (Connection fConn = fallback.dataSource.getConnection();
                 Statement fStmt = fConn.createStatement();
                 ResultSet fRs = fStmt.executeQuery("SELECT * FROM " + table + " WHERE 1=0")) {
                ResultSetMetaData fMeta = fRs.getMetaData();
                for (int i = 1; i <= fMeta.getColumnCount(); i++) {
                    fallbackCols.add(fMeta.getColumnName(i).toLowerCase(Locale.ROOT));
                }
            }

            // 取得 primary 與 fallback 兩端皆存在的共同欄位
            List<String> commonCols = new ArrayList<>();
            try (Connection pConn = primary.dataSource.getConnection();
                 Statement pStmt = pConn.createStatement();
                 ResultSet pRs = pStmt.executeQuery("SELECT * FROM " + table + " WHERE 1=0")) {
                ResultSetMetaData pMeta = pRs.getMetaData();
                for (int i = 1; i <= pMeta.getColumnCount(); i++) {
                    String colName = pMeta.getColumnName(i);
                    if (fallbackCols.contains(colName.toLowerCase(Locale.ROOT))) {
                        commonCols.add(colName);
                    }
                }
            }

            if (commonCols.isEmpty()) {
                LOGGER.warn("NeoAuth: 主資料庫與 SQLite 備援資料庫無共同欄位，略過資料同步。");
                return;
            }

            String selectCols = String.join(", ", commonCols);
            String selectSql = "SELECT " + selectCols + " FROM " + table;

            StringBuilder insertSql = new StringBuilder("INSERT OR REPLACE INTO ").append(table).append(" (");
            insertSql.append(selectCols).append(") VALUES (");
            for (int i = 0; i < commonCols.size(); i++) {
                insertSql.append("?");
                if (i < commonCols.size() - 1) {
                    insertSql.append(", ");
                }
            }
            insertSql.append(")");

            int count = 0;
            try (Connection pConn = primary.dataSource.getConnection();
                 PreparedStatement pStmt = pConn.prepareStatement(selectSql);
                 ResultSet rs = pStmt.executeQuery();
                 Connection fConn = fallback.dataSource.getConnection();
                 PreparedStatement fStmt = fConn.prepareStatement(insertSql.toString())) {

                while (rs.next()) {
                    for (int i = 1; i <= commonCols.size(); i++) {
                        fStmt.setObject(i, rs.getObject(i));
                    }
                    fStmt.addBatch();
                    count++;
                }
                fStmt.executeBatch();
                LOGGER.info("NeoAuth: SQLite 備援資料庫同步完成！共同步 {} 筆帳號資料。", count);
            }
        } catch (Exception e) {
            LOGGER.warn("NeoAuth: 同步至 SQLite 備援資料庫時發生錯誤 (不影響主流程)", e);
        }
    }

    private static final long COOLDOWN_MS = 30000L; // 30 秒熔斷冷卻重試週期
    private volatile long lastPrimaryFailureTime = 0L;

    private synchronized boolean tryReconnectPrimary() {
        if (primary == null) return false;
        if (primary.isConnected()) return true;
        try {
            LOGGER.info("NeoAuth: 正在嘗試重新連線至主資料庫...");
            primary.connect(resolveConfig());
            markPrimarySuccess();
            LOGGER.info("NeoAuth: 主資料庫重新連線成功！");
            if (primary instanceof AbstractSqlDataSource pSql && fallback instanceof AbstractSqlDataSource fSql) {
                syncData(pSql, fSql, this.config);
                syncLoginLogs(pSql, fSql);
            }
            return true;
        } catch (Exception e) {
            markPrimaryFailure();
            LOGGER.warn("NeoAuth: 重新連線主資料庫失敗: {}", e.getMessage());
            return false;
        }
    }

    private boolean isPrimaryHealthy() {
        if (lastPrimaryFailureTime == 0L) {
            if (!primary.isConnected()) {
                return tryReconnectPrimary();
            }
            return true;
        }
        if (System.currentTimeMillis() - lastPrimaryFailureTime > COOLDOWN_MS) {
            if (!primary.isConnected()) {
                return tryReconnectPrimary();
            }
            return true;
        }
        return false;
    }

    private void markPrimarySuccess() {
        if (lastPrimaryFailureTime != 0L) {
            LOGGER.info("NeoAuth: 主資料庫已恢復正常連線！");
            lastPrimaryFailureTime = 0L;
        }
    }

    private void markPrimaryFailure() {
        lastPrimaryFailureTime = System.currentTimeMillis();
    }

    @Override
    public void close() {
        if (primary != null) {
            try { primary.close(); } catch (Exception ignored) {}
        }
        if (fallback != null) {
            try { fallback.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean isConnected() {
        return (primary != null && primary.isConnected()) || (fallback != null && fallback.isConnected());
    }

    @Override
    public boolean isFallbackActive() {
        return !isPrimaryHealthy() && fallback != null && fallback.isConnected();
    }

    @Override
    public boolean isRegistered(String username) {
        if (isPrimaryHealthy()) {
            try {
                boolean res = primary.isRegistered(username);
                markPrimarySuccess();
                return res;
            } catch (DatabaseConnectionException e) {
                markPrimaryFailure();
                if (fallback != null && fallback.isConnected()) {
                    LOGGER.warn("NeoAuth: 主資料庫連線異常，啟用 SQLite 備援進行查詢: {}", username);
                    return fallback.isRegistered(username);
                }
                throw e;
            }
        }
        if (fallback != null && fallback.isConnected()) {
            return fallback.isRegistered(username);
        }
        return primary.isRegistered(username);
    }

    @Override
    public boolean hasPassword(String username) {
        if (isPrimaryHealthy()) {
            try {
                boolean res = primary.hasPassword(username);
                markPrimarySuccess();
                return res;
            } catch (DatabaseConnectionException e) {
                markPrimaryFailure();
                if (fallback != null && fallback.isConnected()) {
                    LOGGER.warn("NeoAuth: 主資料庫連線異常，啟用 SQLite 備援進行查詢: {}", username);
                    return fallback.hasPassword(username);
                }
                throw e;
            }
        }
        if (fallback != null && fallback.isConnected()) {
            return fallback.hasPassword(username);
        }
        return primary.hasPassword(username);
    }

    @Override
    public boolean checkPassword(String username, String password) {
        if (isPrimaryHealthy()) {
            try {
                boolean res = primary.checkPassword(username, password);
                markPrimarySuccess();
                return res;
            } catch (DatabaseConnectionException e) {
                markPrimaryFailure();
                if (fallback != null && fallback.isConnected()) {
                    LOGGER.warn("NeoAuth: 主資料庫連線異常，啟用 SQLite 備援進行密碼驗證: {}", username);
                    return fallback.checkPassword(username, password);
                }
                throw e;
            }
        }
        if (fallback != null && fallback.isConnected()) {
            return fallback.checkPassword(username, password);
        }
        return primary.checkPassword(username, password);
    }

    @Override
    public boolean registerPlayer(String username, String password, String ip) {
        if (!isPrimaryHealthy()) {
            LOGGER.warn("NeoAuth: 主資料庫連線異常，禁止離線註冊: {}", username);
            throw new DatabaseConnectionException("主資料庫連線異常，禁止離線註冊");
        }
        boolean result;
        try {
            result = primary.registerPlayer(username, password, ip);
            markPrimarySuccess();
        } catch (DatabaseConnectionException e) {
            markPrimaryFailure();
            LOGGER.warn("NeoAuth: 主資料庫連線異常，禁止離線註冊: {}", username);
            throw e;
        }
        if (result) {
            try {
                fallback.registerPlayer(username, password, ip);
            } catch (Exception e) {
                LOGGER.warn("NeoAuth: 寫入備援資料庫失敗: {}", username, e);
            }
        }
        return result;
    }

    @Override
    public void updateLogin(String username, String ip) {
        if (isPrimaryHealthy()) {
            try {
                primary.updateLogin(username, ip);
                markPrimarySuccess();
            } catch (Exception e) {
                markPrimaryFailure();
                LOGGER.warn("NeoAuth: 主資料庫連線異常，略過主庫登入記錄更新: {}", username);
            }
        }
        try {
            fallback.updateLogin(username, ip);
        } catch (Exception ignored) {}
    }

    @Override
    public void updateQuit(String username) {
        if (isPrimaryHealthy()) {
            try {
                primary.updateQuit(username);
                markPrimarySuccess();
            } catch (Exception e) {
                markPrimaryFailure();
                LOGGER.warn("NeoAuth: 主資料庫連線異常，略過主庫登出記錄更新: {}", username);
            }
        }
        try {
            fallback.updateQuit(username);
        } catch (Exception ignored) {}
    }

    @Override
    public boolean changePassword(String username, String newPassword) {
        if (!isPrimaryHealthy()) {
            LOGGER.warn("NeoAuth: 主資料庫連線異常，禁止離線修改密碼: {}", username);
            throw new DatabaseConnectionException("主資料庫連線異常，禁止離線修改密碼");
        }
        boolean result;
        try {
            result = primary.changePassword(username, newPassword);
            markPrimarySuccess();
        } catch (DatabaseConnectionException e) {
            markPrimaryFailure();
            LOGGER.warn("NeoAuth: 主資料庫連線異常，禁止離線修改密碼: {}", username);
            throw e;
        }
        if (result) {
            try {
                fallback.changePassword(username, newPassword);
            } catch (Exception e) {
                LOGGER.warn("NeoAuth: 寫入備援資料庫失敗: {}", username, e);
            }
        }
        return result;
    }

    @Override
    public PlayerAuthData getPlayerData(String username) {
        if (isPrimaryHealthy()) {
            try {
                PlayerAuthData data = primary.getPlayerData(username);
                markPrimarySuccess();
                return data;
            } catch (DatabaseConnectionException e) {
                markPrimaryFailure();
                if (fallback != null && fallback.isConnected()) {
                    LOGGER.warn("NeoAuth: 主資料庫連線異常，啟用 SQLite 備援進行查詢: {}", username);
                    return fallback.getPlayerData(username);
                }
                throw e;
            }
        }
        if (fallback != null && fallback.isConnected()) {
            return fallback.getPlayerData(username);
        }
        return primary.getPlayerData(username);
    }

    @Override
    public String getEmail(String username) {
        if (isPrimaryHealthy()) {
            try {
                String email = primary.getEmail(username);
                markPrimarySuccess();
                return email;
            } catch (DatabaseConnectionException e) {
                markPrimaryFailure();
                return fallback.getEmail(username);
            }
        }
        return fallback.getEmail(username);
    }

    @Override
    public boolean setEmail(String username, String email) {
        if (!isPrimaryHealthy()) {
            LOGGER.warn("NeoAuth: 主資料庫連線異常，禁止離線設定信箱: {}", username);
            throw new DatabaseConnectionException("主資料庫連線異常，禁止離線設定信箱");
        }
        boolean result;
        try {
            result = primary.setEmail(username, email);
            markPrimarySuccess();
        } catch (DatabaseConnectionException e) {
            markPrimaryFailure();
            LOGGER.warn("NeoAuth: 主資料庫連線異常，禁止離線設定信箱: {}", username);
            throw e;
        }
        if (result) {
            try {
                fallback.setEmail(username, email);
            } catch (Exception ignored) {}
        }
        return result;
    }

    @Override
    public String getIp(String username) {
        if (isPrimaryHealthy()) {
            try {
                String ip = primary.getIp(username);
                markPrimarySuccess();
                return ip;
            } catch (DatabaseConnectionException e) {
                markPrimaryFailure();
                return fallback.getIp(username);
            }
        }
        return fallback.getIp(username);
    }

    @Override
    public List<String> getAccounts(String usernameOrIp) {
        if (isPrimaryHealthy()) {
            try {
                List<String> accounts = primary.getAccounts(usernameOrIp);
                markPrimarySuccess();
                return accounts;
            } catch (DatabaseConnectionException e) {
                markPrimaryFailure();
                LOGGER.warn("NeoAuth: 主資料庫連線異常，啟用 SQLite 備援進行查詢: {}", usernameOrIp);
                return fallback.getAccounts(usernameOrIp);
            }
        }
        return fallback.getAccounts(usernameOrIp);
    }

    @Override
    public List<PlayerAuthData> getRecentPlayers(int limit) {
        if (isPrimaryHealthy()) {
            try {
                List<PlayerAuthData> list = primary.getRecentPlayers(limit);
                markPrimarySuccess();
                return list;
            } catch (DatabaseConnectionException e) {
                markPrimaryFailure();
                return fallback.getRecentPlayers(limit);
            }
        }
        return fallback.getRecentPlayers(limit);
    }

    @Override
    public long insertLoginLog(String username, String uuid, String ip, String serverIp, int serverPort, String loginMethod, String connectionChannel, String serverName, int loginOpLevel) {
        if (isPrimaryHealthy()) {
            try {
                long logId = primary.insertLoginLog(username, uuid, ip, serverIp, serverPort, loginMethod, connectionChannel, serverName, loginOpLevel);
                markPrimarySuccess();
                return logId;
            } catch (Exception e) {
                markPrimaryFailure();
                LOGGER.warn("NeoAuth: 主資料庫連線異常，將登入日誌降級寫入本地 SQLite 備援資料庫: {}", username);
                return fallback.insertLoginLog(username, uuid, ip, serverIp, serverPort, loginMethod, connectionChannel, serverName, loginOpLevel);
            }
        }
        return fallback.insertLoginLog(username, uuid, ip, serverIp, serverPort, loginMethod, connectionChannel, serverName, loginOpLevel);
    }

    @Override
    public void updateLoginLogLogout(long logId, int logoutOpLevel) {
        if (logId <= 0) return;
        if (isPrimaryHealthy()) {
            try {
                primary.updateLoginLogLogout(logId, logoutOpLevel);
                markPrimarySuccess();
                try {
                    fallback.updateLoginLogLogout(logId, logoutOpLevel);
                } catch (Exception ignored) {}
                return;
            } catch (Exception e) {
                markPrimaryFailure();
                LOGGER.warn("NeoAuth: 主資料庫連線異常，略過主庫登出日誌更新 (logId: {})", logId);
            }
        }
        try {
            fallback.updateLoginLogLogout(logId, logoutOpLevel);
        } catch (Exception ignored) {}
    }

    @Override
    public void syncLoginLogs() {
        if (primary instanceof AbstractSqlDataSource pSql && fallback instanceof AbstractSqlDataSource fSql) {
            syncLoginLogs(pSql, fSql);
        }
    }

    private void syncLoginLogs(AbstractSqlDataSource primary, AbstractSqlDataSource fallback) {
        if (primary == null || fallback == null || primary.dataSource == null || fallback.dataSource == null) return;
        tw.yuaner.neoauth.config.LoginLogsConfig logCfg = tw.yuaner.neoauth.config.ConfigManager.getInstance().getLoginLogsConfig();
        if (logCfg == null || !logCfg.isEnabled()) return;

        String table = logCfg.getTableName();
        String colId = logCfg.getColumnId();
        String colServerName = logCfg.getColumnServerName();
        String colName = logCfg.getColumnName();
        String colLoginTime = logCfg.getColumnLoginTime();
        String colLogoutTime = logCfg.getColumnLogoutTime();
        String colIp = logCfg.getColumnIp();
        String colLoginMethod = logCfg.getColumnLoginMethod();
        String colConnChannel = logCfg.getColumnConnectionChannel();
        String colUuid = logCfg.getColumnUuid();
        String colServerIp = logCfg.getColumnServerIp();
        String colServerPort = logCfg.getColumnServerPort();
        String colLoginOp = logCfg.getColumnLoginOpLevel();
        String colLogoutOp = logCfg.getColumnLogoutOpLevel();

        String selectSql = "SELECT " + colId + ", " + colServerName + ", " + colName + ", " + colLoginTime + ", "
                + colLogoutTime + ", " + colIp + ", " + colLoginMethod + ", " + colConnChannel + ", " + colUuid + ", "
                + colServerIp + ", " + colServerPort + ", " + colLoginOp + ", " + colLogoutOp + " FROM " + table;

        boolean isPrimarySqlite = primary instanceof SqliteDataSource;
        String insertSql;
        if (isPrimarySqlite) {
            insertSql = "INSERT OR REPLACE INTO " + table + " ("
                    + colId + ", " + colServerName + ", " + colName + ", " + colLoginTime + ", "
                    + colLogoutTime + ", " + colIp + ", " + colLoginMethod + ", " + colConnChannel + ", " + colUuid + ", "
                    + colServerIp + ", " + colServerPort + ", " + colLoginOp + ", " + colLogoutOp + ") "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            insertSql = "INSERT INTO " + table + " ("
                    + colId + ", " + colServerName + ", " + colName + ", " + colLoginTime + ", "
                    + colLogoutTime + ", " + colIp + ", " + colLoginMethod + ", " + colConnChannel + ", " + colUuid + ", "
                    + colServerIp + ", " + colServerPort + ", " + colLoginOp + ", " + colLogoutOp + ") "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE " + colLogoutTime + " = VALUES(" + colLogoutTime + "), "
                    + colLogoutOp + " = VALUES(" + colLogoutOp + ")";
        }

        try (Connection fConn = fallback.dataSource.getConnection();
             PreparedStatement fStmt = fConn.prepareStatement(selectSql);
             ResultSet rs = fStmt.executeQuery()) {

            List<Long> syncedIds = new ArrayList<>();
            try (Connection pConn = primary.dataSource.getConnection();
                 PreparedStatement pStmt = pConn.prepareStatement(insertSql)) {

                while (rs.next()) {
                    long id = rs.getLong(1);
                    pStmt.setLong(1, id);
                    pStmt.setString(2, rs.getString(2));
                    pStmt.setString(3, rs.getString(3));
                    pStmt.setLong(4, rs.getLong(4));
                    long logoutTime = rs.getLong(5);
                    if (rs.wasNull()) {
                        pStmt.setNull(5, java.sql.Types.BIGINT);
                    } else {
                        pStmt.setLong(5, logoutTime);
                    }
                    pStmt.setString(6, rs.getString(6));
                    pStmt.setString(7, rs.getString(7));
                    pStmt.setString(8, rs.getString(8));
                    pStmt.setString(9, rs.getString(9));
                    pStmt.setString(10, rs.getString(10));
                    pStmt.setInt(11, rs.getInt(11));
                    pStmt.setInt(12, rs.getInt(12));
                    int logoutOp = rs.getInt(13);
                    if (rs.wasNull()) {
                        pStmt.setNull(13, java.sql.Types.INTEGER);
                    } else {
                        pStmt.setInt(13, logoutOp);
                    }

                    pStmt.addBatch();
                    syncedIds.add(id);
                }

                if (!syncedIds.isEmpty()) {
                    pStmt.executeBatch();
                    LOGGER.info("NeoAuth: 成功將本地 SQLite 備援庫中 {} 筆登入日誌回補同步至主資料庫！", syncedIds.size());

                    // 從 SQLite 刪除已同步回主庫的紀錄
                    String deleteSql = "DELETE FROM " + table + " WHERE " + colId + " = ?";
                    try (PreparedStatement delStmt = fConn.prepareStatement(deleteSql)) {
                        for (Long syncedId : syncedIds) {
                            delStmt.setLong(1, syncedId);
                            delStmt.addBatch();
                        }
                        delStmt.executeBatch();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("NeoAuth: 回補登入日誌至主資料庫時發生異常 (稍後將自動重試)", e);
        }
    }
}
