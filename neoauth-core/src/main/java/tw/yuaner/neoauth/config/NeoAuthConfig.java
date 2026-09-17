package tw.yuaner.neoauth.config;

import java.util.Map;

/**
 * NeoAuth 主設定檔實作類別 (對應 config/neoauth/config.yml)。
 */
public class NeoAuthConfig implements IAuthConfig {

    private String dbBackend = "SQLITE";
    private String sqLiteFile = "config/neoauth/neoauth.db";
    private String dbHost = "127.0.0.1";
    private String dbPort = "3306";
    private String dbName = "neoauth";
    private String dbUsername = "root";
    private String dbPassword = "";
    private String dbTable = "neoauth";
    private int dbPoolSize = 10;
    private int dbMaxLifetime = 1800;
    private boolean fallbackToSqLite = false;

    // SSL 與連線進階設定
    private boolean mySQLUseSSL = false;
    private boolean mySQLCheckServerCertificate = true;
    private boolean mySQLAllowPublicKeyRetrieval = true;
    private String mySQLServerSslCert = "";
    private String mySQLClientSslCert = "";
    private String mySQLClientSslKey = "";

    // 自訂資料庫欄位名稱 (AuthMeReloaded 相容)
    private String mySQLColumnId = "id";
    private String mySQLColumnName = "username";
    private String mySQLRealName = "realname";
    private String mySQLColumnPassword = "password";
    private String mySQLColumnSalt = "";
    private String mySQLColumnEmail = "email";
    private String mySQLColumnLogged = "isLogged";
    private String mySQLColumnHasSession = "hasSession";
    private String mySQLTotpKey = "totp";
    private String mySQLColumnIp = "ip";
    private String mySQLColumnLastLogin = "lastlogin";
    private String mySQLColumnRegisterDate = "regdate";
    private String mySQLColumnRegisterIp = "regip";
    private String mySQLLastLocX = "x";
    private String mySQLLastLocY = "y";
    private String mySQLLastLocZ = "z";
    private String mySQLLastLocWorld = "world";
    private String mySQLLastLocYaw = "yaw";
    private String mySQLLastLocPitch = "pitch";
    private String mySQLPlayerUUID = "playerUUID";

    private String messagesLanguage = "zhtw";
    private boolean allowOfflinePlayers = true;
    private boolean dynamicPremiumVerification = true;
    private int dynamicVerificationTimeout = 7;
    private boolean circuitBreakerEnabled = true;
    private int circuitBreakerDurationSeconds = 180;
    private String customYggdrasilUrl = "";

    private String passwordHash = "SHA256";
    private int minPasswordLength = 0;
    private int maxPasswordLength = 0;
    private int doubleMD5SaltLength = 6;

    private int timeout = 90;
    private boolean kickOnWrongPassword = false;
    private int maxLoginTries = 3;
    private boolean blindness = true;
    private boolean slowness = true;
    private boolean displayWelcomeMessage = true;

    // settings.registration (AuthMeReloaded 相容結構)
    private boolean registrationEnabled = true;
    private int registrationMessageInterval = 5;
    private boolean registrationForced = true;
    private boolean forceKickAfterRegister = false;
    private boolean forceLoginAfterRegister = false;

    // bluemap (BlueMap 網頁地圖整合)
    private boolean blueMapEnabled = true;
    private boolean useSkinRestorerConfig = true;
    private String blueMapPriority = "OFFICIAL_FIRST";
    private String blueMapAvatarUrl = "https://mc-heads.net/avatar/{username}/64";
    private String blueMapCustomAvatarUrl = "";
    private int blueMapCacheTtlMinutes = 120;

    // tab (TAB by NEZNAMY 模組連動)
    private boolean tabEnabled = true;
    private String tabDateFormat = "yyyy-MM-dd HH:mm:ss";

    public NeoAuthConfig() {}

