package tw.yuaner.neoauth.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * WebSocket 反向代理客戶端真實 IP 解析工具類。
 * <p>
 * 當玩家透過 WebSocket 模組 (例如 WSMC) 連線至伺服器時，
 * 底層 Netty TCP 連線通常來自本機反向代理伺服器 (如 Nginx: 127.0.0.1)，
 * 導致原生 {@code player.getIpAddress()} 無法取得使用者的真實 IP。
 * <p>
 * 本工具類透過純反射方式，從 WSMC 附加於 Connection 上的 WebSocket Handshake Request
 * 解析 HTTP 標頭 (如 CF-Connecting-IP, True-Client-IP, X-Real-IP, X-Forwarded-For)，
 * 萃取出玩家真實 IP，並安全地更新 Connection 物件中的 SocketAddress，
 * 使整個伺服器生態系 (包括其他第三方模組) 均能透明讀取正確的客戶端 IP。
 */
public class WebSocketIpResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-WsIp");

    /**
     * 支援的反向代理客戶端 IP 候選標頭名稱清單（依優先級排列）。
     */
    private static final String[] CANDIDATE_HEADERS = new String[]{
            "CF-Connecting-IP",   // Cloudflare CDN
            "True-Client-IP",     // Cloudflare Enterprise / Akamai
            "X-Real-IP",          // Nginx 反向代理
            "X-Forwarded-For",    // 標準代理鏈（取最左側真實客戶端 IP）
            "X-Client-IP",        // 部分反向代理伺服器
            "Forwarded"           // RFC 7239 標準轉發標頭
    };

    /**
     * 依據 Connection 實例與預設回退 IP 解析玩家真實 IP。
     *
     * @param connection net.minecraft.network.Connection 實例 (型別 Object 以相容 core)
     * @param fallbackIp 原生 socket 所回傳的 IP（通常為 player.getIpAddress()）
     * @return 解析後的有效真實 IP；若無反向代理標頭則回傳 fallbackIp，均無效時回傳 "127.0.0.1"
     */
    public static String resolveIp(Object connection, String fallbackIp) {
        String wsIp = extractWsClientIp(connection);
        if (wsIp != null && !wsIp.isBlank()) {
            updateConnectionAddress(connection, wsIp);
            return wsIp;
        }

        if (fallbackIp != null && !fallbackIp.isBlank()) {
            String cleaned = cleanIp(fallbackIp);
            if (isIpAddress(cleaned)) {
                return cleaned;
            }
        }

        return "127.0.0.1";
    }

    /**
     * 依據玩家實體物件 (ServerPlayer) 解析真實連線 IP。
     *
     * @param playerObj 伺服器玩家物件 (ServerPlayer)
     * @return 解析後的真實 IP 字串
     */
    public static String resolvePlayerIp(Object playerObj) {
        if (playerObj == null) {
            return "127.0.0.1";
        }

        Object connection = extractConnection(playerObj);
        String fallbackIp = extractPlayerIpAddress(playerObj);
        return resolveIp(connection, fallbackIp);
    }

    /**
     * 從 Connection 實例中提取 WebSocket 握手請求並解析真實客戶端 IP。
     *
     * @param connection net.minecraft.network.Connection 物件
     * @return 解析到的真實 IP，若未找到或非 WebSocket 連線則回傳 null
     */
    public static String extractWsClientIp(Object connection) {
        if (connection == null) {
            return null;
        }

        try {
            tw.yuaner.neoauth.config.IAuthConfig config = tw.yuaner.neoauth.config.ConfigManager.getInstance().getConfig();
            if (config != null && !config.isWsmcIntegrationEnabled()) {
                return null;
            }

            // 尋找 WSMC 注入於 Connection 之握手請求方法: getWsHandshakeRequest()
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

            // 取得 Netty HttpRequest 中的 headers()
            Method headersMethod = findMethod(req.getClass(), "headers", 0);
            if (headersMethod == null) {
                return null;
            }

            Object headers = headersMethod.invoke(req);
            if (headers == null) {
                return null;
            }

            // 取得 HttpHeaders.get(String) 方法
            Method getHeaderMethod = findHeaderGetMethod(headers.getClass());
            if (getHeaderMethod == null) {
                return null;
            }

            for (String headerName : CANDIDATE_HEADERS) {
                Object val = getHeaderMethod.invoke(headers, headerName);
                if (val != null) {
                    String strVal = val.toString().trim();
                    if (!strVal.isEmpty() && !"unknown".equalsIgnoreCase(strVal)) {
                        if ("Forwarded".equalsIgnoreCase(headerName)) {
                            String parsed = parseForwardedHeader(strVal);
                            if (parsed != null && isIpAddress(parsed)) {
                                return parsed;
                            }
                        } else {
                            // X-Forwarded-For 可能為逗號分隔的 IP 列表，最左邊為原始客戶端
                            String firstIp = strVal.split(",")[0].trim();
                            firstIp = cleanIp(firstIp);
                            if (firstIp != null && isIpAddress(firstIp)) {
                                return firstIp;
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LOGGER.debug("NeoAuth: 從 WebSocket 握手資訊解析真實 IP 時發生異常: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 嘗試更新 Connection 實例內部的 SocketAddress address 屬性，
     * 讓 Minecraft 原生 getRemoteAddress() 及其他模組透明取得真實 IP。
     *
     * @param connection net.minecraft.network.Connection 物件
     * @param realIp     解析出的真實客戶端 IP
     */
    public static void updateConnectionAddress(Object connection, String realIp) {
        if (connection == null || realIp == null || !isIpAddress(realIp)) {
            return;
        }

        try {
            tw.yuaner.neoauth.config.IAuthConfig config = tw.yuaner.neoauth.config.ConfigManager.getInstance().getConfig();
            if (config != null && !config.isWsmcIntegrationEnabled()) {
                return;
            }

            InetAddress inetAddr = InetAddress.getByName(realIp);
            int port = 25565;

            // 嘗試取得既有的 RemoteAddress port
            try {
                Method getRemote = findMethod(connection.getClass(), "getRemoteAddress", 0);
                if (getRemote != null) {
                    Object remote = getRemote.invoke(connection);
                    if (remote instanceof InetSocketAddress inetSocket) {
                        int p = inetSocket.getPort();
                        if (p > 0) {
                            port = p;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            InetSocketAddress newSocketAddr = new InetSocketAddress(inetAddr, port);

            // 遍歷 Connection 類別階層，找到型別為 SocketAddress 的欄位進行替換
            Class<?> clazz = connection.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (SocketAddress.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        f.set(connection, newSocketAddr);
                        return;
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable e) {
            LOGGER.debug("NeoAuth: 更新 Connection SocketAddress 失敗 (非致命): {}", e.getMessage());
        }
    }

    /**
     * 從 ServerPlayer 實例中安全提取底層 Connection 物件。
     *
     * @param playerObj ServerPlayer 實例
     * @return Connection 物件，失敗時回傳 null
     */
    public static Object extractConnection(Object playerObj) {
        if (playerObj == null) {
            return null;
        }

        try {
            Object listener = null;

            // 嘗試存取 player.connection (ServerGamePacketListenerImpl)
            try {
                Field f = playerObj.getClass().getField("connection");
                listener = f.get(playerObj);
            } catch (Throwable ignored) {
            }

            if (listener == null) {
                for (Field f : playerObj.getClass().getFields()) {
                    if ("connection".equalsIgnoreCase(f.getName())) {
                        listener = f.get(playerObj);
                        break;
                    }
                }
            }

            if (listener == null) {
                for (Field f : playerObj.getClass().getDeclaredFields()) {
                    if ("connection".equalsIgnoreCase(f.getName())) {
                        f.setAccessible(true);
                        listener = f.get(playerObj);
                        break;
                    }
                }
            }

            if (listener == null) {
                return null;
            }

            // 1. NeoForge / 1.20.2+: getConnection()
            try {
                Method m = findMethod(listener.getClass(), "getConnection", 0);
                if (m != null) {
                    return m.invoke(listener);
                }
            } catch (Throwable ignored) {
            }

            // 2. Forge 1.20.1: connection 欄位
            try {
                Field f = listener.getClass().getField("connection");
                return f.get(listener);
            } catch (Throwable ignored) {
            }

            for (Field f : listener.getClass().getDeclaredFields()) {
                if ("connection".equalsIgnoreCase(f.getName())) {
                    f.setAccessible(true);
                    return f.get(listener);
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    /**
     * 呼叫 ServerPlayer.getIpAddress() 取得原生 IP 字串。
     */
    private static String extractPlayerIpAddress(Object playerObj) {
        if (playerObj == null) {
            return null;
        }
        try {
            Method m = findMethod(playerObj.getClass(), "getIpAddress", 0);
            if (m != null) {
                Object res = m.invoke(playerObj);
                if (res instanceof String s) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 清理 IP 字串（去除首尾空格、引號、外圍中括號以及連接埠）。
     *
     * @param raw 原始 IP 字串
     * @return 淨化後的純 IP 字串，無效時回傳 null
     */
    public static String cleanIp(String raw) {
        if (raw == null) {
            return null;
        }
        raw = raw.trim();
        if (raw.isEmpty()) {
            return null;
        }

        // 去除前後引號，例如 "1.2.3.4"
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() > 1) {
            raw = raw.substring(1, raw.length() - 1).trim();
        }

        // 處理包含中括號之 IPv6: [2001:db8::1] 或 [2001:db8::1]:port
        if (raw.startsWith("[")) {
            int endBracket = raw.indexOf(']');
            if (endBracket != -1) {
                return raw.substring(1, endBracket).trim();
            }
        }

        // 處理帶有埠號之 IPv4: 1.2.3.4:5678 (僅有單一冒號且包含小數點)
        int firstColon = raw.indexOf(':');
        int lastColon = raw.lastIndexOf(':');
        if (firstColon != -1 && firstColon == lastColon && raw.indexOf('.') != -1) {
            return raw.substring(0, firstColon).trim();
        }

        return raw;
    }

    /**
     * 驗證給定字串是否符合標準 IPv4 或 IPv6 格式（無 DNS 阻塞查詢）。
     *
     * @param ip 待檢查字串
     * @return true 若為有效之 IPv4 或 IPv6 位址
     */
    public static boolean isIpAddress(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return false;
        }

        // IPv4 檢查
        if (ip.indexOf('.') != -1) {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return false;
            }
            for (String part : parts) {
                try {
                    int n = Integer.parseInt(part);
                    if (n < 0 || n > 255) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }

        // IPv6 檢查 (僅包含十六進位字元與冒號)
        if (ip.indexOf(':') != -1) {
            for (int i = 0; i < ip.length(); i++) {
                char c = ip.charAt(i);
                if (c != ':' && Character.digit(c, 16) == -1) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    /**
     * 解析 RFC 7239 Forwarded 標頭中的 for= 欄位。
     */
    public static String parseForwardedHeader(String header) {
        if (header == null) {
            return null;
        }
        for (String part : header.split(";")) {
            part = part.trim();
            if (part.toLowerCase().startsWith("for=")) {
                String val = part.substring(4).trim();
                if (val.startsWith("\"") && val.endsWith("\"") && val.length() > 1) {
                    val = val.substring(1, val.length() - 1).trim();
                }
                return cleanIp(val);
            }
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
