package tw.yuaner.neoauth;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家驗證與登入狀態管理中心。
 * <p>
 * 本類別採用執行緒安全的集合（{@link ConcurrentHashMap#newKeySet()}）來維護：
 * <ul>
 *   <li>已成功登入（或已註冊登入）的玩家 UUID 清單。</li>
 *   <li>已通過 Mojang 官方伺服器線上驗證的正版玩家 UUID 清單。</li>
 * </ul>
 * 同時提供標準的 Minecraft 離線模式 UUID 計算與比對邏輯。
 */
public class AuthManager {

    /**
     * 儲存目前已通過身分驗證並登入的玩家 UUID 集合。
     */
    private static final Set<UUID> LOGGED_IN_PLAYERS = ConcurrentHashMap.newKeySet();

    /**
     * 儲存已通過 Mojang 官方驗證（具備官方 Skin / 特性）的正版玩家 UUID 集合。
     */
    private static final Set<UUID> PREMIUM_VERIFIED = ConcurrentHashMap.newKeySet();

    /**
     * 檢查指定 UUID 的玩家是否已經完成登入。
     *
     * @param uuid 玩家 UUID
     * @return true 若該玩家已登入，否則為 false
     */
    public static boolean isLoggedIn(UUID uuid) {
        if (uuid == null) return false;
        return LOGGED_IN_PLAYERS.contains(uuid);
    }

    /**
     * 將指定 UUID 的玩家標記為已登入狀態。
     *
     * @param uuid 玩家 UUID
     */
    public static void setLoggedIn(UUID uuid) {
        if (uuid != null) {
            LOGGED_IN_PLAYERS.add(uuid);
        }
    }

    /**
     * 將指定 UUID 的玩家標記為未登入狀態（登出或斷線時呼叫）。
     *
     * @param uuid 玩家 UUID
     */
    public static void setLoggedOut(UUID uuid) {
        if (uuid != null) {
            LOGGED_IN_PLAYERS.remove(uuid);
        }
    }

    /**
     * 將指定 UUID 的玩家標記為通過 Mojang 官方線上驗證。
     *
     * @param uuid 玩家 UUID
     */
    public static void markPremiumVerified(UUID uuid) {
        if (uuid != null) {
            PREMIUM_VERIFIED.add(uuid);
        }
    }

    /**
     * 檢查指定 UUID 的玩家是否為通過 Mojang 官方線上驗證的正版玩家。
     *
     * @param uuid 玩家 UUID
     * @return true 若通過 Mojang 官方驗證，否則為 false
     */
    public static boolean isPremiumVerified(UUID uuid) {
        return uuid != null && PREMIUM_VERIFIED.contains(uuid);
    }

    /**
     * 清除指定 UUID 的 Mojang 正版驗證狀態（玩家離線時呼叫）。
     *
     * @param uuid 玩家 UUID
     */
    public static void clearPremiumVerified(UUID uuid) {
        if (uuid != null) {
            PREMIUM_VERIFIED.remove(uuid);
        }
    }

    /**
     * 根據玩家名稱與目前 UUID，判斷是否為 Minecraft 離線模式（Cracked）所生成的 UUID。
     * <p>
     * 官方離線 UUID 生成演算法為：{@code UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(UTF_8))}。
     *
     * @param username 玩家名稱
     * @param uuid     玩家目前的 UUID
     * @return true 若目前的 UUID 與離線演算法計算出的 UUID 相同
     */
    public static boolean isOfflineUuid(String username, UUID uuid) {
        if (username == null || uuid == null) return false;
        UUID expectedOfflineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        return expectedOfflineUuid.equals(uuid);
    }
}
