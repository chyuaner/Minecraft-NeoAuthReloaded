package tw.yuaner.neoauth.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.config.MessagesManager;
import tw.yuaner.neoauth.core.PlayerSessionData;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * TAB by NEZNAMY 模組整合管理工具類。
 * <p>
 * 透過純反射安全介接 TAB 模組的 {@code TabAPI}，在完全不依賴外部編譯依賴的前提下，
 * 向 TAB 註冊 NeoAuth 專屬變數（如登入方式、IPv4/IPv6 連線線路、當前登入時間、綁定信箱）。
 * 同時監聽 TAB 的 {@code TabLoadEvent}，確保在遊戲內執行 {@code /tab reload} 時能自動重新註冊變數。
 */
public class TabIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-TAB");
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean LISTENER_REGISTERED = new AtomicBoolean(false);

    /**
     * 檢查目前執行環境是否已載入 TAB 模組 API。
     *
     * @return true 若 TAB API 可用，否則為 false
     */
    public static boolean isTabAvailable() {
        try {
            Class.forName("me.neznamy.tab.api.TabAPI");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 向 TAB 模組註冊 NeoAuth 專屬變數與生命週期監聽器。
     */
    public static void register() {
        IAuthConfig config = ConfigManager.getInstance().getConfig();
        if (config != null && !config.isTabIntegrationEnabled()) {
            LOGGER.debug("NeoAuth: TAB 連動功能已在 config.yml 中停用。");
            return;
        }

        if (!isTabAvailable()) {
            LOGGER.debug("NeoAuth: 未偵測到 TAB 模組，跳過 TAB 變數註冊。");
            return;
        }

        try {
            Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Method getInstanceMethod = tabApiClass.getMethod("getInstance");
            Object tabApi = getInstanceMethod.invoke(null);
            if (tabApi == null) {
                LOGGER.debug("NeoAuth: TabAPI.getInstance() 回傳 null，TAB 模組可能尚未完全初始化。");
                return;
            }

            registerPlaceholders(tabApi);
            registerReloadListener(tabApi);
            REGISTERED.set(true);
            LOGGER.info("NeoAuth: 成功向 TAB 模組註冊 NeoAuth 專屬變數！");
        } catch (Throwable t) {
            LOGGER.warn("NeoAuth: 向 TAB 模組註冊變數時發生異常: {}", t.getMessage());
        }
    }

    /**
     * 執行實際的變數註冊邏輯。
     */
    private static void registerPlaceholders(Object tabApi) {
        try {
            Method getPlaceholderManagerMethod = tabApi.getClass().getMethod("getPlaceholderManager");
            Object placeholderManager = getPlaceholderManagerMethod.invoke(tabApi);
            if (placeholderManager == null) return;

            Method registerPlayerPlaceholder = placeholderManager.getClass().getMethod(
                    "registerPlayerPlaceholder", String.class, int.class, Function.class
            );

            // 1. %neoauth_login_type% (每 1000ms 刷新)
            registerPlayerPlaceholder.invoke(placeholderManager, "%neoauth_login_type%", 1000,
                    (Function<Object, Object>) tabPlayer -> getLoginType(extractPlayerUuid(tabPlayer)));

            // 2. %neoauth_ip_type% (每 1000ms 刷新)
            registerPlayerPlaceholder.invoke(placeholderManager, "%neoauth_ip_type%", 1000,
                    (Function<Object, Object>) tabPlayer -> getIpType(extractPlayerUuid(tabPlayer)));

            // 3. %neoauth_login_time% (每 1000ms 刷新)
            registerPlayerPlaceholder.invoke(placeholderManager, "%neoauth_login_time%", 1000,
                    (Function<Object, Object>) tabPlayer -> getLoginTime(extractPlayerUuid(tabPlayer)));

            // 4. %neoauth_email% (每 1000ms 刷新)
            registerPlayerPlaceholder.invoke(placeholderManager, "%neoauth_email%", 1000,
                    (Function<Object, Object>) tabPlayer -> getEmail(extractPlayerUuid(tabPlayer)));

            // 5. %neoauth_player% (靜態不刷新 -1)
            registerPlayerPlaceholder.invoke(placeholderManager, "%neoauth_player%", -1,
                    (Function<Object, Object>) tabPlayer -> getPlayerName(tabPlayer));

        } catch (Throwable t) {
            LOGGER.warn("NeoAuth: 註冊 TAB 變數時呼叫 PlaceholderManager 失敗: {}", t.getMessage());
        }
    }

    /**
     * 監聽 TAB 的 TabLoadEvent，確保 /tab reload 後能自動恢復變數註冊。
     */
    private static void registerReloadListener(Object tabApi) {
        if (LISTENER_REGISTERED.get()) return;
        try {
            Method getEventBusMethod = tabApi.getClass().getMethod("getEventBus");
            Object eventBus = getEventBusMethod.invoke(tabApi);
            if (eventBus == null) return;

            Class<?> tabLoadEventClass = null;
            try {
                tabLoadEventClass = Class.forName("me.neznamy.tab.api.event.plugin.TabLoadEvent");
            } catch (ClassNotFoundException e) {
                try {
                    tabLoadEventClass = Class.forName("me.neznamy.tab.api.event.TabLoadEvent");
                } catch (ClassNotFoundException ignored) {}
            }

            if (tabLoadEventClass != null) {
                Method registerMethod = eventBus.getClass().getMethod("register", Class.class, Consumer.class);
                registerMethod.invoke(eventBus, tabLoadEventClass, (Consumer<Object>) event -> {
                    LOGGER.info("NeoAuth: 偵測到 TAB 重新載入，正在自動重新註冊 NeoAuth 變數...");
                    try {
                        Method getInstanceMethod = tabApi.getClass().getMethod("getInstance");
                        Object latestApi = getInstanceMethod.invoke(null);
                        if (latestApi != null) {
                            registerPlaceholders(latestApi);
                        }
                    } catch (Exception ex) {
                        LOGGER.warn("NeoAuth: 重新註冊 TAB 變數失敗: {}", ex.getMessage());
                    }
                });
                LISTENER_REGISTERED.set(true);
            }
        } catch (Throwable t) {
            LOGGER.debug("NeoAuth: 註冊 TAB reload 監聽器時略過: {}", t.getMessage());
        }
    }

    /**
     * 登出或伺服器關閉時呼叫。
     */
    public static void unregister() {
        REGISTERED.set(false);
    }

    /**
     * 從 TabPlayer 反射提取玩家 UUID。
     */
    public static UUID extractPlayerUuid(Object tabPlayer) {
        if (tabPlayer == null) return null;
        try {
            Method getUniqueId = tabPlayer.getClass().getMethod("getUniqueId");
            return (UUID) getUniqueId.invoke(tabPlayer);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 從 TabPlayer 反射提取玩家名稱。
     */
    public static String extractPlayerName(Object tabPlayer) {
        if (tabPlayer == null) return "";
        try {
            Method getName = tabPlayer.getClass().getMethod("getName");
            return (String) getName.invoke(tabPlayer);
        } catch (Exception ignored) {
            return "";
        }
    }

    // ==========================================
    // 變數內容計算邏輯 (公開以便單元測試)
    // ==========================================

    /**
     * 計算 %neoauth_login_type% 顯示文字。
     */
    public static String getLoginType(UUID uuid) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (uuid == null || !AuthManager.isLoggedIn(uuid)) {
            return msgMgr.get("tab.login_type_unlogged");
        }
        PlayerSessionData session = AuthManager.getSession(uuid);
        boolean isPremium = (session != null && session.isPremium()) || AuthManager.isPremiumVerified(uuid);
        return isPremium ? msgMgr.get("tab.login_type_premium") : msgMgr.get("tab.login_type_password");
    }

    /**
     * 計算 %neoauth_ip_type% 顯示文字 (IPv6 / IPv4)。
     */
    public static String getIpType(UUID uuid) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (uuid == null) {
            return msgMgr.get("tab.ip_type_unknown");
        }
        PlayerSessionData session = AuthManager.getSession(uuid);
        if (session == null || session.getIp() == null || session.getIp().isBlank()) {
            return msgMgr.get("tab.ip_type_unknown");
        }
        return session.isIpv6() ? msgMgr.get("tab.ip_type_ipv6") : msgMgr.get("tab.ip_type_ipv4");
    }

    /**
     * 計算 %neoauth_login_time% 顯示文字 (當前登入時間)。
     */
    public static String getLoginTime(UUID uuid) {
        if (uuid == null || !AuthManager.isLoggedIn(uuid)) {
            return "-";
        }
        PlayerSessionData session = AuthManager.getSession(uuid);
        if (session == null || session.getLoginTime() <= 0) {
            return "-";
        }
        IAuthConfig config = ConfigManager.getInstance().getConfig();
        String pattern = config != null && config.getTabDateFormat() != null && !config.getTabDateFormat().isBlank()
                ? config.getTabDateFormat().trim()
                : "yyyy-MM-dd HH:mm:ss";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern);
            return sdf.format(new Date(session.getLoginTime()));
        } catch (Exception e) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(session.getLoginTime()));
        }
    }

    /**
     * 計算 %neoauth_email% 顯示文字。
     */
    public static String getEmail(UUID uuid) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (uuid == null) {
            return msgMgr.get("tab.email_none");
        }
        PlayerSessionData session = AuthManager.getSession(uuid);
        if (session != null && session.getEmail() != null && !session.getEmail().isBlank()) {
            return session.getEmail();
        }
        return msgMgr.get("tab.email_none");
    }

    /**
     * 計算 %neoauth_player% 顯示文字。
     */
    public static String getPlayerName(Object tabPlayer) {
        UUID uuid = extractPlayerUuid(tabPlayer);
        if (uuid != null) {
            PlayerSessionData session = AuthManager.getSession(uuid);
            if (session != null && session.getUsername() != null) {
                return session.getUsername();
            }
        }
        return extractPlayerName(tabPlayer);
    }
}
