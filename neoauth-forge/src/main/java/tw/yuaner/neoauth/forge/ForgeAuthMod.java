package tw.yuaner.neoauth.forge;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import tw.yuaner.neoauth.DatabaseManager;

/**
 * Minecraft 1.20.1 Forge 模組主進入點。
 * <p>
 * 負責註冊 Forge 伺服器設定檔，並監聽伺服器生命週期（啟動時初始化資料庫、關閉時釋放連線池）。
 */
@Mod("neoauth")
public class ForgeAuthMod {

    public static final String MODID = "neoauth";
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

        // 註冊設定檔至 config/neoauth-server.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeConfig.SERVER_SPEC, "neoauth-server.toml");

        // 註冊伺服器生命週期監聽
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * 伺服器啟動完成事件：初始化 MariaDB 資料庫連線池與資料表。
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("NeoAuth: 伺服器啟動中，正在初始化 MariaDB 資料庫連線...");
        DatabaseManager.init();
    }

    /**
     * 伺服器停止事件：關閉資料庫連線池。
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("NeoAuth: 伺服器正在停止，正在關閉資料庫連線池...");
        DatabaseManager.close();
    }
}
