package tw.yuaner.neoauth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.NeoAuthConfig;
import tw.yuaner.neoauth.core.AuthLogic;
import tw.yuaner.neoauth.database.PlayerAuthData;
import tw.yuaner.neoauth.database.SqliteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SqliteDataSourceTest {

    @TempDir
    Path tempDir;

    private SqliteDataSource dataSource;
    private Path dbFile;

    @BeforeEach
    public void setUp() throws Exception {
        dbFile = tempDir.resolve("test_neoauth.db");
        Map<String, Object> dsMap = new HashMap<>();
        dsMap.put("backend", "SQLITE");
        dsMap.put("sqLiteFile", dbFile.toString());
        dsMap.put("mySQLTablename", "neoauth");

        Map<String, Object> rootMap = new HashMap<>();
        rootMap.put("DataSource", dsMap);

        NeoAuthConfig config = NeoAuthConfig.fromMap(rootMap);
        ConfigManager.getInstance().setConfig(config);
        ConfigManager.getInstance().setConfigDir(tempDir.resolve("config/neoauth"));

        dataSource = new SqliteDataSource();
        dataSource.connect(config);
        DatabaseManager.setActiveDataSource(dataSource);
    }

    @AfterEach
    public void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
        DatabaseManager.setActiveDataSource(null);
        ConfigManager.getInstance().setConfig(new NeoAuthConfig());
        ConfigManager.getInstance().setConfigDir(null);
    }

    @Test
    public void testSqliteConnectionAndRegistration() {
        assertTrue(dataSource.isConnected(), "資料庫應成功連線");

        // 初始狀態下玩家未註冊
        assertFalse(dataSource.isRegistered("Steve"));

        // 註冊玩家
        boolean regSuccess = dataSource.registerPlayer("Steve", "Pass123456", "127.0.0.1");
        assertTrue(regSuccess, "玩家 Steve 應註冊成功");

        // 再次註冊應失敗 (防止重複註冊)
        assertFalse(dataSource.registerPlayer("Steve", "AnotherPass", "127.0.0.1"));
        assertFalse(dataSource.registerPlayer("steve", "AnotherPass", "127.0.0.1"));

        // 註冊判定 (大小寫不敏感)
        assertTrue(dataSource.isRegistered("Steve"));
        assertTrue(dataSource.isRegistered("steve"));
        assertTrue(dataSource.isRegistered("STEVE"));

        // 密碼比對
        assertTrue(dataSource.checkPassword("Steve", "Pass123456"));
        assertTrue(dataSource.checkPassword("steve", "Pass123456"));
        assertFalse(dataSource.checkPassword("Steve", "WrongPass"));
    }

    @Test
    public void testChangePassword() {
        dataSource.registerPlayer("Alex", "OldPassword", "10.0.0.1");
        assertTrue(dataSource.checkPassword("Alex", "OldPassword"));

        boolean changeSuccess = dataSource.changePassword("Alex", "NewPassword123");
        assertTrue(changeSuccess, "密碼應修改成功");

        assertTrue(dataSource.checkPassword("Alex", "NewPassword123"));
        assertFalse(dataSource.checkPassword("Alex", "OldPassword"));
    }

    @Test
    public void testLoginUpdateAndPlayerData() {
        dataSource.registerPlayer("Steve", "Password", "127.0.0.1");

        PlayerAuthData initialData = dataSource.getPlayerData("Steve");
        assertNotNull(initialData);
        assertEquals("steve", initialData.getUsername());
        assertEquals("Steve", initialData.getRealName());
        assertEquals("127.0.0.1", initialData.getRegIp());

        // 更新登入狀態
        dataSource.updateLogin("Steve", "192.168.1.100");

        PlayerAuthData updatedData = dataSource.getPlayerData("Steve");
        assertNotNull(updatedData);
        assertEquals("192.168.1.100", updatedData.getIp());
        assertTrue(updatedData.getLastLogin() > 0);
    }

    @Test
    public void testEmailOperations() {
        dataSource.registerPlayer("Steve", "Password", "127.0.0.1");
        assertNull(dataSource.getEmail("Steve"));

        boolean emailSet = dataSource.setEmail("Steve", "steve@minecraft.net");
        assertTrue(emailSet);
        assertEquals("steve@minecraft.net", dataSource.getEmail("Steve"));
    }

    @Test
    public void testAccountsAndRecentPlayers() {
        dataSource.registerPlayer("PlayerOne", "Pass1", "192.168.1.10");
        dataSource.registerPlayer("PlayerTwo", "Pass2", "192.168.1.10");
        dataSource.registerPlayer("PlayerThree", "Pass3", "192.168.1.20");

        // 依據 IP 查詢
        List<String> ipAccounts = dataSource.getAccounts("192.168.1.10");
        assertEquals(2, ipAccounts.size());
        assertTrue(ipAccounts.contains("PlayerOne"));
        assertTrue(ipAccounts.contains("PlayerTwo"));

        // 依據玩家名稱查詢關聯帳號
        List<String> playerAccounts = dataSource.getAccounts("PlayerOne");
        assertEquals(2, playerAccounts.size());
        assertTrue(playerAccounts.contains("PlayerOne"));
        assertTrue(playerAccounts.contains("PlayerTwo"));

        // 查詢最近玩家
        List<PlayerAuthData> recent = dataSource.getRecentPlayers(10);
        assertEquals(3, recent.size());
    }

    @Test
    public void testAuthMeSchemaCompatibility() throws Exception {
        // 驗證生成的資料表結構與 AuthMe 完全相容
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toString())) {
            DatabaseMetaData md = conn.getMetaData();
            Set<String> columns = new HashSet<>();
            try (ResultSet rs = md.getColumns(null, null, "neoauth", null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }

            assertTrue(columns.contains("id"), "資料表應包含 id 欄位");
            assertTrue(columns.contains("username"), "資料表應包含 username 欄位");
            assertTrue(columns.contains("realname"), "資料表應包含 realname 欄位");
            assertTrue(columns.contains("password"), "資料表應包含 password 欄位");
            assertTrue(columns.contains("ip"), "資料表應包含 ip 欄位");
            assertTrue(columns.contains("lastlogin"), "資料表應包含 lastlogin 欄位");
            assertTrue(columns.contains("regdate"), "資料表應包含 regdate 欄位");
            assertTrue(columns.contains("regip"), "資料表應包含 regip 欄位");
            assertTrue(columns.contains("x"), "資料表應包含 x 欄位");
            assertTrue(columns.contains("y"), "資料表應包含 y 欄位");
            assertTrue(columns.contains("z"), "資料表應包含 z 欄位");
            assertTrue(columns.contains("world"), "資料表應包含 world 欄位");
            assertTrue(columns.contains("yaw"), "資料表應包含 yaw 欄位");
            assertTrue(columns.contains("pitch"), "資料表應包含 pitch 欄位");
            assertTrue(columns.contains("email"), "資料表應包含 email 欄位");
            assertTrue(columns.contains("islogged"), "資料表應包含 islogged 欄位");
            assertTrue(columns.contains("hassession"), "資料表應包含 hassession 欄位");
            assertTrue(columns.contains("playeruuid"), "資料表應包含 playeruuid 欄位");
        }
    }

    @Test
    public void testRelativePathCreation() throws Exception {
        Path subFolder = tempDir.resolve("nested").resolve("sub");
        Path relDb = subFolder.resolve("nested.db");

        Map<String, Object> dsMap = new HashMap<>();
        dsMap.put("backend", "SQLITE");
        dsMap.put("sqLiteFile", relDb.toString());

        Map<String, Object> rootMap = new HashMap<>();
        rootMap.put("DataSource", dsMap);

        NeoAuthConfig config = NeoAuthConfig.fromMap(rootMap);

        SqliteDataSource ds = new SqliteDataSource();
        ds.connect(config);

        assertTrue(ds.isConnected());
        assertTrue(Files.exists(relDb), "巢狀 SQLite 資料庫檔案應已自動建立");
        ds.close();
    }

    @Test
    public void testFirstTimePremiumPlayerRequiresRegistration() {
        UUID premiumUuid = UUID.randomUUID();
        String username = "PremiumPlayer";
        String ip = "203.0.113.10";

        // 1. 正版玩家第一次進服（尚未註冊）：應回傳 false 並保持未登入狀態，要求註冊
        boolean autoLoggedInFirstTime = AuthLogic.handlePlayerJoin(premiumUuid, username, ip, true);
        assertFalse(autoLoggedInFirstTime, "未註冊之正版玩家第一次進服不可直接自動登入");
        assertFalse(AuthManager.isLoggedIn(premiumUuid), "玩家狀態應為未登入");

        // 2. 玩家執行 /register 註冊帳號
        AuthLogic.RegisterResult regResult = AuthLogic.attemptRegister(premiumUuid, username, ip, "SecurePass123", "SecurePass123");
        assertEquals(AuthLogic.RegisterResult.SUCCESS, regResult, "註冊應成功");
        assertTrue(AuthManager.isLoggedIn(premiumUuid), "註冊完成後應設定為登入狀態");
        assertTrue(dataSource.isRegistered(username), "資料庫應已成功儲存該正版玩家的帳號資料");

        // 3. 模擬玩家離線後重新連線
        AuthManager.setLoggedOut(premiumUuid);
        assertFalse(AuthManager.isLoggedIn(premiumUuid));

        // 4. 正版玩家第二次進服（已有資料庫帳號）：應自動通過驗證並登入
        boolean autoLoggedInSecondTime = AuthLogic.handlePlayerJoin(premiumUuid, username, ip, true);
        assertTrue(autoLoggedInSecondTime, "已註冊之正版玩家應自動完成登入");
        assertTrue(AuthManager.isLoggedIn(premiumUuid), "玩家狀態應自動變更為已登入");
    }

    @Test
    public void testRegistrationDisabledBlocksRegistration() {
        // 設定 settings.registration.enabled 為 false
        Map<String, Object> regMap = new HashMap<>();
        regMap.put("enabled", false);
        Map<String, Object> setMap = new HashMap<>();
        setMap.put("registration", regMap);
        Map<String, Object> rootMap = new HashMap<>();
        rootMap.put("settings", setMap);

        NeoAuthConfig config = NeoAuthConfig.fromMap(rootMap);
        ConfigManager.getInstance().setConfig(config);

        UUID playerUuid = UUID.randomUUID();
        String username = "WebOnlyPlayer";
        String ip = "192.168.1.100";

        // 未註冊玩家進服提示應為「遊戲內不開放註冊！」
        String prompt = AuthLogic.getPromptMessage(username);
        assertEquals("§c遊戲內不開放註冊！", prompt);

        // 嘗試在遊戲內 /register 應被阻擋
        AuthLogic.RegisterResult regResult = AuthLogic.attemptRegister(playerUuid, username, ip, "Pass123", "Pass123");
        assertEquals(AuthLogic.RegisterResult.REGISTRATION_DISABLED, regResult);
        assertFalse(AuthManager.isLoggedIn(playerUuid));
        assertFalse(dataSource.isRegistered(username));

        // 恢復 registration.enabled 為 true
        regMap.put("enabled", true);
        config = NeoAuthConfig.fromMap(rootMap);
        ConfigManager.getInstance().setConfig(config);

        assertEquals("§c請先註冊！使用指令: §e/register <密碼> <確認密碼>", AuthLogic.getPromptMessage(username));
        regResult = AuthLogic.attemptRegister(playerUuid, username, ip, "Pass123", "Pass123");
        assertEquals(AuthLogic.RegisterResult.SUCCESS, regResult);
        assertTrue(AuthManager.isLoggedIn(playerUuid));
    }

    @Test
    public void testRegistrationForceOption() {
        Map<String, Object> regMap = new HashMap<>();
        regMap.put("force", false);
        Map<String, Object> setMap = new HashMap<>();
        setMap.put("registration", regMap);
        Map<String, Object> rootMap = new HashMap<>();
        rootMap.put("settings", setMap);

        NeoAuthConfig config = NeoAuthConfig.fromMap(rootMap);
        ConfigManager.getInstance().setConfig(config);

        UUID guestUuid = UUID.randomUUID();
        String username = "GuestPlayer";
        String ip = "192.168.1.101";

        // 非強制註冊模式：未註冊訪客進服應直接放行並登入，且資料庫中已建立空密碼帳號
        boolean allowed = AuthLogic.handlePlayerJoin(guestUuid, username, ip, false);
        assertTrue(allowed, "非強制註冊模式下未註冊訪客應直接放行");
        assertTrue(AuthManager.isLoggedIn(guestUuid), "未註冊訪客狀態應為已登入（免驗證）");
        assertTrue(dataSource.isRegistered(username), "資料庫應已自動建立該使用者紀錄");
        assertFalse(dataSource.hasPassword(username), "訪客使用者的密碼應為空");

        // 訪客第二次進入伺服器
        boolean allowedSecond = AuthLogic.handlePlayerJoin(guestUuid, username, ip, false);
        assertTrue(allowedSecond, "空密碼訪客第二次進入應繼續自動放行");
        assertTrue(AuthManager.isLoggedIn(guestUuid));

        // 訪客玩家希望主動為自己設定密碼，執行 /register
        AuthLogic.RegisterResult regResult = AuthLogic.attemptRegister(guestUuid, username, ip, "Secret123", "Secret123");
        assertEquals(AuthLogic.RegisterResult.SUCCESS, regResult, "空密碼訪客應可透過 /register 設定正式密碼");
        assertTrue(dataSource.hasPassword(username), "設定密碼後 hasPassword 應為 true");
        assertTrue(dataSource.checkPassword(username, "Secret123"), "新密碼應可正確校驗");

        // 已經有密碼的已登入玩家再次執行 /register 應被拒絕
        AuthLogic.RegisterResult regAgain = AuthLogic.attemptRegister(guestUuid, username, ip, "Another123", "Another123");
        assertEquals(AuthLogic.RegisterResult.ALREADY_LOGGED_IN, regAgain);
    }

    @Test
    public void testForceLoginAfterRegister() {
        Map<String, Object> regMap = new HashMap<>();
        regMap.put("enabled", true);
        regMap.put("forceLoginAfterRegister", true);
        Map<String, Object> setMap = new HashMap<>();
        setMap.put("registration", regMap);
        Map<String, Object> rootMap = new HashMap<>();
        rootMap.put("settings", setMap);

        NeoAuthConfig config = NeoAuthConfig.fromMap(rootMap);
        ConfigManager.getInstance().setConfig(config);

        UUID playerUuid = UUID.randomUUID();
        String username = "NeedLoginPlayer";
        String ip = "192.168.1.102";

        AuthLogic.RegisterResult regResult = AuthLogic.attemptRegister(playerUuid, username, ip, "Pass123", "Pass123");
        assertEquals(AuthLogic.RegisterResult.SUCCESS, regResult);
        assertTrue(dataSource.isRegistered(username), "帳號應已成功建立");
        assertFalse(AuthManager.isLoggedIn(playerUuid), "forceLoginAfterRegister 啟用時註冊完畢不應直接處於登入狀態");
    }

    @Test
    public void testSetEnableRegisterPersistence() {
        boolean result = ConfigManager.getInstance().setEnableRegister(false);
        assertTrue(result);
        assertFalse(ConfigManager.getInstance().getConfig().isRegistrationEnabled());

        result = ConfigManager.getInstance().setEnableRegister(true);
        assertTrue(result);
        assertTrue(ConfigManager.getInstance().getConfig().isRegistrationEnabled());
    }
}
