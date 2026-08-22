package tw.yuaner.neoauth;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static HikariDataSource dataSource;

    public static void init() {
        String host = Config.SERVER.dbHost.get();
        String port = Config.SERVER.dbPort.get();
        String dbName = Config.SERVER.dbName.get();
        String username = Config.SERVER.dbUsername.get();
        String password = Config.SERVER.dbPassword.get();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + dbName);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(config);
            createTableIfNotExists();
            LOGGER.info("NeoAuth: Successfully connected to database!");
        } catch (Exception e) {
            LOGGER.error("NeoAuth: Could not connect to MariaDB database! Please verify your settings in config/neoauth-server.toml. Error: {}", e.getMessage());
            dataSource = null;
        }
    }

    private static void createTableIfNotExists() {
        String table = Config.SERVER.dbTable.get();
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                "id INTEGER AUTO_INCREMENT PRIMARY KEY," +
                "username VARCHAR(255) NOT NULL UNIQUE," +
                "password VARCHAR(255) NOT NULL," +
                "totp VARCHAR(16)," +
                "ip VARCHAR(40)," +
                "lastlogin BIGINT," +
                "regdate BIGINT NOT NULL," +
                "regip VARCHAR(40)," +
                "x DOUBLE NOT NULL DEFAULT '0.0'," +
                "y DOUBLE NOT NULL DEFAULT '0.0'," +
                "z DOUBLE NOT NULL DEFAULT '0.0'," +
                "world VARCHAR(255) NOT NULL DEFAULT 'world'," +
                "yaw FLOAT," +
                "pitch FLOAT," +
                "email VARCHAR(255)," +
                "isLogged INT DEFAULT '0'," +
                "realname VARCHAR(255) NOT NULL DEFAULT 'Player'," +
                "salt varchar(255)," +
                "hasSession INT NOT NULL DEFAULT '0'," +
                "premiumUUID VARCHAR(36)," +
                "playerUUID VARCHAR(36)" +
                ");";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            LOGGER.error("Failed to create AuthMe table", e);
        }
    }

    public static boolean isRegistered(String username) {
        if (dataSource == null) return false;
        String sql = "SELECT id FROM " + Config.SERVER.dbTable.get() + " WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Database error checking registration", e);
            return false;
        }
    }

    public static boolean checkPassword(String username, String password) {
        if (dataSource == null) return false;
        String sql = "SELECT password FROM " + Config.SERVER.dbTable.get() + " WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password");
                    return PasswordManager.checkPassword(password, hash);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Database error checking password", e);
        }
        return false;
    }

    public static boolean registerPlayer(String username, String password, String ip) {
        if (dataSource == null) return false;
        if (isRegistered(username)) return false;

        String hash = PasswordManager.hashPassword(password);
        long now = System.currentTimeMillis();
        
        String sql = "INSERT INTO " + Config.SERVER.dbTable.get() + " (username, realname, password, ip, regip, regdate, lastlogin) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.toLowerCase());
            stmt.setString(2, username);
            stmt.setString(3, hash);
            stmt.setString(4, ip);
            stmt.setString(5, ip);
            stmt.setLong(6, now);
            stmt.setLong(7, now);
            
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Database error registering player", e);
            return false;
        }
    }
    
    public static void updateLogin(String username, String ip) {
        if (dataSource == null) return;
        String sql = "UPDATE " + Config.SERVER.dbTable.get() + " SET lastlogin = ?, ip = ?, isLogged = 1 WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, ip);
            stmt.setString(3, username.toLowerCase());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Database error updating login", e);
        }
    }
    
    public static void close() {
        if (dataSource == null) return;
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
