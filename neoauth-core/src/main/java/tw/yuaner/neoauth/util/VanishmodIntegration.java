package tw.yuaner.neoauth.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vanishmod 整合管理工具類。
 * <p>
 * 透過純反射安全介接 Vanishmod API，在不依賴外部編譯依賴的前提下，
 * 處理玩家的隱形狀態。
 */
public class VanishmodIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-Vanish");
    private static final AtomicBoolean AVAILABLE = new AtomicBoolean(false);
    private static final AtomicBoolean CHECKED = new AtomicBoolean(false);
    private static final Set<UUID> FORCED_VANISHED = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /** 靜默隱形操作中的玩家 UUID 集合：當 NeoAuth 正在呼叫 vanish/unvanish 時暫時加入，
     *  以便讓 Mixin 判斷並抑制 ActionBar 等副作用訊息。*/
    private static final Set<UUID> SILENTLY_VANISHING = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Path dataFilePath = null;

    private static Class<?> vanishUtilClass = null;
    private static Class<?> vanishingHandlerClass = null;
    private static Method isVanishedMethod = null;
    private static Method updateVanishedStatusMethod = null;
    private static Method sendPacketsOnVanishMethod = null;
    private static Method getServerLevelMethod = null;
    private static java.util.Set<UUID> vanishedPlayersSet = null;

    /**
     * 檢查目前執行環境是否已載入 Vanishmod。
     *
     * @return true 若 Vanishmod API 可用，否則為 false
     */
    @SuppressWarnings("unchecked")
    public static boolean isVanishmodAvailable() {
        if (CHECKED.get()) {
            return AVAILABLE.get();
        }
        
        try {
            vanishUtilClass = Class.forName("redstonedubstep.mods.vanishmod.VanishUtil");
            vanishingHandlerClass = Class.forName("redstonedubstep.mods.vanishmod.VanishingHandler");
            
            // net.minecraft.world.entity.player.Player 或 net.minecraft.server.level.ServerPlayer
            Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            
            isVanishedMethod = vanishUtilClass.getMethod("isVanished", Object.class);
            updateVanishedStatusMethod = vanishingHandlerClass.getMethod("updateVanishedStatus", serverPlayerClass, boolean.class);
            sendPacketsOnVanishMethod = vanishingHandlerClass.getMethod("sendPacketsOnVanish", serverPlayerClass, serverLevelClass, boolean.class);
            
            // ServerPlayer.serverLevel() 
            getServerLevelMethod = serverPlayerClass.getMethod("serverLevel");

            java.lang.reflect.Field vanishedPlayersField = vanishUtilClass.getField("VANISHED_PLAYERS");
            vanishedPlayersSet = (java.util.Set<UUID>) vanishedPlayersField.get(null);

            AVAILABLE.set(true);
            LOGGER.info("NeoAuth: 偵測到 Vanishmod 模組，已啟用隱形整合功能。");
        } catch (Throwable t) {
            AVAILABLE.set(false);
            LOGGER.debug("NeoAuth: 未偵測到 Vanishmod 模組，將不啟用隱形整合功能。");
        } finally {
            CHECKED.set(true);
        }
        
        return AVAILABLE.get();
    }

    /**
     * 檢查指定玩家是否處於隱形狀態。
     * 
     * @param serverPlayer net.minecraft.server.level.ServerPlayer 的實例
     * @return 若玩家隱形回傳 true，否則回傳 false
     */
    public static boolean isVanished(Object serverPlayer) {
        if (!isVanishmodAvailable() || serverPlayer == null) return false;
        try {
            return (boolean) isVanishedMethod.invoke(null, serverPlayer);
        } catch (Throwable t) {
            LOGGER.warn("NeoAuth: 呼叫 Vanishmod 檢查隱形狀態失敗: {}", t.getMessage());
            return false;
        }
    }

    /**
     * 初始化資料檔案路徑並讀取先前紀錄。
     */
    public static void init(Path dataDir) {
        dataFilePath = dataDir.resolve("vanished.dat");
        if (Files.exists(dataFilePath)) {
            try {
                for (String line : Files.readAllLines(dataFilePath)) {
                    if (!line.isBlank()) {
                        FORCED_VANISHED.add(UUID.fromString(line.trim()));
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("NeoAuth: 讀取隱形玩家紀錄失敗: {}", e.getMessage());
            }
        }
    }

    private static void saveData() {
        if (dataFilePath == null) return;
        try {
            Files.write(dataFilePath, FORCED_VANISHED.stream().map(UUID::toString).toList());
        } catch (Exception e) {
            LOGGER.warn("NeoAuth: 儲存隱形玩家紀錄失敗: {}", e.getMessage());
        }
    }

    public static void addForcedVanished(UUID uuid) {
        if (uuid != null) {
            FORCED_VANISHED.add(uuid);
            saveData();
        }
    }

    public static void removeForcedVanished(UUID uuid) {
        if (uuid != null) {
            FORCED_VANISHED.remove(uuid);
            saveData();
        }
    }

    public static boolean hasForcedVanished(UUID uuid) {
        return uuid != null && FORCED_VANISHED.contains(uuid);
    }

    /**
     * 設定玩家隱形（靜默模式，不會觸發 ActionBar 等副作用訊息）。
     *
     * @param serverPlayer net.minecraft.server.level.ServerPlayer 的實例
     */
    public static void vanishPlayer(Object serverPlayer) {
        if (!isVanishmodAvailable() || serverPlayer == null) return;
        UUID uuid = getUUID(serverPlayer);
        try {
            if (uuid != null) SILENTLY_VANISHING.add(uuid);
            updateVanishedStatusMethod.invoke(null, serverPlayer, true);
            Object serverLevel = getServerLevelMethod.invoke(serverPlayer);
            sendPacketsOnVanishMethod.invoke(null, serverPlayer, serverLevel, true);
        } catch (Throwable t) {
            LOGGER.warn("NeoAuth: 呼叫 Vanishmod 設定隱形失敗: {}", t.getMessage());
        } finally {
            if (uuid != null) SILENTLY_VANISHING.remove(uuid);
        }
    }

    /**
     * 解除玩家隱形（靜默模式，不會觸發 ActionBar 等副作用訊息）。
     *
     * @param serverPlayer net.minecraft.server.level.ServerPlayer 的實例
     */
    public static void unvanishPlayer(Object serverPlayer) {
        if (!isVanishmodAvailable() || serverPlayer == null) return;
        UUID uuid = getUUID(serverPlayer);
        try {
            if (uuid != null) SILENTLY_VANISHING.add(uuid);
            updateVanishedStatusMethod.invoke(null, serverPlayer, false);
            Object serverLevel = getServerLevelMethod.invoke(serverPlayer);
            sendPacketsOnVanishMethod.invoke(null, serverPlayer, serverLevel, false);
        } catch (Throwable t) {
            LOGGER.warn("NeoAuth: 呼叫 Vanishmod 解除隱形失敗: {}", t.getMessage());
        } finally {
            if (uuid != null) SILENTLY_VANISHING.remove(uuid);
        }
    }

    /**
     * 判斷指定 UUID 的玩家是否正在被 NeoAuth 靜默隱形/解除中（供 Mixin 抑制 ActionBar 封包）。
     */
    public static boolean isSilentlyVanishing(UUID uuid) {
        return uuid != null && SILENTLY_VANISHING.contains(uuid);
    }

    /** 取得 ServerPlayer 的 UUID（反射）。*/
    private static UUID getUUID(Object serverPlayer) {
        try {
            return (UUID) serverPlayer.getClass().getMethod("getUUID").invoke(serverPlayer);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 靜默修改 VANISHED_PLAYERS 集合，避免觸發廣播或封包。
     * 主要用於閃避 Vanishmod 在 PlayerLoggedInEvent (NORMAL) 發送的聊天提示。
     */
    public static void setSilentlyVanished(UUID uuid, boolean vanished) {
        if (!isVanishmodAvailable() || vanishedPlayersSet == null || uuid == null) return;
        if (vanished) {
            vanishedPlayersSet.add(uuid);
        } else {
            vanishedPlayersSet.remove(uuid);
        }
    }
}
