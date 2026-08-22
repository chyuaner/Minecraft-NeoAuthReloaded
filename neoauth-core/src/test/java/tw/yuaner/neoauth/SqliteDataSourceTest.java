package tw.yuaner.neoauth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.yuaner.neoauth.config.NeoAuthConfig;
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

        dataSource = new SqliteDataSource();
        dataSource.connect(config);
    }

    @AfterEach
    public void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
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
}
