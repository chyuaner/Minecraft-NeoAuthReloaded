package tw.yuaner.neoauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tw.yuaner.neoauth.config.NeoAuthConfig;
import tw.yuaner.neoauth.util.YggdrasilService;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class YggdrasilServiceTest {

    @Test
    @DisplayName("驗證 YggdrasilService 處理無效或空參數回傳 false")
    public void testInvalidParametersReturnFalse() {
        assertFalse(YggdrasilService.hasJoined(null, "AiyinMi", "digest123", "127.0.0.1"));
        assertFalse(YggdrasilService.hasJoined("", "AiyinMi", "digest123", "127.0.0.1"));
        assertFalse(YggdrasilService.hasJoined("   ", "AiyinMi", "digest123", "127.0.0.1"));
        assertFalse(YggdrasilService.hasJoined("https://mc8.yuaner.tw/api/yggdrasil", null, "digest123", "127.0.0.1"));
        assertFalse(YggdrasilService.hasJoined("https://mc8.yuaner.tw/api/yggdrasil", "AiyinMi", null, "127.0.0.1"));
    }

    @Test
    @DisplayName("驗證 NeoAuthConfig 解析 customYggdrasilUrl 正確性")
    public void testConfigParsing() {
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("customYggdrasilUrl", "https://mc8.yuaner.tw/api/yggdrasil");
        map.put("settings", settings);

        NeoAuthConfig config = NeoAuthConfig.fromMap(map);
        assertEquals("https://mc8.yuaner.tw/api/yggdrasil", config.getCustomYggdrasilUrl());
    }

    @Test
    @DisplayName("驗證 AuthManager 儲存與檢索已驗證 Textures 屬性")
    public void testAuthManagerTexturesCache() {
        java.util.UUID uuid = java.util.UUID.randomUUID();
        String username = "Barianyyy0517";
        String dummyTextures = "ewogICJ0aW1lc3RhbXAiIDogMTYwMDAwMDAwMCwKICAicHJvZmlsZUlkIiA6ICJjODUwMjU5YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJCYXJpYW55eXkwNTE3IgogIH0=";
        String dummySig = "dummySignature123";

        AuthManager.markPremiumVerifiedWithTextures(uuid, username, dummyTextures, dummySig);

        assertTrue(AuthManager.isPremiumVerified(uuid));
        assertNotNull(AuthManager.getVerifiedTextures(uuid));
        assertEquals(dummyTextures, AuthManager.getVerifiedTextures(uuid).value());
        assertEquals(dummySig, AuthManager.getVerifiedTextures(uuid).signature());
        assertTrue(AuthManager.getVerifiedTextures(uuid).hasSignature());

        assertNotNull(AuthManager.getVerifiedTextures("barianyyy0517"));
        assertEquals(dummyTextures, AuthManager.getVerifiedTextures("barianyyy0517").value());

        AuthManager.clearPremiumVerified(uuid);
        assertFalse(AuthManager.isPremiumVerified(uuid));
        assertNull(AuthManager.getVerifiedTextures(uuid));
    }
}
