package tw.yuaner.neoauth;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;

@Mod(NeoAuth.MODID)
public class NeoAuth {
    public static final String MODID = "neoauth";
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoAuth(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("NeoAuth initializing...");

        // Register Config
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);

        // Register ourselves for server and other game events we are interested in
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("NeoAuth: Server starting, initializing database...");
        DatabaseManager.init();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("NeoAuth: Server stopping, closing database...");
        DatabaseManager.close();
    }
}
