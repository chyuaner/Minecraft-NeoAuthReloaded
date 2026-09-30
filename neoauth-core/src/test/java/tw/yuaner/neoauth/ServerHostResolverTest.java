package tw.yuaner.neoauth;

import org.junit.jupiter.api.Test;
import tw.yuaner.neoauth.util.ServerHostResolver;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ServerHostResolverTest {

    @Test
    public void testCleanHost() {
        assertEquals("mc8.yuaner.tw", ServerHostResolver.cleanHost("mc8.yuaner.tw"));
        assertEquals("mc8.yuaner.tw", ServerHostResolver.cleanHost("mc8.yuaner.tw\0FML3\0"));
        assertEquals("mc8-4.yuaner.tw", ServerHostResolver.cleanHost("mc8-4.yuaner.tw\0FML\0"));
        assertEquals("mc8.yuaner.tw", ServerHostResolver.cleanHost("mc8.yuaner.tw."));
        assertEquals("mc8-play.yuaner.tw:25565", ServerHostResolver.cleanHost("mc8-play.yuaner.tw:25565"));
        assertEquals("mc8-play4.yuaner.tw:25565", ServerHostResolver.cleanHost("\"mc8-play4.yuaner.tw:25565\""));
        assertNull(ServerHostResolver.cleanHost(""));
        assertNull(ServerHostResolver.cleanHost(null));
    }

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
        private FakeHttpRequest handshakeRequest;

        public FakeConnection(FakeHttpRequest req) {
            this.handshakeRequest = req;
        }

        public FakeHttpRequest getWsHandshakeRequest() {
            return handshakeRequest;
        }
    }

    public static class FakeClientIntentionPacket {
        private final String hostName;
        private final int port;

        public FakeClientIntentionPacket(String hostName, int port) {
            this.hostName = hostName;
            this.port = port;
        }

        public String hostName() {
            return hostName;
        }

        public int port() {
            return port;
        }
    }

    @Test
    public void testExtractWsHostCdn() {
        FakeHttpRequest req = new FakeHttpRequest();
        req.headers().set("Host", "mc8-ws.yuaner.tw");
        req.headers().set("X-Forwarded-Proto", "https");
        FakeConnection conn = new FakeConnection(req);

        String host = ServerHostResolver.extractWsHost(conn);
        assertEquals("wss://mc8-ws.yuaner.tw", host);
    }

    @Test
    public void testExtractWsHostForwardedHost() {
        FakeHttpRequest req = new FakeHttpRequest();
        req.headers().set("Host", "127.0.0.1:25565");
        req.headers().set("X-Forwarded-Host", "mc8-play.yuaner.tw");
        req.headers().set("CF-Visitor", "{\"scheme\":\"https\"}");
        FakeConnection conn = new FakeConnection(req);

        String host = ServerHostResolver.extractWsHost(conn);
        assertEquals("wss://mc8-play.yuaner.tw", host);
    }

    public static class FakeListener {
        public FakeConnection connection;

        public FakeListener(FakeConnection conn) {
            this.connection = conn;
        }

        public FakeConnection getConnection() {
            return connection;
        }
    }

    public static class FakePlayer {
        public FakeListener connection;

        public FakePlayer(FakeConnection conn) {
            this.connection = new FakeListener(conn);
        }
    }

    @Test
    public void testRecordHandshakeAndResolve() {
        FakeConnection conn = new FakeConnection(null);
        FakeClientIntentionPacket packet = new FakeClientIntentionPacket("mc8.yuaner.tw\0FML3\0", 25565);

        ServerHostResolver.recordHandshake(conn, packet);

        FakePlayer fakePlayer = new FakePlayer(conn);
        String host = ServerHostResolver.resolveServerHost(fakePlayer);
        assertEquals("mc8.yuaner.tw", host);
    }

    @Test
    public void testRecordHandshakeWithPortDirectConnect() {
        FakeConnection conn = new FakeConnection(null);
        FakeClientIntentionPacket packet = new FakeClientIntentionPacket("mc8-play.yuaner.tw:25565", 25565);

        ServerHostResolver.recordHandshake(conn, packet);

        FakePlayer fakePlayer = new FakePlayer(conn);
        String host = ServerHostResolver.resolveServerHost(fakePlayer);
        assertEquals("mc8-play.yuaner.tw:25565", host);
    }
}
