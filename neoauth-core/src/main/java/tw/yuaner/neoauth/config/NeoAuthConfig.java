package tw.yuaner.neoauth.config;

import java.util.Map;

/**
 * NeoAuth 主設定檔實作類別 (對應 config/neoauth/config.yml)。
 */
public class NeoAuthConfig implements IAuthConfig {

    private String dbBackend = "MARIADB";
    private String dbHost = "127.0.0.1";
    private String dbPort = "3306";
    private String dbName = "neoauth";
    private String dbUsername = "root";
    private String dbPassword = "";
    private String dbTable = "neoauth";
    private int dbPoolSize = 10;
    private int dbMaxLifetime = 1800;

    private String messagesLanguage = "zhtw";
    private boolean allowOfflinePlayers = true;

    private String passwordHash = "SHA256";
    private int minPasswordLength = 0;
    private int maxPasswordLength = 0;

    private int timeout = 90;
    private boolean kickOnWrongPassword = false;
    private int maxLoginTries = 3;
    private boolean blindness = true;
    private boolean slowness = true;
    private boolean displayWelcomeMessage = true;

    private boolean teleportUnAuthedToSpawn = false;
    private boolean saveQuitLocation = true;

    public NeoAuthConfig() {}

    @SuppressWarnings("unchecked")
    public static NeoAuthConfig fromMap(Map<String, Object> map) {
        NeoAuthConfig config = new NeoAuthConfig();
        if (map == null) return config;

        // DataSource
        Object dsObj = map.get("DataSource");
        if (dsObj instanceof Map<?, ?> dsMap) {
            if (dsMap.get("backend") != null) config.dbBackend = String.valueOf(dsMap.get("backend"));
            if (dsMap.get("mySQLHost") != null) config.dbHost = String.valueOf(dsMap.get("mySQLHost"));
            if (dsMap.get("mySQLPort") != null) config.dbPort = String.valueOf(dsMap.get("mySQLPort"));
            if (dsMap.get("mySQLDatabase") != null) config.dbName = String.valueOf(dsMap.get("mySQLDatabase"));
            if (dsMap.get("mySQLUsername") != null) config.dbUsername = String.valueOf(dsMap.get("mySQLUsername"));
            if (dsMap.get("mySQLPassword") != null) config.dbPassword = String.valueOf(dsMap.get("mySQLPassword"));
            if (dsMap.get("mySQLTablename") != null) config.dbTable = String.valueOf(dsMap.get("mySQLTablename"));
            if (dsMap.get("poolSize") instanceof Number n) config.dbPoolSize = n.intValue();
            if (dsMap.get("maxLifetime") instanceof Number n) config.dbMaxLifetime = n.intValue();
        }

        // settings
        Object settingsObj = map.get("settings");
        if (settingsObj instanceof Map<?, ?> setMap) {
            if (setMap.get("messagesLanguage") != null) config.messagesLanguage = String.valueOf(setMap.get("messagesLanguage"));
            if (setMap.get("allowOfflinePlayers") instanceof Boolean b) config.allowOfflinePlayers = b;

            // security
            Object secObj = setMap.get("security");
            if (secObj instanceof Map<?, ?> secMap) {
                if (secMap.get("passwordHash") != null) config.passwordHash = String.valueOf(secMap.get("passwordHash"));
                if (secMap.get("minPasswordLength") instanceof Number n) config.minPasswordLength = n.intValue();
                if (secMap.get("maxPasswordLength") instanceof Number n) config.maxPasswordLength = n.intValue();
            }

            // restrictions
            Object restObj = setMap.get("restrictions");
            if (restObj instanceof Map<?, ?> restMap) {
                if (restMap.get("timeout") instanceof Number n) config.timeout = n.intValue();
                if (restMap.get("kickOnWrongPassword") instanceof Boolean b) config.kickOnWrongPassword = b;
                if (restMap.get("maxLoginTries") instanceof Number n) config.maxLoginTries = n.intValue();
                if (restMap.get("blindness") instanceof Boolean b) config.blindness = b;
                if (restMap.get("slowness") instanceof Boolean b) config.slowness = b;
                if (restMap.get("displayWelcomeMessage") instanceof Boolean b) config.displayWelcomeMessage = b;
            }

            // spawn
            Object spawnObj = setMap.get("spawn");
            if (spawnObj instanceof Map<?, ?> spawnMap) {
                if (spawnMap.get("teleportUnAuthedToSpawn") instanceof Boolean b) config.teleportUnAuthedToSpawn = b;
                if (spawnMap.get("saveQuitLocation") instanceof Boolean b) config.saveQuitLocation = b;
            }
        }

        return config;
    }

    @Override
    public String getDbBackend() {
        return dbBackend;
    }

    @Override
    public String getDbHost() {
        return dbHost;
    }

    @Override
    public String getDbPort() {
        return dbPort;
    }

    @Override
    public String getDbName() {
        return dbName;
    }

    @Override
    public String getDbUsername() {
        return dbUsername;
    }

    @Override
    public String getDbPassword() {
        return dbPassword;
    }

    @Override
    public String getDbTable() {
        return dbTable;
    }

    @Override
    public int getDbPoolSize() {
        return dbPoolSize;
    }

    @Override
    public int getDbMaxLifetime() {
        return dbMaxLifetime;
    }

    @Override
    public boolean isAllowOfflinePlayers() {
        return allowOfflinePlayers;
    }

    @Override
    public String getMessagesLanguage() {
        return messagesLanguage;
    }

    @Override
    public String getPasswordHash() {
        return passwordHash;
    }

    @Override
    public int getMinPasswordLength() {
        return minPasswordLength;
    }

    @Override
    public int getMaxPasswordLength() {
        return maxPasswordLength;
    }

    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public boolean isKickOnWrongPassword() {
        return kickOnWrongPassword;
    }

    @Override
    public int getMaxLoginTries() {
        return maxLoginTries;
    }

    @Override
    public boolean isBlindnessEnabled() {
        return blindness;
    }

    @Override
    public boolean isSlownessEnabled() {
        return slowness;
    }

    @Override
    public boolean isDisplayWelcomeMessage() {
        return displayWelcomeMessage;
    }

    @Override
    public boolean isTeleportUnAuthedToSpawn() {
        return teleportUnAuthedToSpawn;
    }

    @Override
    public boolean isSaveQuitLocation() {
        return saveQuitLocation;
    }
}
