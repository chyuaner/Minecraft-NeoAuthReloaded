package tw.yuaner.neoauth.platform;

import tw.yuaner.neoauth.config.IAuthConfig;

/**
 * 平台服務抽象介面。
 * <p>
 * 封裝不同 Minecraft 版本 (1.20.1 vs 1.21.1) 及不同 Mod Loader (Forge vs NeoForge)
 * 之間的 API 差異（如藥水效果給予/移除、訊息發送、設定檔實例取得等）。
 */
public interface IPlatformHelper {

    /**
     * 取得目前平台的設定檔實作。
     *
     * @return {@link IAuthConfig} 設定檔實例
     */
    IAuthConfig getConfig();

    /**
     * 取得目前載入器平台名稱 (例如: "Forge" 或 "NeoForge")。
     *
     * @return 平台名稱字串
     */
    String getPlatformName();

    /**
     * 對尚未登入的玩家施加凍結防護效果（緩速、跳躍抑制、失明）。
     * <p>
     * 在 1.20.1 與 1.21.1 中，MobEffects 的型別定義有所不同 (MobEffect vs Holder&lt;MobEffect&gt;)，
     * 因此透過此抽象方法交由具體平台實作。
     *
     * @param player 伺服器玩家物件 (ServerPlayer)
     */
    void applyFreezeEffects(Object player);

    /**
     * 移除玩家身上的凍結防護效果（登入成功或斷線後呼叫）。
     *
     * @param player 伺服器玩家物件 (ServerPlayer)
     */
    void removeFreezeEffects(Object player);

    /**
     * 向玩家發送一般的系統提示訊息（顯示於聊天欄）。
     *
     * @param player  伺服器玩家物件 (ServerPlayer)
     * @param message 訊息內容（支援樣式字元如 §a, §c）
     */
    void sendMessage(Object player, String message);

    /**
     * 向玩家發送 Action Bar 提示訊息（顯示於快捷欄上方）。
     *
     * @param player  伺服器玩家物件 (ServerPlayer)
     * @param message 訊息內容
     */
    void sendActionBar(Object player, String message);

    /**
     * 將指定玩家傳送至特定世界維度與座標。
     *
     * @param player    伺服器玩家物件 (ServerPlayer)
     * @param worldName 維度名稱 (例如 "minecraft:overworld")
     * @param x         X 座標
     * @param y         Y 座標
     * @param z         Z 座標
     * @param yaw       偏航角
     * @param pitch     俯仰角
     * @return true 若傳送成功，否則為 false
     */
    boolean teleportPlayer(Object player, String worldName, double x, double y, double z, float yaw, float pitch);

    /**
     * 由伺服器後台 (Console) 權限執行指令。
     *
     * @param serverOrSource 伺服器實例或指令來源
     * @param command        欲執行的指令 (不含前導斜線)
     */
    void executeConsoleCommand(Object serverOrSource, String command);

    /**
     * 以指定玩家的身分權限執行指令。
     *
     * @param player  伺服器玩家物件 (ServerPlayer)
     * @param command 欲執行的指令 (不含前導斜線)
     */
    void executePlayerCommand(Object player, String command);

    /**
     * 依玩家名稱查詢目前在線上的玩家物件。
     *
     * @param serverOrSource 伺服器實例或指令來源
     * @param username       玩家名稱
     * @return 伺服器玩家物件 (ServerPlayer)，若不在線則回傳 null
     */
    Object getOnlinePlayer(Object serverOrSource, String username);

    /**
     * 取得目前所有在線玩家的名稱清單。
     *
     * @param serverOrSource 伺服器實例或指令來源
     * @return 玩家名稱清單
     */
    java.util.List<String> getOnlinePlayerNames(Object serverOrSource);
}
