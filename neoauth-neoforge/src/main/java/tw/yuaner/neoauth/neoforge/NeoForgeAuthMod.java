package tw.yuaner.neoauth.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import tw.yuaner.neoauth.DatabaseManager;
import tw.yuaner.neoauth.config.ConfigManager;

/**
 * Minecraft 1.21.1 NeoForge 模組主進入點。
 * <p>
 * 負責初始化 NeoAuth 設定檔系統，並監聽伺服器生命週期（啟動時初始化資料庫、關閉時釋放連線池）。
 */
@Mod("neoauthreloaded")
public class NeoForgeAuthMod {

    public static final String MODID = "neoauthreloaded";
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeAuthMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("NeoAuth (NeoForge 1.21.1) 模組初始化中...");

        // 初始化設定檔目錄 (config/neoauth/) 與預設檔案
        ConfigManager.init();

        // 註冊伺服器生命週期監聽
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * 伺服器啟動完成事件：初始化資料庫連線池與資料表。
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("NeoAuth: 伺服器啟動中，正在初始化資料庫連線...");
        DatabaseManager.init();
        tw.yuaner.neoauth.util.TabIntegration.register();
    }

    /**
     * 玩家成功進入世界事件：確保此時 BlueMap 已完全載入，即時同步或補寫頭像至 BlueMap 儲存庫。
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() != null) {
            String username = event.getEntity().getGameProfile().getName();
            java.util.UUID offlineUuid = event.getEntity().getUUID();
            tw.yuaner.neoauth.util.BlueMapIntegration.syncBlueMapPlayerHead(username, offlineUuid);
        }
    }

    /**
     * 伺服器停止事件：關閉資料庫連線池與背景執行緒池。
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("NeoAuth: 伺服器正在停止，正在關閉資料庫連線池與背景執行緒...");
        tw.yuaner.neoauth.util.TabIntegration.unregister();
        DatabaseManager.close();
        tw.yuaner.neoauth.util.BlueMapIntegration.shutdown();
    }
}
