package tw.yuaner.neoauth.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.IAuthConfig;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SQLite 資料庫來源實作。
 * <p>
 * 支援相對路徑（含跨目錄 {@code ../}）與絕對路徑解析，自動建立父資料夾，
 * 並採用 AuthMe 相容之 {@code INTEGER PRIMARY KEY AUTOINCREMENT} 語法建立資料表，
 * 同時配置 WAL 模式 (Write-Ahead Logging) 與 Busy Timeout 確保多執行緒讀寫順暢不鎖表。
 */
public class SqliteDataSource extends AbstractSqlDataSource {

    private Path resolvedDbPath;

    @Override
    protected HikariDataSource createDataSource(IAuthConfig config) throws Exception {
        Class.forName("org.sqlite.JDBC");

        String rawPath = config.getSqLiteFile();
        if (rawPath == null || rawPath.isBlank()) {
            rawPath = "config/neoauth/neoauth.db";
        }

        Path path = Path.of(rawPath);
        if (!path.isAbsolute()) {
            path = Path.of("").toAbsolutePath().resolve(path).normalize();
        }
        this.resolvedDbPath = path;

        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        if (parent != null && Files.exists(parent) && !Files.isWritable(parent)) {
            LOGGER.error("NeoAuth: SQLite 目錄無寫入權限 [{}]: 請確認 Linux 目錄擁有者與權限 (例如執行 chown -R 或 chmod -R 775)！", parent);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + path.toString());
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(60000);
        hikariConfig.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; PRAGMA synchronous=NORMAL;");

        HikariDataSource ds = new HikariDataSource(hikariConfig);
        LOGGER.info("NeoAuth: 成功載入 SQLite 資料庫 [{}]！", path);
        return ds;
    }

    /**
     * 取得解析後的 SQLite 檔案絕對路徑。
     *
     * @return 資料庫檔案絕對路徑
     */
    public Path getResolvedDbPath() {
        return resolvedDbPath;
    }

    @Override
    protected String getCreateTableSql(IAuthConfig config) {
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
        sb.append(colId).append(" INTEGER PRIMARY KEY AUTOINCREMENT, ");
        sb.append(colName).append(" VARCHAR(255) NOT NULL UNIQUE, ");
        sb.append(colPassword).append(" VARCHAR(255) NOT NULL DEFAULT '', ");
        if (colTotp != null && !colTotp.isBlank()) {
            sb.append(colTotp).append(" VARCHAR(32), ");
        }
        sb.append(colIp).append(" VARCHAR(40), ");
        sb.append(colLastLogin).append(" BIGINT, ");
        sb.append(colRegDate).append(" BIGINT NOT NULL DEFAULT 0, ");
        sb.append(colRegIp).append(" VARCHAR(40), ");
        sb.append(colX).append(" DOUBLE NOT NULL DEFAULT 0.0, ");
        sb.append(colY).append(" DOUBLE NOT NULL DEFAULT 0.0, ");
        sb.append(colZ).append(" DOUBLE NOT NULL DEFAULT 0.0, ");
        sb.append(colWorld).append(" VARCHAR(255) NOT NULL DEFAULT 'world', ");
        sb.append(colYaw).append(" FLOAT, ");
        sb.append(colPitch).append(" FLOAT, ");
        sb.append(colEmail).append(" VARCHAR(255), ");
        sb.append(colLogged).append(" INT NOT NULL DEFAULT 0, ");
        sb.append(colRealName).append(" VARCHAR(255) NOT NULL DEFAULT 'Player', ");
        if (colSalt != null && !colSalt.isBlank()) {
            sb.append(colSalt).append(" VARCHAR(255), ");
        }
        sb.append(colHasSession).append(" INT NOT NULL DEFAULT 0, ");
        if (colPlayerUUID != null && !colPlayerUUID.isBlank()) {
            sb.append(colPlayerUUID).append(" VARCHAR(36)");
        } else {
            sb.append("playerUUID VARCHAR(36)");
        }
        sb.append(");");

        return sb.toString();
    }

    @Override
    protected String getCreateLoginLogsTableSql() {
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
        String colServerHost = logCfg != null ? logCfg.getColumnServerHost() : "server_host";
        String colServerIp = logCfg != null ? logCfg.getColumnServerIp() : "server_ip";
        String colServerPort = logCfg != null ? logCfg.getColumnServerPort() : "server_port";
        String colLoginOp = logCfg != null ? logCfg.getColumnLoginOpLevel() : "login_op_level";
        String colLogoutOp = logCfg != null ? logCfg.getColumnLogoutOpLevel() : "logout_op_level";

        return "CREATE TABLE IF NOT EXISTS " + table + " (" +
                colId + " BIGINT PRIMARY KEY, " +
                colServerName + " VARCHAR(50), " +
                colName + " VARCHAR(255) NOT NULL, " +
                colLoginTime + " BIGINT NOT NULL, " +
                colLogoutTime + " BIGINT, " +
                colIp + " VARCHAR(45), " +
                colLoginMethod + " VARCHAR(50), " +
                colConnChannel + " VARCHAR(50), " +
                colUuid + " VARCHAR(36), " +
                colServerHost + " VARCHAR(255), " +
                colServerIp + " VARCHAR(45), " +
                colServerPort + " INTEGER, " +
                colLoginOp + " INTEGER DEFAULT 0, " +
                colLogoutOp + " INTEGER DEFAULT NULL" +
                ");";
    }
}
