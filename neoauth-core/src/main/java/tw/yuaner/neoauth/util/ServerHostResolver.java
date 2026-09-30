package tw.yuaner.neoauth.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 伺服器端連線網址 (Server Host / Domain) 解析工具類。
 * <p>
 * 用於在玩家連線進入伺服器時，精準識別並記錄玩家是透過哪一個網址或連線方式進服，
 * 例如：
 * <ul>
 *     <li>{@code mc8.yuaner.tw} (正式直連，走 SRV)</li>
 *     <li>{@code mc8-4.yuaner.tw} (正式直連，IPv4 Only，走 SRV)</li>
 *     <li>{@code wss://mc8-ws.yuaner.tw} (正式經由 CDN WebSocket)</li>
 *     <li>{@code wss://mc8-play.yuaner.tw} (備用兼測試用 WebSocket 直連)</li>
 *     <li>{@code mc8-play.yuaner.tw} 或 {@code mc8-play.yuaner.tw:25565} (備用兼測試用，無 SRV)</li>
 *     <li>{@code mc8-play4.yuaner.tw} 或 {@code mc8-play4.yuaner.tw:25565} (備用兼測試用，IPv4 Only，無 SRV)</li>
 * </ul>
 */
public class ServerHostResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-HostResolver");

    private static final Map<Object, String> CONNECTION_HOST_MAP = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PLAYER_HOST_MAP = new ConcurrentHashMap<>();

    /**
     * 在 Handshake 階段 (ServerHandshakePacketListenerImpl.handleIntention)
     * 從 ClientIntentionPacket 記錄客戶端連線主機名稱。
     *
     * @param connection net.minecraft.network.Connection 物件
     * @param packet     net.minecraft.network.protocol.handshake.ClientIntentionPacket 物件
     */
    public static void recordHandshake(Object connection, Object packet) {
        if (connection == null || packet == null) {
            return;
        }

        try {
            String host = extractPacketHost(packet);
            if (host != null && !host.isBlank()) {
                String cleaned = cleanHost(host);
                if (cleaned != null && !cleaned.isBlank()) {
                    CONNECTION_HOST_MAP.put(connection, cleaned);
                    setChannelAttribute(connection, cleaned);
                }
            }
        } catch (Throwable e) {
            LOGGER.debug("NeoAuth: 記錄 Handshake 主機名稱時發生異常: {}", e.getMessage());
        }
    }

    /**
     * 綁定玩家 UUID 與連線主機名稱。
     *
     * @param uuid 玩家 UUID
     * @param host 連線主機名稱
     */
    public static void bindPlayerHost(UUID uuid, String host) {
        if (uuid != null && host != null && !host.isBlank()) {
            PLAYER_HOST_MAP.put(uuid, host);
        }
    }

    /**
     * 當玩家斷線或離開時，清理暫存的主機名稱記錄。
     *
     * @param uuid 玩家 UUID
     */
    public static void cleanupPlayer(UUID uuid) {
        if (uuid != null) {
            PLAYER_HOST_MAP.remove(uuid);
        }
    }

    /**
     * 當 Connection 關閉時清理暫存。
     *
     * @param connection net.minecraft.network.Connection 物件
     */
    public static void cleanupConnection(Object connection) {
        if (connection != null) {
            CONNECTION_HOST_MAP.remove(connection);
        }
    }

    /**
     * 依據伺服器玩家實體解析其進服所使用的伺服器網址 (Server Host)。
     *
     * @param playerObj ServerPlayer 實體
     * @return 連線網址字串，例如 "mc8.yuaner.tw" 或 "wss://mc8-ws.yuaner.tw"
     */
    public static String resolveServerHost(Object playerObj) {
        if (playerObj == null) {
            return "127.0.0.1";
        }

        Object connection = WebSocketIpResolver.extractConnection(playerObj);
        if (connection == null) {
            connection = playerObj;
        }

        // 1. 優先檢測 WebSocket (WSMC) 握手 HTTP 標頭
        if (connection != null) {
            String wsHost = extractWsHost(connection);
            if (wsHost != null && !wsHost.isBlank()) {
                return wsHost;
            }
        }

        // 2. 檢測 Handshake 階段記錄的主機名稱
        if (connection != null) {
            // 從 Netty Channel Attribute 取得
            String channelHost = getChannelAttribute(connection);
            if (channelHost != null && !channelHost.isBlank()) {
                return channelHost;
            }

            // 從 Connection 暫存表取得
            String recorded = CONNECTION_HOST_MAP.get(connection);
            if (recorded != null && !recorded.isBlank()) {
                return recorded;
            }
        }

        // 3. 從玩家 UUID 綁定暫存取得
        try {
            Method getUUID = playerObj.getClass().getMethod("getUUID");
            Object uuidObj = getUUID.invoke(playerObj);
            if (uuidObj instanceof UUID u) {
                String playerHost = PLAYER_HOST_MAP.get(u);
                if (playerHost != null && !playerHost.isBlank()) {
                    return playerHost;
                }
            }
        } catch (Throwable ignored) {
        }

        return "127.0.0.1";
    }

    /**
     * 從 WSMC Connection 之 WebSocket 握手請求中提取 HTTP Host / 網址。
     *
     * @param connection net.minecraft.network.Connection 物件
     * @return 格式化後的 WebSocket 網址 (如 "wss://mc8-ws.yuaner.tw")，若無則傳回 null
     */
    public static String extractWsHost(Object connection) {
        if (connection == null) {
            return null;
        }

        try {
            tw.yuaner.neoauth.config.IAuthConfig config = tw.yuaner.neoauth.config.ConfigManager.getInstance().getConfig();
            if (config != null && !config.isWsmcIntegrationEnabled()) {
                return null;
            }

            Method getHandshakeMethod = findMethod(connection.getClass(), "getWsHandshakeRequest", 0);
            if (getHandshakeMethod == null) {
                getHandshakeMethod = findMethod(connection.getClass(), "getHandshakeRequest", 0);
            }
            if (getHandshakeMethod == null) {
                return null;
            }

            Object req = getHandshakeMethod.invoke(connection);
            if (req == null) {
                return null;
            }

            Method headersMethod = findMethod(req.getClass(), "headers", 0);
            if (headersMethod == null) {
                return null;
            }

            Object headers = headersMethod.invoke(req);
            if (headers == null) {
                return null;
            }

            Method getHeaderMethod = findHeaderGetMethod(headers.getClass());
            if (getHeaderMethod == null) {
                return null;
            }

            // 優先讀取 X-Forwarded-Host (代理前真實 Host)，次之讀取 Host 標頭
            String host = null;
            Object fwdHost = getHeaderMethod.invoke(headers, "X-Forwarded-Host");
            if (fwdHost != null && !fwdHost.toString().isBlank()) {
                host = fwdHost.toString().split(",")[0].trim();
            }
            if (host == null || host.isBlank()) {
                Object h = getHeaderMethod.invoke(headers, "Host");
                if (h != null) {
                    host = h.toString().trim();
                }
            }

            if (host != null && !host.isBlank()) {
                host = cleanHost(host);

                // 判斷是否為安全傳輸 (wss://)
                boolean isSecure = true; // 正式服多數透過 SSL 終結 (Cloudflare CDN / Nginx HTTPS)
                Object proto = getHeaderMethod.invoke(headers, "X-Forwarded-Proto");
                if (proto != null) {
                    String p = proto.toString().trim().toLowerCase();
                    if ("http".equals(p) || "ws".equals(p)) {
                        isSecure = false;
                    } else if ("https".equals(p) || "wss".equals(p)) {
                        isSecure = true;
                    }
                }
                Object cfVisitor = getHeaderMethod.invoke(headers, "CF-Visitor");
                if (cfVisitor != null && cfVisitor.toString().toLowerCase().contains("https")) {
                    isSecure = true;
                }
                Object ssl = getHeaderMethod.invoke(headers, "X-Forwarded-Ssl");
                if (ssl != null && "off".equalsIgnoreCase(ssl.toString().trim())) {
                    isSecure = false;
                }

                String prefix = isSecure ? "wss://" : "ws://";
                if (host.startsWith("ws://") || host.startsWith("wss://") || host.startsWith("http://") || host.startsWith("https://")) {
                    return host;
                }
                return prefix + host;
            }
        } catch (Throwable e) {
            LOGGER.debug("NeoAuth: 從 WebSocket 握手資訊提取 Host 時發生異常: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 從 ClientIntentionPacket 中反射獲取 hostName 欄位值。
     */
    private static String extractPacketHost(Object packet) {
        if (packet == null) return null;
        try {
            // 1.20.2+ / 1.21+: hostName()
            Method m = findMethod(packet.getClass(), "hostName", 0);
            if (m != null) {
                Object res = m.invoke(packet);
                if (res instanceof String s) return s;
            }
        } catch (Throwable ignored) {
        }

        try {
            // 1.20.1: getHostName()
            Method m = findMethod(packet.getClass(), "getHostName", 0);
            if (m != null) {
                Object res = m.invoke(packet);
                if (res instanceof String s) return s;
            }
        } catch (Throwable ignored) {
        }

        try {
            // 直接反射欄位 hostName
            Field f = packet.getClass().getDeclaredField("hostName");
            f.setAccessible(true);
            Object res = f.get(packet);
            if (res instanceof String s) return s;
        } catch (Throwable ignored) {
        }

        return null;
    }

    /**
     * 清理主機名稱字串 (去除 Forge/FML 標記、前後引號、尾隨點號與多餘空白)。
     *
     * @param raw 原始主機名稱字串
     * @return 淨化後的主機名稱字串
     */
    public static String cleanHost(String raw) {
        if (raw == null) {
            return null;
        }
        String host = raw.trim();
        if (host.isEmpty()) {
            return null;
        }

        // 去除 Forge / FML3 在握手主機名稱後面附加的空字元標記 (例如 "mc8.yuaner.tw\0FML3\0")
        if (host.contains("\0")) {
            host = host.split("\0")[0].trim();
        }

        // 去除前後雙引號
        if (host.startsWith("\"") && host.endsWith("\"") && host.length() > 1) {
            host = host.substring(1, host.length() - 1).trim();
        }

        // 去除末尾 DNS 根網域名稱點號 (例如 "mc8.yuaner.tw.")
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1).trim();
        }

        return host;
    }

    private static void setChannelAttribute(Object connection, String host) {
        try {
            Method channelMethod = findMethod(connection.getClass(), "channel", 0);
            if (channelMethod == null) return;
            Object channel = channelMethod.invoke(connection);
            if (channel == null) return;
            Class<?> attrKeyClass = Class.forName("io.netty.util.AttributeKey");
            Method valueOf = attrKeyClass.getMethod("valueOf", String.class);
            Object key = valueOf.invoke(null, "neoauth:server_host");

            Method attrMethod = channel.getClass().getMethod("attr", attrKeyClass);
            Object attr = attrMethod.invoke(channel, key);
            if (attr != null) {
                Method setMethod = attr.getClass().getMethod("set", Object.class);
                setMethod.invoke(attr, host);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String getChannelAttribute(Object connection) {
        try {
            Method channelMethod = findMethod(connection.getClass(), "channel", 0);
            if (channelMethod == null) return null;
            Object channel = channelMethod.invoke(connection);
            if (channel == null) return null;
            Class<?> attrKeyClass = Class.forName("io.netty.util.AttributeKey");
            Method valueOf = attrKeyClass.getMethod("valueOf", String.class);
            Object key = valueOf.invoke(null, "neoauth:server_host");

            Method attrMethod = channel.getClass().getMethod("attr", attrKeyClass);
            Object attr = attrMethod.invoke(channel, key);
            if (attr != null) {
                Method getMethod = attr.getClass().getMethod("get");
                Object val = getMethod.invoke(attr);
                if (val instanceof String s) return s;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                    m.setAccessible(true);
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findHeaderGetMethod(Class<?> clazz) {
        for (Method m : clazz.getMethods()) {
            if ("get".equals(m.getName()) && m.getParameterCount() == 1
                    && (m.getParameterTypes()[0] == String.class || m.getParameterTypes()[0] == CharSequence.class)) {
                return m;
            }
        }
        return null;
    }
}
