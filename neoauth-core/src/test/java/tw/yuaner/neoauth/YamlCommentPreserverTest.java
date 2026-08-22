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
        assertTrue(mergedYaml.contains("# 資料庫類型 (支援: SQLITE, MARIADB, MYSQL)"));
        assertTrue(mergedYaml.contains("# 玩家未驗證時的行為限制"));
        assertTrue(mergedYaml.contains("# 是否強制保持離線版 UUID (v3) 相容模式"));

        // 2. 驗證使用者的自訂值被正確替換
        assertTrue(mergedYaml.contains("mySQLHost: \"192.168.1.100\""));
        assertTrue(mergedYaml.contains("mySQLDatabase: \"custom_db\""));
        assertTrue(mergedYaml.contains("mySQLUsername: \"custom_user\""));
        assertTrue(mergedYaml.contains("mySQLPassword: \"my_secret_password\""));
        assertTrue(mergedYaml.contains("mySQLTablename: \"custom_auth\""));
        assertTrue(mergedYaml.contains("messagesLanguage: \"en\""));
        assertTrue(mergedYaml.contains("allowOfflinePlayers: false"));

        // 3. 驗證範本中缺失的區塊與預設值被自動補齊 (包含 SSL 與自訂欄位名稱)
        assertTrue(mergedYaml.contains("sqLiteFile: \"config/neoauth/neoauth.db\""));
        assertTrue(mergedYaml.contains("mySQLUseSSL: false"));
        assertTrue(mergedYaml.contains("mySQLCheckServerCertificate: true"));
        assertTrue(mergedYaml.contains("mySQLAllowPublicKeyRetrieval: true"));
        assertTrue(mergedYaml.contains("mySQLColumnId: \"id\""));
        assertTrue(mergedYaml.contains("mySQLColumnName: \"username\""));
        assertTrue(mergedYaml.contains("mySQLRealName: \"realname\""));
        assertTrue(mergedYaml.contains("mySQLColumnPassword: \"password\""));
        assertTrue(mergedYaml.contains("mySQLtotpKey: \"totp\""));
        assertTrue(mergedYaml.contains("poolSize: 10"));
        assertTrue(mergedYaml.contains("maxLifetime: 1800"));
        assertTrue(mergedYaml.contains("timeout: 90"));
        assertTrue(mergedYaml.contains("minPasswordLength: 0"));
        assertTrue(mergedYaml.contains("maxPasswordLength: 0"));
        assertTrue(mergedYaml.contains("keepOfflineUuidCompatibility: false"));

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
        assertFalse(config.isMySqlUseSSL());
        assertTrue(config.isMySqlCheckServerCertificate());
        assertTrue(config.isMySqlAllowPublicKeyRetrieval());
        assertEquals("id", config.getMySqlColumnId());
        assertEquals("username", config.getMySqlColumnName());
        assertEquals("realname", config.getMySqlRealName());
        assertEquals("password", config.getMySqlColumnPassword());
        assertEquals("totp", config.getMySqlTotpKey());
    }

    @Test
    public void testPasswordManagerSha256() {
        String password = "TestPassword123";
        String hash = PasswordManager.hashPassword(password);

        assertTrue(hash.startsWith("$SHA$"), "AuthMe SHA256 hash must start with $SHA$");
        assertTrue(PasswordManager.checkPassword(password, hash), "Password check must succeed for correct password");
        assertFalse(PasswordManager.checkPassword("WrongPassword", hash), "Password check must fail for wrong password");
    }

    @Test
    public void testPasswordManagerSalted2Md5() {
        String password = "MySecretPassword!";
        String salt = "aB3dE6";

        // 1. Discuz! / Blessing Skin 模式：獨立 salt 欄位 (MD5(MD5(password) + salt))
        String discuzHash = PasswordManager.computeSalted2Md5(password, salt);
        assertEquals(32, discuzHash.length());
        assertTrue(PasswordManager.checkPassword(password, discuzHash, salt));
        assertFalse(PasswordManager.checkPassword("WrongPassword", discuzHash, salt));

        // 2. AuthMe $MD5$salt$hash 複合格式
        String authmeMd5 = "$MD5$" + salt + "$" + discuzHash;
        assertTrue(PasswordManager.checkPassword(password, authmeMd5));
        assertFalse(PasswordManager.checkPassword("WrongPassword", authmeMd5));
    }

    @Test
    public void testPasswordManagerSaltedSha512() {
        String password = "MySecurePassword512";
        String salt = "1234567890abcdef";

        // AuthMe $SHA$salt$hash (SHA-512, 128 hex chars)
        String hash512 = PasswordManager.sha512Hex(PasswordManager.sha512Hex(password) + salt);
        assertEquals(128, hash512.length());
        String fullHash = "$SHA$" + salt + "$" + hash512;

        assertTrue(PasswordManager.checkPassword(password, fullHash));
        assertFalse(PasswordManager.checkPassword("WrongPassword", fullHash));
    }
}
