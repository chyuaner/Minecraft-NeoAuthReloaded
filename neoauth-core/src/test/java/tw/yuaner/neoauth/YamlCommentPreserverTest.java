package tw.yuaner.neoauth;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import tw.yuaner.neoauth.config.NeoAuthConfig;
import tw.yuaner.neoauth.config.YamlCommentPreserver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class YamlCommentPreserverTest {

    @Test
    public void testMergePreservingComments() throws Exception {
        String templateText;
        try (InputStream in = getClass().getResourceAsStream("/defaults/config.yml")) {
            assertNotNull(in, "Template /defaults/config.yml should exist");
            templateText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 模擬使用者提供的最小配置 (類似 samples/config/neoauth/config.yml)
        String userMinimalYaml = """
                DataSource:
                  backend: "MARIADB"
                  mySQLHost: "192.168.1.100"
                  mySQLPort: "3306"
                  mySQLDatabase: "custom_db"
                  mySQLUsername: "custom_user"
                  mySQLPassword: "my_secret_password"
                  mySQLTablename: "custom_auth"
                
                settings:
                  messagesLanguage: "en"
                  allowOfflinePlayers: false
                  security:
                    passwordHash: "SHA256"
                """;

        Yaml yaml = new Yaml();
        Map<String, Object> diskMap = yaml.load(userMinimalYaml);

        String mergedYaml = YamlCommentPreserver.mergePreservingComments(templateText, diskMap);

        // 1. 驗證所有註解完整保留
        assertTrue(mergedYaml.contains("# NeoAuth 主設定檔"));
        assertTrue(mergedYaml.contains("# 資料庫類型 (支援: MARIADB, MYSQL)"));
        assertTrue(mergedYaml.contains("# 玩家未驗證時的行為限制"));
        assertTrue(mergedYaml.contains("# 重生點與登入傳送設定"));

        // 2. 驗證使用者的自訂值被正確替換
        assertTrue(mergedYaml.contains("mySQLHost: \"192.168.1.100\""));
        assertTrue(mergedYaml.contains("mySQLDatabase: \"custom_db\""));
        assertTrue(mergedYaml.contains("mySQLUsername: \"custom_user\""));
        assertTrue(mergedYaml.contains("mySQLPassword: \"my_secret_password\""));
        assertTrue(mergedYaml.contains("mySQLTablename: \"custom_auth\""));
        assertTrue(mergedYaml.contains("messagesLanguage: \"en\""));
        assertTrue(mergedYaml.contains("allowOfflinePlayers: false"));

        // 3. 驗證範本中缺失的區塊與預設值被自動補齊
        assertTrue(mergedYaml.contains("poolSize: 10"));
        assertTrue(mergedYaml.contains("maxLifetime: 1800"));
        assertTrue(mergedYaml.contains("timeout: 90"));
        assertTrue(mergedYaml.contains("minPasswordLength: 0"));
        assertTrue(mergedYaml.contains("maxPasswordLength: 0"));
        assertTrue(mergedYaml.contains("teleportUnAuthedToSpawn: false"));
        assertTrue(mergedYaml.contains("saveQuitLocation: true"));

        // 4. 驗證合併後的 YAML 可被完整解析為 NeoAuthConfig 物件
        Map<String, Object> mergedMap = yaml.load(mergedYaml);
        NeoAuthConfig config = NeoAuthConfig.fromMap(mergedMap);
        assertEquals("192.168.1.100", config.getDbHost());
        assertEquals("custom_db", config.getDbName());
        assertEquals("my_secret_password", config.getDbPassword());
        assertEquals("en", config.getMessagesLanguage());
        assertFalse(config.isAllowOfflinePlayers());
        assertEquals("SHA256", config.getPasswordHash());
        assertEquals(90, config.getTimeout());
        assertEquals(0, config.getMinPasswordLength());
        assertEquals(0, config.getMaxPasswordLength());
    }

    @Test
    public void testPasswordManagerSha256() {
        String password = "TestPassword123";
        String hash = PasswordManager.hashPassword(password);

        assertTrue(hash.startsWith("$SHA$"), "AuthMe SHA256 hash must start with $SHA$");
        assertTrue(PasswordManager.checkPassword(password, hash), "Password check must succeed for correct password");
        assertFalse(PasswordManager.checkPassword("WrongPassword", hash), "Password check must fail for wrong password");
    }
}
