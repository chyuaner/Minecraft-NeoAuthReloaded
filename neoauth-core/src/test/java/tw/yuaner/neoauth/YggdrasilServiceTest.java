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
}
