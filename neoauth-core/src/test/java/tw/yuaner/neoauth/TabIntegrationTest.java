package tw.yuaner.neoauth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.NeoAuthConfig;
import tw.yuaner.neoauth.core.PlayerSessionData;
import tw.yuaner.neoauth.util.TabIntegration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAB 模組連動與會話快取單元測試。
 */
public class TabIntegrationTest {

    private final UUID testUuid = UUID.randomUUID();
    private final String testUsername = "TestPlayer";

    @BeforeEach
    public void setUp() {
        AuthManager.setLoggedOut(testUuid);
        AuthManager.clearPremiumVerified(testUuid);
        AuthManager.removeSession(testUuid);
    }

    @AfterEach
    public void tearDown() {
        AuthManager.setLoggedOut(testUuid);
        AuthManager.clearPremiumVerified(testUuid);
        AuthManager.removeSession(testUuid);
    }

    @Test
    @DisplayName("測試 PlayerSessionData IPv4 與 IPv6 識別邏輯")
    public void testPlayerSessionDataIpTypes() {
        PlayerSessionData v4Session = new PlayerSessionData(testUuid, testUsername, "192.168.1.100", true, System.currentTimeMillis(), "test@example.com");
        assertFalse(v4Session.isIpv6());
        assertEquals("192.168.1.100", v4Session.getIp());

        PlayerSessionData v6Session = new PlayerSessionData(testUuid, testUsername, "2001:db8::1", true, System.currentTimeMillis(), null);
        assertTrue(v6Session.isIpv6());

        PlayerSessionData nullIpSession = new PlayerSessionData(testUuid, testUsername, null, false, 0L, null);
        assertFalse(nullIpSession.isIpv6());

        nullIpSession.setIp("fe80::1ff:fe23:4567:890a");
        assertTrue(nullIpSession.isIpv6());
    }

    @Test
    @DisplayName("測試 AuthManager 會話快取管理 (建立、查詢、更新信箱、刪除)")
    public void testAuthManagerSessionLifecycle() {
        assertNull(AuthManager.getSession(testUuid));
        assertNull(AuthManager.getSessionByName(testUsername));

        PlayerSessionData session = AuthManager.createSession(testUuid, testUsername, "1.1.1.1", false);
        assertNotNull(session);
        assertEquals(session, AuthManager.getSession(testUuid));
        assertEquals(session, AuthManager.getSessionByName(testUsername));
        assertEquals(session, AuthManager.getSessionByName("testplayer"));

        // 更新信箱 (依 UUID)
        AuthManager.updateSessionEmail(testUuid, "new@example.com");
        assertEquals("new@example.com", AuthManager.getSession(testUuid).getEmail());

        // 更新信箱 (依 名稱)
        AuthManager.updateSessionEmail("TestPlayer", "another@example.com");
        assertEquals("another@example.com", AuthManager.getSession(testUuid).getEmail());

        // 移除會話
        AuthManager.removeSession(testUuid);
        assertNull(AuthManager.getSession(testUuid));
        assertNull(AuthManager.getSessionByName(testUsername));
    }

    @Test
    @DisplayName("測試 TabIntegration 登入方式變數計算 (%neoauth_login_type%)")
    public void testTabIntegrationLoginType() {
        // 1. 未登入
        AuthManager.setLoggedOut(testUuid);
        AuthManager.createSession(testUuid, testUsername, "127.0.0.1", false);
        String unlogged = TabIntegration.getLoginType(testUuid);
        assertTrue(unlogged.contains("未登入"));

        // 2. 密碼登入 (isLoggedIn = true, isPremium = false)
        AuthManager.setLoggedIn(testUuid);
        String passwordType = TabIntegration.getLoginType(testUuid);
        assertTrue(passwordType.contains("密碼登入"));

        // 3. 正版登入 (isLoggedIn = true, isPremium = true)
        AuthManager.getSession(testUuid).setPremium(true);
        String premiumType = TabIntegration.getLoginType(testUuid);
        assertTrue(premiumType.contains("Mojang"));
    }

    @Test
    @DisplayName("測試 TabIntegration IP 線路變數計算 (%neoauth_ip_type%)")
    public void testTabIntegrationIpType() {
        // IPv4
        AuthManager.createSession(testUuid, testUsername, "192.168.0.1", false);
        assertEquals("§7IPv4", TabIntegration.getIpType(testUuid));

        // IPv6
        AuthManager.createSession(testUuid, testUsername, "2400:cb00:2048:1::c629:d7a2", false);
        assertEquals("§dIPv6", TabIntegration.getIpType(testUuid));

        // null / 未知
        AuthManager.createSession(testUuid, testUsername, null, false);
        assertEquals("§7未知", TabIntegration.getIpType(testUuid));
        assertEquals("§7未知", TabIntegration.getIpType(null));
    }

    @Test
    @DisplayName("測試 TabIntegration 登入時間變數計算 (%neoauth_login_time%)")
    public void testTabIntegrationLoginTime() {
        AuthManager.setLoggedOut(testUuid);
        assertEquals("-", TabIntegration.getLoginTime(testUuid));

        AuthManager.setLoggedIn(testUuid);
        AuthManager.createSession(testUuid, testUsername, "127.0.0.1", true);
        String timeStr = TabIntegration.getLoginTime(testUuid);
        assertNotEquals("-", timeStr);
        assertTrue(timeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    @DisplayName("測試 TabIntegration 信箱變數計算 (%neoauth_email%)")
    public void testTabIntegrationEmail() {
        AuthManager.createSession(testUuid, testUsername, "127.0.0.1", true);
        assertEquals("§7未綁定", TabIntegration.getEmail(testUuid));

        AuthManager.updateSessionEmail(testUuid, "player@yuaner.tw");
        assertEquals("player@yuaner.tw", TabIntegration.getEmail(testUuid));

        assertEquals("§7未綁定", TabIntegration.getEmail(null));
    }

    @Test
    @DisplayName("測試 NeoAuthConfig 解析 tab 設定 (enabled 與 dateFormat)")
    public void testNeoAuthConfigTabSettings() {
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> tabMap = new HashMap<>();
        tabMap.put("enabled", false);
        tabMap.put("dateFormat", "yyyy/MM/dd HH:mm");
        map.put("tab", tabMap);

        NeoAuthConfig config = NeoAuthConfig.fromMap(map);
        assertFalse(config.isTabIntegrationEnabled());
        assertEquals("yyyy/MM/dd HH:mm", config.getTabDateFormat());

        // 預設值檢查
        NeoAuthConfig defaultConfig = NeoAuthConfig.fromMap(new HashMap<>());
        assertTrue(defaultConfig.isTabIntegrationEnabled());
        assertEquals("yyyy-MM-dd HH:mm:ss", defaultConfig.getTabDateFormat());
    }
}
