package tw.yuaner.neoauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.config.NeoAuthConfig;
import tw.yuaner.neoauth.core.LoginLogsService;
import tw.yuaner.neoauth.database.SqliteDataSource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LoginLogsServiceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() throws Exception {
        Path dbFile = tempDir.resolve("test_logs.db");
        String yaml = "DataSource:\n  backend: 'SQLITE'\n  sqLiteFile: '" + dbFile.toString() + "'\n";
        IAuthConfig config = NeoAuthConfig.fromMap(new org.yaml.snakeyaml.Yaml().load(yaml));

        Map<String, Object> logMap = new HashMap<>();
        logMap.put("enabled", true);
        logMap.put("server_name", "test");
        logMap.put("mySQLTablename", "login_logs");
        ConfigManager.getInstance().setLoginLogsConfig(tw.yuaner.neoauth.config.LoginLogsConfig.fromMap(Map.of("login_logs", logMap)));

        SqliteDataSource ds = new SqliteDataSource();
        ds.connect(config);
        DatabaseManager.setActiveDataSource(ds);
    }

    @Test
    public void testHelpOutput() {
        LoginLogsService.QueryResult result = LoginLogsService.handleCommand("player1", false, "help");
        assertTrue(result.isSuccess());
        assertTrue(result.getLines().stream().anyMatch(l -> l.contains("/loginlogs")));
        assertTrue(result.getLines().stream().anyMatch(l -> l.contains("1h")));
        assertTrue(result.getLines().stream().anyMatch(l -> l.contains("1d")));
    }

    @Test
    public void testDefaultOutputIncludesTip() {
        // 先插入一筆紀錄
        String uuid = UUID.randomUUID().toString();
        long logId = DatabaseManager.insertLoginLog("player1", uuid, "192.168.1.100", "TPE", "srv.mc", "127.0.0.1", 25565, "Password", "TCP", "test", 0);
        DatabaseManager.updateLoginLogLogout(logId, 0);

        LoginLogsService.QueryResult result = LoginLogsService.handleCommand("player1", false, null);
        assertTrue(result.isSuccess());
        assertFalse(result.getLines().isEmpty());

        // 應包含登入時間、遊玩時間與 IP
        assertTrue(result.getLines().stream().anyMatch(l -> l.contains("192.168.1.100")));
        assertTrue(result.getLines().stream().anyMatch(l -> l.contains("登入")));
        assertTrue(result.getLines().stream().anyMatch(l -> l.contains("遊玩時間")));

        // 預設查詢應包含提示說明 (2~3行內)
        assertTrue(result.getLines().stream().anyMatch(l -> l.contains("提示:")));
    }

    @Test
    public void testNonAdminCannotQueryOtherPlayer() {
        LoginLogsService.QueryResult result = LoginLogsService.handleCommand("player1", false, "player2");
        assertFalse(result.isSuccess());
        assertTrue(result.getLines().stream().anyMatch(l -> l.contains("權限")));
    }

    @Test
    public void testAdminCanQueryOtherPlayer() {
        String uuid = UUID.randomUUID().toString();
        DatabaseManager.insertLoginLog("player2", uuid, "10.0.0.1", "HKG", "srv.mc", "127.0.0.1", 25565, "Password", "TCP", "test", 0);

        LoginLogsService.QueryResult result = LoginLogsService.handleCommand("admin", true, "player2");
        assertTrue(result.isSuccess());
        assertTrue(result.getLines().stream().anyMatch(l -> l.contains("10.0.0.1")));
    }

    @Test
    public void testTimeQuery() {
        String uuid = UUID.randomUUID().toString();
        DatabaseManager.insertLoginLog("player1", uuid, "172.16.0.1", "TPE", "srv.mc", "127.0.0.1", 25565, "Password", "TCP", "test", 0);

        LoginLogsService.QueryResult result = LoginLogsService.handleCommand("player1", false, "1d");
        assertTrue(result.isSuccess());
        assertTrue(result.getLines().stream().anyMatch(l -> l.contains("172.16.0.1")));
    }

    @Test
    public void testMultiServerFiltering() {
        String uuid = UUID.randomUUID().toString();
        // 插入屬於本伺服器 (test) 的日誌
        DatabaseManager.insertLoginLog("player1", uuid, "192.168.10.50", "TPE", "srv.mc", "127.0.0.1", 25565, "Password", "TCP", "test", 0);
        // 插入屬於其他伺服器 (other-server) 的日誌 (共用資料庫情境)
        DatabaseManager.insertLoginLog("player1", uuid, "192.168.99.99", "TPE", "srv.mc", "127.0.0.1", 25565, "Password", "TCP", "other-server", 0);

        // 預設應過濾掉非本伺服器 (test) 的日誌
        LoginLogsService.QueryResult result = LoginLogsService.handleCommand("player1", false, "6");
        assertTrue(result.isSuccess());
        // 應該包含本伺服器的 IP
        assertTrue(result.getLines().stream().anyMatch(l -> l.contains("192.168.10.50")), "應包含本伺服器之日誌");
        // 不應包含其他伺服器的 IP
        assertFalse(result.getLines().stream().anyMatch(l -> l.contains("192.168.99.99")), "應篩選掉其他伺服器之日誌");
    }
}
