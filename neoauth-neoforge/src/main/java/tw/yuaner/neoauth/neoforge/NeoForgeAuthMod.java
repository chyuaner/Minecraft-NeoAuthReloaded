package tw.yuaner.neoauth.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import tw.yuaner.neoauth.DatabaseManager;

/**
 * Minecraft 1.21.1 NeoForge 模組主進入點。
 * <p>
 * 負責註冊 NeoForge 伺服器設定檔，並監聽伺服器生命週期（啟動時初始化資料庫、關閉時釋放連線池）。
 */
@Mod("neoauth")
public class NeoForgeAuthMod {

    public static final String MODID = "neoauth";
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeAuthMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("NeoAuth (NeoForge 1.21.1) 模組初始化中...");

        // 註冊設定檔至 config/neoauth-server.toml
        modContainer.registerConfig(ModConfig.Type.COMMON, NeoForgeConfig.SERVER_SPEC, "neoauth-server.toml");

        // 註冊伺服器生命週期監聽
        NeoForge.EVENT_BUS.register(this);
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