    @SuppressWarnings("unchecked")
    public static NeoAuthConfig fromMap(Map<String, Object> map) {
        NeoAuthConfig config = new NeoAuthConfig();
        if (map == null) return config;

        // DataSource
        Object dsObj = map.get("DataSource");
        if (dsObj instanceof Map<?, ?> dsMap) {
            if (dsMap.get("backend") != null) config.dbBackend = String.valueOf(dsMap.get("backend"));
            if (dsMap.get("sqLiteFile") != null) config.sqLiteFile = String.valueOf(dsMap.get("sqLiteFile"));
            else if (dsMap.get("sqliteFile") != null) config.sqLiteFile = String.valueOf(dsMap.get("sqliteFile"));
            if (dsMap.get("mySQLHost") != null) config.dbHost = String.valueOf(dsMap.get("mySQLHost"));
            if (dsMap.get("mySQLPort") != null) config.dbPort = String.valueOf(dsMap.get("mySQLPort"));
            if (dsMap.get("mySQLDatabase") != null) config.dbName = String.valueOf(dsMap.get("mySQLDatabase"));
            if (dsMap.get("mySQLUsername") != null) config.dbUsername = String.valueOf(dsMap.get("mySQLUsername"));
            if (dsMap.get("mySQLPassword") != null) config.dbPassword = String.valueOf(dsMap.get("mySQLPassword"));
            if (dsMap.get("mySQLTablename") != null) config.dbTable = String.valueOf(dsMap.get("mySQLTablename"));
            if (dsMap.get("poolSize") instanceof Number n) config.dbPoolSize = n.intValue();
            if (dsMap.get("maxLifetime") instanceof Number n) config.dbMaxLifetime = n.intValue();
            if (dsMap.get("fallbackToSqLite") instanceof Boolean b) config.fallbackToSqLite = b;

            // SSL & 連線設定
            if (dsMap.get("mySQLUseSSL") instanceof Boolean b) config.mySQLUseSSL = b;
            if (dsMap.get("mySQLCheckServerCertificate") instanceof Boolean b) config.mySQLCheckServerCertificate = b;
            if (dsMap.get("mySQLAllowPublicKeyRetrieval") instanceof Boolean b) config.mySQLAllowPublicKeyRetrieval = b;
            if (dsMap.get("mySQLServerSslCert") != null) config.mySQLServerSslCert = String.valueOf(dsMap.get("mySQLServerSslCert"));
            else if (dsMap.get("serverSslCert") != null) config.mySQLServerSslCert = String.valueOf(dsMap.get("serverSslCert"));
            if (dsMap.get("mySQLClientSslCert") != null) config.mySQLClientSslCert = String.valueOf(dsMap.get("mySQLClientSslCert"));
            else if (dsMap.get("clientSslCert") != null) config.mySQLClientSslCert = String.valueOf(dsMap.get("clientSslCert"));
            if (dsMap.get("mySQLClientSslKey") != null) config.mySQLClientSslKey = String.valueOf(dsMap.get("mySQLClientSslKey"));
            else if (dsMap.get("clientSslKey") != null) config.mySQLClientSslKey = String.valueOf(dsMap.get("clientSslKey"));

            // 欄位名稱
            if (dsMap.get("mySQLColumnId") != null) config.mySQLColumnId = String.valueOf(dsMap.get("mySQLColumnId"));
            if (dsMap.get("mySQLColumnName") != null) config.mySQLColumnName = String.valueOf(dsMap.get("mySQLColumnName"));
            if (dsMap.get("mySQLRealName") != null) config.mySQLRealName = String.valueOf(dsMap.get("mySQLRealName"));
            if (dsMap.get("mySQLColumnPassword") != null) config.mySQLColumnPassword = String.valueOf(dsMap.get("mySQLColumnPassword"));
            if (dsMap.get("mySQLColumnSalt") != null) config.mySQLColumnSalt = String.valueOf(dsMap.get("mySQLColumnSalt"));
            if (dsMap.get("mySQLColumnEmail") != null) config.mySQLColumnEmail = String.valueOf(dsMap.get("mySQLColumnEmail"));
            if (dsMap.get("mySQLColumnLogged") != null) config.mySQLColumnLogged = String.valueOf(dsMap.get("mySQLColumnLogged"));
            if (dsMap.get("mySQLColumnHasSession") != null) config.mySQLColumnHasSession = String.valueOf(dsMap.get("mySQLColumnHasSession"));
            if (dsMap.get("mySQLtotpKey") != null) config.mySQLTotpKey = String.valueOf(dsMap.get("mySQLtotpKey"));
            if (dsMap.get("mySQLColumnIp") != null) config.mySQLColumnIp = String.valueOf(dsMap.get("mySQLColumnIp"));
            if (dsMap.get("mySQLColumnLastLogin") != null) config.mySQLColumnLastLogin = String.valueOf(dsMap.get("mySQLColumnLastLogin"));
            if (dsMap.get("mySQLColumnRegisterDate") != null) config.mySQLColumnRegisterDate = String.valueOf(dsMap.get("mySQLColumnRegisterDate"));
            if (dsMap.get("mySQLColumnRegisterIp") != null) config.mySQLColumnRegisterIp = String.valueOf(dsMap.get("mySQLColumnRegisterIp"));
            if (dsMap.get("mySQLlastlocX") != null) config.mySQLLastLocX = String.valueOf(dsMap.get("mySQLlastlocX"));
            if (dsMap.get("mySQLlastlocY") != null) config.mySQLLastLocY = String.valueOf(dsMap.get("mySQLlastlocY"));
            if (dsMap.get("mySQLlastlocZ") != null) config.mySQLLastLocZ = String.valueOf(dsMap.get("mySQLlastlocZ"));
            if (dsMap.get("mySQLlastlocWorld") != null) config.mySQLLastLocWorld = String.valueOf(dsMap.get("mySQLlastlocWorld"));
            if (dsMap.get("mySQLlastlocYaw") != null) config.mySQLLastLocYaw = String.valueOf(dsMap.get("mySQLlastlocYaw"));
            if (dsMap.get("mySQLlastlocPitch") != null) config.mySQLLastLocPitch = String.valueOf(dsMap.get("mySQLlastlocPitch"));
            if (dsMap.get("mySQLPlayerUUID") != null) config.mySQLPlayerUUID = String.valueOf(dsMap.get("mySQLPlayerUUID"));
        }

        // settings
        Object settingsObj = map.get("settings");
        if (settingsObj instanceof Map<?, ?> setMap) {
            if (setMap.get("messagesLanguage") != null) config.messagesLanguage = String.valueOf(setMap.get("messagesLanguage"));
            if (setMap.get("allowOfflinePlayers") instanceof Boolean b) config.allowOfflinePlayers = b;
            if (setMap.get("dynamicPremiumVerification") instanceof Boolean b) config.dynamicPremiumVerification = b;
            if (setMap.get("dynamicVerificationTimeout") instanceof Number n) config.dynamicVerificationTimeout = n.intValue();
            if (setMap.get("circuitBreakerEnabled") instanceof Boolean b) config.circuitBreakerEnabled = b;
            if (setMap.get("circuitBreakerDurationSeconds") instanceof Number n) config.circuitBreakerDurationSeconds = n.intValue();
            if (setMap.get("customYggdrasilUrl") != null) config.customYggdrasilUrl = String.valueOf(setMap.get("customYggdrasilUrl"));
            // registration (AuthMeReloaded 相容結構)
            Object regObj = setMap.get("registration");
            if (regObj instanceof Map<?, ?> regMap) {
                if (regMap.get("enabled") instanceof Boolean b) config.registrationEnabled = b;
                else if (regMap.get("enable") instanceof Boolean b) config.registrationEnabled = b;

                if (regMap.get("messageInterval") instanceof Number n) config.registrationMessageInterval = n.intValue();
                if (regMap.get("force") instanceof Boolean b) config.registrationForced = b;
                if (regMap.get("forceKickAfterRegister") instanceof Boolean b) config.forceKickAfterRegister = b;
                if (regMap.get("forceLoginAfterRegister") instanceof Boolean b) config.forceLoginAfterRegister = b;
            }

            // security
            Object secObj = setMap.get("security");
            if (secObj instanceof Map<?, ?> secMap) {
                if (secMap.get("passwordHash") != null) config.passwordHash = String.valueOf(secMap.get("passwordHash"));
                if (secMap.get("minPasswordLength") instanceof Number n) config.minPasswordLength = n.intValue();
                if (secMap.get("maxPasswordLength") instanceof Number n) config.maxPasswordLength = n.intValue();
                if (secMap.get("doubleMD5SaltLength") instanceof Number n) config.doubleMD5SaltLength = n.intValue();
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
        }

        // ExternalBoardOptions (支援 Discuz, Phpwind, Blessing Skin 等外部論壇與皮膚站設定)
        Object extObj = map.get("ExternalBoardOptions");
        if (extObj instanceof Map<?, ?> extMap) {
            if (extMap.get("mySQLColumnSalt") != null && config.mySQLColumnSalt.isBlank()) {
                config.mySQLColumnSalt = String.valueOf(extMap.get("mySQLColumnSalt"));
            }
        }

        // bluemap (支援頂層 bluemap 與 settings.bluemap)
        Object blueMapObj = map.get("bluemap");
        if (blueMapObj == null && settingsObj instanceof Map<?, ?> sMap) {
            blueMapObj = sMap.get("bluemap");
        }
        if (blueMapObj instanceof Map<?, ?> bmMap) {
            if (bmMap.get("enabled") instanceof Boolean b) config.blueMapEnabled = b;
            else if (bmMap.get("enable") instanceof Boolean b) config.blueMapEnabled = b;
            else if (bmMap.get("enable-bluemap-integration") instanceof Boolean b) config.blueMapEnabled = b;
            else if (bmMap.get("enableBluemapIntegration") instanceof Boolean b) config.blueMapEnabled = b;

            if (bmMap.get("useSkinRestorerConfig") instanceof Boolean b) config.useSkinRestorerConfig = b;
            else if (bmMap.get("use-skinrestorer-config") instanceof Boolean b) config.useSkinRestorerConfig = b;
            else if (bmMap.get("useSkinRestorer") instanceof Boolean b) config.useSkinRestorerConfig = b;
            else if (bmMap.get("use-skinrestorer") instanceof Boolean b) config.useSkinRestorerConfig = b;

            if (bmMap.get("priority") != null) config.blueMapPriority = String.valueOf(bmMap.get("priority"));
            else if (bmMap.get("sourcePriority") != null) config.blueMapPriority = String.valueOf(bmMap.get("sourcePriority"));
            else if (bmMap.get("source-priority") != null) config.blueMapPriority = String.valueOf(bmMap.get("source-priority"));

            if (bmMap.get("avatarUrl") != null) config.blueMapAvatarUrl = String.valueOf(bmMap.get("avatarUrl"));
            else if (bmMap.get("avatar-url") != null) config.blueMapAvatarUrl = String.valueOf(bmMap.get("avatar-url"));
            else if (bmMap.get("avatar_url") != null) config.blueMapAvatarUrl = String.valueOf(bmMap.get("avatar_url"));

            if (bmMap.get("customAvatarUrl") != null) config.blueMapCustomAvatarUrl = String.valueOf(bmMap.get("customAvatarUrl"));
            else if (bmMap.get("custom-avatar-url") != null) config.blueMapCustomAvatarUrl = String.valueOf(bmMap.get("custom-avatar-url"));
            else if (bmMap.get("custom_avatar_url") != null) config.blueMapCustomAvatarUrl = String.valueOf(bmMap.get("custom_avatar_url"));

            if (bmMap.get("cacheTtlMinutes") instanceof Number n) config.blueMapCacheTtlMinutes = n.intValue();
            else if (bmMap.get("cache-ttl-minutes") instanceof Number n) config.blueMapCacheTtlMinutes = n.intValue();
            else if (bmMap.get("cache_ttl_minutes") instanceof Number n) config.blueMapCacheTtlMinutes = n.intValue();
        }

        Object tabObj = map.get("tab");
        if (tabObj instanceof Map<?, ?> tabMap) {
            if (tabMap.get("enabled") instanceof Boolean b) config.tabEnabled = b;
            else if (tabMap.get("enable") instanceof Boolean b) config.tabEnabled = b;

            if (tabMap.get("dateFormat") != null) config.tabDateFormat = String.valueOf(tabMap.get("dateFormat"));
            else if (tabMap.get("date-format") != null) config.tabDateFormat = String.valueOf(tabMap.get("date-format"));
            else if (tabMap.get("date_format") != null) config.tabDateFormat = String.valueOf(tabMap.get("date_format"));
        }

        return config;
    }

    @Override
    public String getDbBackend() {
        return dbBackend;
    }

    @Override
    public String getSqLiteFile() {
        return sqLiteFile;
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
    public boolean isMySqlUseSSL() {
        return mySQLUseSSL;
    }

    @Override
    public boolean isMySqlCheckServerCertificate() {
        return mySQLCheckServerCertificate;
    }

    @Override
    public boolean isFallbackToSqLite() {
        return fallbackToSqLite;
    }

    @Override
    public boolean isMySqlAllowPublicKeyRetrieval() {
        return mySQLAllowPublicKeyRetrieval;
    }

    @Override
    public String getMySqlServerSslCert() {
        return mySQLServerSslCert;
    }

    @Override
    public String getMySqlClientSslCert() {
        return mySQLClientSslCert;
    }

    @Override
    public String getMySqlClientSslKey() {
        return mySQLClientSslKey;
    }

    @Override
    public String getMySqlColumnId() {
        return mySQLColumnId;
    }

    @Override
    public String getMySqlColumnName() {
        return mySQLColumnName;
    }

    @Override
    public String getMySqlRealName() {
        return mySQLRealName;
    }

    @Override
    public String getMySqlColumnPassword() {
        return mySQLColumnPassword;
    }

    @Override
    public String getMySqlColumnSalt() {
        return mySQLColumnSalt;
    }

    @Override
    public String getMySqlColumnEmail() {
        return mySQLColumnEmail;
    }

    @Override
    public String getMySqlColumnLogged() {
        return mySQLColumnLogged;
    }

    @Override
    public String getMySqlColumnHasSession() {
        return mySQLColumnHasSession;
    }

    @Override
    public String getMySqlTotpKey() {
        return mySQLTotpKey;
    }

    @Override
    public String getMySqlColumnIp() {
        return mySQLColumnIp;
    }

    @Override
    public String getMySqlColumnLastLogin() {
        return mySQLColumnLastLogin;
    }

    @Override
    public String getMySqlColumnRegisterDate() {
        return mySQLColumnRegisterDate;
    }

    @Override
    public String getMySqlColumnRegisterIp() {
        return mySQLColumnRegisterIp;
    }

    @Override
    public String getMySqlLastLocX() {
        return mySQLLastLocX;
    }

    @Override
    public String getMySqlLastLocY() {
        return mySQLLastLocY;
    }

    @Override
    public String getMySqlLastLocZ() {
        return mySQLLastLocZ;
    }

    @Override
    public String getMySqlLastLocWorld() {
        return mySQLLastLocWorld;
    }

    @Override
    public String getMySqlLastLocYaw() {
        return mySQLLastLocYaw;
    }

    @Override
    public String getMySqlLastLocPitch() {
        return mySQLLastLocPitch;
    }

    @Override
    public String getMySqlPlayerUUID() {
        return mySQLPlayerUUID;
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
    public boolean isDynamicPremiumVerification() {
        return dynamicPremiumVerification;
    }

    @Override
    public int getDynamicVerificationTimeout() {
        return dynamicVerificationTimeout;
    }

    @Override
    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    @Override
    public int getCircuitBreakerDurationSeconds() {
        return circuitBreakerDurationSeconds;
    }

    @Override
    public String getCustomYggdrasilUrl() {
        return customYggdrasilUrl;
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
    public int getDoubleMD5SaltLength() {
        return doubleMD5SaltLength;
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

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    @Override
    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    @Override
    public int getRegistrationMessageInterval() {
        return registrationMessageInterval;
    }

    @Override
    public boolean isRegistrationForced() {
        return registrationForced;
    }

    @Override
    public boolean isForceKickAfterRegister() {
        return forceKickAfterRegister;
    }

    @Override
    public boolean isForceLoginAfterRegister() {
        return forceLoginAfterRegister;
    }

    @Override
    public boolean isBlueMapIntegrationEnabled() {
        return blueMapEnabled;
    }

    @Override
    public String getBlueMapAvatarUrl() {
        return blueMapAvatarUrl;
    }

    @Override
    public String getBlueMapCustomAvatarUrl() {
        return blueMapCustomAvatarUrl;
    }

    @Override
    public boolean isUseSkinRestorerConfig() {
        return useSkinRestorerConfig;
    }

    @Override
    public String getBlueMapPriority() {
        return blueMapPriority != null && !blueMapPriority.isBlank() ? blueMapPriority.trim().toUpperCase() : "OFFICIAL_FIRST";
    }

    @Override
    public int getBlueMapCacheTtlMinutes() {
        return blueMapCacheTtlMinutes >= 0 ? blueMapCacheTtlMinutes : 120;
    }

    @Override
    public boolean isTabIntegrationEnabled() {
        return tabEnabled;
    }

    @Override
    public String getTabDateFormat() {
        return tabDateFormat != null && !tabDateFormat.isBlank() ? tabDateFormat.trim() : "yyyy-MM-dd HH:mm:ss";
    }
}
