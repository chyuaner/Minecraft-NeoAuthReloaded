package tw.yuaner.neoauth;

import org.junit.jupiter.api.Test;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.NeoAuthConfig;
import tw.yuaner.neoauth.util.WebSocketIpResolver;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class WebSocketIpResolverTest {

    @Test
    public void testCleanIp() {
        assertEquals("114.136.234.154", WebSocketIpResolver.cleanIp("114.136.234.154"));
        assertEquals("114.136.234.154", WebSocketIpResolver.cleanIp(" 114.136.234.154 "));
        assertEquals("114.136.234.154", WebSocketIpResolver.cleanIp("\"114.136.234.154\""));
        assertEquals("114.136.234.154", WebSocketIpResolver.cleanIp("114.136.234.154:25565"));
        assertEquals("2001:db8::1", WebSocketIpResolver.cleanIp("[2001:db8::1]:25565"));
        assertEquals("2001:db8::1", WebSocketIpResolver.cleanIp("[2001:db8::1]"));
        assertEquals("2001:db8::1", WebSocketIpResolver.cleanIp("2001:db8::1"));
        assertNull(WebSocketIpResolver.cleanIp(""));
        assertNull(WebSocketIpResolver.cleanIp(null));
    }

    @Test
    public void testIsIpAddress() {
        assertTrue(WebSocketIpResolver.isIpAddress("127.0.0.1"));
        assertTrue(WebSocketIpResolver.isIpAddress("114.136.234.154"));
        assertTrue(WebSocketIpResolver.isIpAddress("::1"));
        assertTrue(WebSocketIpResolver.isIpAddress("2001:db8::1"));

        assertFalse(WebSocketIpResolver.isIpAddress(""));
        assertFalse(WebSocketIpResolver.isIpAddress(null));
        assertFalse(WebSocketIpResolver.isIpAddress("unknown"));
        assertFalse(WebSocketIpResolver.isIpAddress("256.0.0.1"));
        assertFalse(WebSocketIpResolver.isIpAddress("1.2.3"));
        assertFalse(WebSocketIpResolver.isIpAddress("example.com"));
    }

    @Test
    public void testParseForwardedHeader() {
        assertEquals("192.0.2.60", WebSocketIpResolver.parseForwardedHeader("for=192.0.2.60;proto=http;by=203.0.113.43"));
        assertEquals("2001:db8:cafe::17", WebSocketIpResolver.parseForwardedHeader("for=\"[2001:db8:cafe::17]:4711\""));
        assertNull(WebSocketIpResolver.parseForwardedHeader("proto=https;by=203.0.113.43"));
    }

    // 模擬 WSMC Connection 物件與 Netty HttpRequest
    public static class FakeHttpHeaders {
        private final Map<String, String> map = new HashMap<>();

        public void set(String key, String value) {
            map.put(key, value);
        }

        public String get(String key) {
            return map.get(key);
        }
    }

    public static class FakeHttpRequest {
        private final FakeHttpHeaders headers = new FakeHttpHeaders();

        public FakeHttpHeaders headers() {
            return headers;
        }
    }

    public static class FakeConnection {
        private SocketAddress address = new InetSocketAddress("127.0.0.1", 54321);
        private FakeHttpRequest handshakeRequest;

        public FakeConnection(FakeHttpRequest req) {
            this.handshakeRequest = req;
        }

        public FakeHttpRequest getWsHandshakeRequest() {
            return handshakeRequest;
        }

        public SocketAddress getRemoteAddress() {
            return address;
        }
    }

    @Test
    public void testResolveIpWithCfConnectingIp() {
        FakeHttpRequest req = new FakeHttpRequest();
        req.headers().set("CF-Connecting-IP", "114.136.234.154");
        req.headers().set("X-Real-IP", "172.68.12.34");
        req.headers().set("X-Forwarded-For", "114.136.234.154, 172.68.12.34");

        FakeConnection conn = new FakeConnection(req);
        String resolved = WebSocketIpResolver.resolveIp(conn, "127.0.0.1");
        assertEquals("114.136.234.154", resolved);

        // 驗證 Connection 內之 address 亦被同步替換
        SocketAddress updatedAddr = conn.getRemoteAddress();
        assertInstanceOf(InetSocketAddress.class, updatedAddr);
        assertEquals("114.136.234.154", ((InetSocketAddress) updatedAddr).getAddress().getHostAddress());
        assertEquals(54321, ((InetSocketAddress) updatedAddr).getPort());
    }

    @Test
    public void testResolveIpWithXRealIp() {
        FakeHttpRequest req = new FakeHttpRequest();
        req.headers().set("X-Real-IP", "114.136.234.154");
        req.headers().set("X-Forwarded-For", "114.136.234.154");

        FakeConnection conn = new FakeConnection(req);
        String resolved = WebSocketIpResolver.resolveIp(conn, "127.0.0.1");
        assertEquals("114.136.234.154", resolved);
    }

    @Test
    public void testResolveIpFallback() {
        FakeConnection conn = new FakeConnection(null);
        String resolved = WebSocketIpResolver.resolveIp(conn, "114.136.234.154");
        assertEquals("114.136.234.154", resolved);

        String resolvedDefault = WebSocketIpResolver.resolveIp(null, null);
        assertEquals("127.0.0.1", resolvedDefault);
    }

    @Test
    public void testResolveIpWhenDisabled() throws Exception {
        Map<String, Object> extensionsMap = new HashMap<>();
        Map<String, Object> wsmcMap = new HashMap<>();
        wsmcMap.put("enabled", false);
        extensionsMap.put("wsmc", wsmcMap);

        NeoAuthConfig testConfig = NeoAuthConfig.fromMap(new HashMap<>(), extensionsMap);
        assertFalse(testConfig.isWsmcIntegrationEnabled());

        Field configField = ConfigManager.class.getDeclaredField("config");
        configField.setAccessible(true);
        NeoAuthConfig original = (NeoAuthConfig) configField.get(ConfigManager.getInstance());
        try {
            configField.set(ConfigManager.getInstance(), testConfig);

            FakeHttpRequest req = new FakeHttpRequest();
            req.headers().set("CF-Connecting-IP", "114.136.234.154");
            FakeConnection conn = new FakeConnection(req);

            // 當 wsmc.enabled = false 時，即使有 WSMC 握手標頭，也必須繞過解析直接回傳原生 fallbackIp
            String resolved = WebSocketIpResolver.resolveIp(conn, "127.0.0.1");
            assertEquals("127.0.0.1", resolved);
        } finally {
            configField.set(ConfigManager.getInstance(), original);
        }
    }
}
