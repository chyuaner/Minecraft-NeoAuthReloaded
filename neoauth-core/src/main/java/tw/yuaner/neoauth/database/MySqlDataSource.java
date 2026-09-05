package tw.yuaner.neoauth.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import tw.yuaner.neoauth.config.IAuthConfig;

/**
 * MariaDB / MySQL 資料庫來源實作。
 * <p>
 * 使用高效能 HikariCP 連線池連線至 MySQL / MariaDB 伺服器，
 * 並採用 {@code INTEGER AUTO_INCREMENT PRIMARY KEY} 語法建立 AuthMe 相容之資料表結構。
 */
public class MySqlDataSource extends AbstractSqlDataSource {

    @Override
    protected HikariDataSource createDataSource(IAuthConfig config) throws Exception {
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
        hikariConfig.setConnectionTimeout(5000);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        // SSL 與 RSA 公鑰檢索設定
        hikariConfig.addDataSourceProperty("useSSL", String.valueOf(config.isMySqlUseSSL()));
        hikariConfig.addDataSourceProperty("verifyServerCertificate", String.valueOf(config.isMySqlCheckServerCertificate()));
        hikariConfig.addDataSourceProperty("allowPublicKeyRetrieval", String.valueOf(config.isMySqlAllowPublicKeyRetrieval()));

        // 自訂 SSL 憑證與 mTLS 設定 (MariaDB Connector/J 原生支援)
        if (config.getMySqlServerSslCert() != null && !config.getMySqlServerSslCert().isBlank()) {
            hikariConfig.addDataSourceProperty("serverSslCert", config.getMySqlServerSslCert().trim());
        }
        if (config.getMySqlClientSslCert() != null && !config.getMySqlClientSslCert().isBlank()) {
            hikariConfig.addDataSourceProperty("clientSslCert", config.getMySqlClientSslCert().trim());
        }
        if (config.getMySqlClientSslKey() != null && !config.getMySqlClientSslKey().isBlank()) {
            hikariConfig.addDataSourceProperty("clientSslKey", config.getMySqlClientSslKey().trim());
        }

        HikariDataSource ds = new HikariDataSource(hikariConfig);
        LOGGER.info("NeoAuth: 成功建立 {} 資料庫連線池 [{}:{}/{}]！", backend, host, port, dbName);
        return ds;
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

        return sb.toString();
    }
}
