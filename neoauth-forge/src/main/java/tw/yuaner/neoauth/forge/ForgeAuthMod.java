package tw.yuaner.neoauth.forge;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import tw.yuaner.neoauth.DatabaseManager;
import tw.yuaner.neoauth.config.ConfigManager;

/**
 * Minecraft 1.20.1 Forge 模組主進入點。
 * <p>
 * 負責初始化 NeoAuth 設定檔系統，並監聽伺服器生命週期（啟動時初始化資料庫、關閉時釋放連線池）。
 */
@Mod("neoauthreloaded")
public class ForgeAuthMod {

    public static final String MODID = "neoauthreloaded";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ForgeAuthMod() {
        LOGGER.info("NeoAuth (Forge 1.20.1) 模組初始化中...");

        // 註冊為純伺服器端模組，客戶端連線時無需安裝此模組
        ModLoadingContext.get().registerExtensionPoint(
                net.minecraftforge.fml.IExtensionPoint.DisplayTest.class,
                () -> new net.minecraftforge.fml.IExtensionPoint.DisplayTest(
                        () -> net.minecraftforge.fml.IExtensionPoint.DisplayTest.IGNORESERVERONLY,
                        (remoteVersion, isFromServer) -> true
                )
        );

        // 初始化設定檔目錄 (config/neoauth/) 與預設檔案
        ConfigManager.init();

        // 註冊伺服器生命週期監聽
        MinecraftForge.EVENT_BUS.register(this);
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
     * 伺服器已完全就緒事件：所有模組與世界載入完畢，確保 TAB 模組若稍晚載入能自動補註冊變數。
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (!tw.yuaner.neoauth.util.TabIntegration.isRegistered()) {
            LOGGER.info("NeoAuth: 伺服器完全就緒，正在為 TAB 模組補註冊變數...");
            tw.yuaner.neoauth.util.TabIntegration.register();
        }
    }

    /**
     * 玩家成功進入世界事件：確保此時 BlueMap 已完全載入，即時同步或補寫頭像至 BlueMap 儲存庫。
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!tw.yuaner.neoauth.util.TabIntegration.isRegistered()) {
            tw.yuaner.neoauth.util.TabIntegration.register();
        }
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
