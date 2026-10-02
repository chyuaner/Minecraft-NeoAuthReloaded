package tw.yuaner.neoauth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.yuaner.neoauth.config.ConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class LanguageModeIntegrationTest {

    @TempDir
    Path tempDir;

    private Path originalConfigDir;

    @BeforeEach
    public void setUp() {
        originalConfigDir = ConfigManager.getInstance().getConfigDir();
        ConfigManager.getInstance().setConfigDir(tempDir);
    }

    @AfterEach
    public void tearDown() {
        ConfigManager.getInstance().setConfigDir(originalConfigDir);
    }

    @Test
    public void testStandardModeWithoutMessagesLanguage() {
        // 全新安裝：無 config.yml，無 settings.messagesLanguage
        ConfigManager.getInstance().load();

        assertNull(ConfigManager.getInstance().getConfig().getMessagesLanguage());

        // 應自動釋出 messages.yml 與 help.yml
        Path messagesFile = tempDir.resolve("messages").resolve("messages.yml");
        Path helpFile = tempDir.resolve("messages").resolve("help.yml");
        assertTrue(Files.exists(messagesFile), "messages.yml should be extracted in standard mode");
        assertTrue(Files.exists(helpFile), "help.yml should be extracted in standard mode");

        // 檢查訊息與幫助管理器
        String successMsg = ConfigManager.getInstance().getMessagesManager().get("login.success");
        assertTrue(successMsg.contains("登入成功"));

        String failedMsg = ConfigManager.getInstance().getMessagesManager().get("admin.setenableregister_failed");
        assertTrue(failedMsg.contains("設定儲存失敗"));

        String enabledMsg = ConfigManager.getInstance().getMessagesManager().get("general.enabled");
        assertEquals("啟用", enabledMsg);

        assertNotNull(ConfigManager.getInstance().getHelpManager().getAdminHeader());
        assertTrue(ConfigManager.getInstance().getHelpManager().getAdminHelpLines().size() > 5);
    }

    @Test
    public void testMultiLanguageCompatModeWithMessagesLanguage() throws IOException {
        // 模擬既有 AuthMe 設定檔：帶有 settings.messagesLanguage: "en"
        String legacyConfig = """
                settings:
                  messagesLanguage: "en"
                """;
        Files.writeString(tempDir.resolve("config.yml"), legacyConfig);

        ConfigManager.getInstance().load();

        assertEquals("en", ConfigManager.getInstance().getConfig().getMessagesLanguage());

        // 應自動合併/釋出 messages_en.yml 與 help_en.yml
        Path messagesFile = tempDir.resolve("messages").resolve("messages_en.yml");
        Path helpFile = tempDir.resolve("messages").resolve("help_en.yml");
        assertTrue(Files.exists(messagesFile), "messages_en.yml should be extracted in compat mode");
        assertTrue(Files.exists(helpFile), "help_en.yml should be extracted in compat mode");

        // 英文語系驗證
        String successMsg = ConfigManager.getInstance().getMessagesManager().get("login.success");
        assertTrue(successMsg.contains("Successful login"));

        String enabledMsg = ConfigManager.getInstance().getMessagesManager().get("general.enabled");
        assertEquals("Enabled", enabledMsg);

        assertTrue(ConfigManager.getInstance().getHelpManager().getAdminHeader().contains("Admin Commands"));
    }
}
