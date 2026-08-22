package tw.yuaner.neoauth.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.core.AuthLogic;
import tw.yuaner.neoauth.platform.Services;

import java.util.UUID;

/**
 * Minecraft 1.20.1 Forge 事件監聽器。
 * <p>
 * 監聽 Forge 匯流排事件，包含：
 * <ul>
 *   <li>Brigadier 指令註冊 (/login, /register, /l, /reg, /neoauth reload)</li>
 *   <li>玩家進出伺服器事件與歡迎公告發送</li>
 *   <li>未登入玩家的行為防護（阻擋對話、指令、方塊破壞/放置、互動、丟棄物品、傷害及移動）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "neoauth", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvents {

    /**
     * 註冊登入、註冊與管理指令。
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 註冊 /login 與 /l
        dispatcher.register(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.string())
                        .executes(context -> executeLogin(context.getSource(), StringArgumentType.getString(context, "password")))));
        dispatcher.register(Commands.literal("l")
                .then(Commands.argument("password", StringArgumentType.string())
                        .executes(context -> executeLogin(context.getSource(), StringArgumentType.getString(context, "password")))));

        // 註冊 /register 與 /reg
        dispatcher.register(Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.string())
                        .executes(context -> executeRegister(context.getSource(), StringArgumentType.getString(context, "password"), StringArgumentType.getString(context, "password")))
                        .then(Commands.argument("confirm", StringArgumentType.string())
                                .executes(context -> executeRegister(context.getSource(), StringArgumentType.getString(context, "password"), StringArgumentType.getString(context, "confirm"))))));
        dispatcher.register(Commands.literal("reg")
                .then(Commands.argument("password", StringArgumentType.string())
                        .executes(context -> executeRegister(context.getSource(), StringArgumentType.getString(context, "password"), StringArgumentType.getString(context, "password")))
                        .then(Commands.argument("confirm", StringArgumentType.string())
                                .executes(context -> executeRegister(context.getSource(), StringArgumentType.getString(context, "password"), StringArgumentType.getString(context, "confirm"))))));

        // 註冊 /neoauth reload 管理指令
        dispatcher.register(Commands.literal("neoauth")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("reload")
                        .executes(context -> executeReload(context.getSource()))));
    }

    private static int executeLogin(CommandSourceStack source, String password) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.only_players")));
            return 0;
        }

        String username = player.getGameProfile().getName();
        String ip = player.getIpAddress();
        AuthLogic.LoginResult result = AuthLogic.attemptLogin(player.getUUID(), username, ip, password);

        if (result == AuthLogic.LoginResult.SUCCESS) {
            Services.PLATFORM.removeFreezeEffects(player);
            source.sendSuccess(() -> Component.literal(result.getMessage()), false);
            return 1;
        } else {
            source.sendFailure(Component.literal(result.getMessage()));
            return 0;
        }
    }

    private static int executeRegister(CommandSourceStack source, String password, String confirm) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.only_players")));
            return 0;
        }

        String username = player.getGameProfile().getName();
        String ip = player.getIpAddress();
        AuthLogic.RegisterResult result = AuthLogic.attemptRegister(player.getUUID(), username, ip, password, confirm);

        if (result == AuthLogic.RegisterResult.SUCCESS) {
            Services.PLATFORM.removeFreezeEffects(player);
            source.sendSuccess(() -> Component.literal(result.getMessage()), false);
            return 1;
        } else {
            source.sendFailure(Component.literal(result.getMessage()));
            return 0;
        }
    }

    private static int executeReload(CommandSourceStack source) {
        boolean success = ConfigManager.getInstance().reload();
        if (success) {
            source.sendSuccess(() -> Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.reload_success")), true);
            return 1;
        } else {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.reload_failed", "Unknown error")));
            return 0;
        }
    }

    private static void promptAuth(ServerPlayer player) {
        String msg = AuthLogic.getPromptMessage(player.getGameProfile().getName());
        Services.PLATFORM.sendActionBar(player, msg);
    }

    /**
     * 玩家進入伺服器事件處理。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String username = player.getGameProfile().getName();
            UUID uuid = player.getUUID();
            boolean isOffline = AuthManager.isOfflineUuid(username, uuid);
            boolean hasTextures = player.getGameProfile().getProperties() != null
                    && player.getGameProfile().getProperties().containsKey("textures");
            boolean isPremium = !isOffline && (hasTextures || AuthManager.isPremiumVerified(uuid));

            // 發送 welcome.txt 歡迎公告 (若啟用)
            if (ConfigManager.getInstance().getConfig().isDisplayWelcomeMessage()) {
                String welcome = ConfigManager.getInstance().getFormattedWelcomeMessage(username);
                if (!welcome.isEmpty()) {
                    for (String line : welcome.split("\r?\n")) {
                        Services.PLATFORM.sendMessage(player, line);
                    }
                }
            }

            if (isPremium) {
                // 正版驗證通過，自動放行並登入
                AuthManager.setLoggedIn(uuid);
                Services.PLATFORM.removeFreezeEffects(player);
                String msg = ConfigManager.getInstance().getMessagesManager().get("general.welcome_premium", username);
                Services.PLATFORM.sendMessage(player, msg);
            } else {
                // 離線玩家或未通過正版驗證玩家，進入待登入狀態
                AuthManager.setLoggedOut(uuid);
                String msg = AuthLogic.getPromptMessage(username);
                Services.PLATFORM.sendMessage(player, msg);
            }
        }
    }

    /**
     * 玩家離開伺服器事件處理。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AuthManager.setLoggedOut(player.getUUID());
            AuthManager.clearPremiumVerified(player.getUUID());
        }
    }

    /**
     * 對話訊息防護（未登入玩家禁止發言）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null && !AuthManager.isLoggedIn(player.getUUID())) {
            event.setCanceled(true);
            promptAuth(player);
        }
    }

    /**
     * 指令執行防護（未登入玩家僅允許使用登入/註冊相關指令）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            if (!AuthManager.isLoggedIn(player.getUUID())) {
                String cmd = event.getParseResults().getReader().getString();
                if (!AuthLogic.isCommandAllowed(cmd)) {
                    event.setCanceled(true);
                    promptAuth(player);
                }
            }
        }
    }

    // --- 方塊與實體互動防護 ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractLeftBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractRightBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractRightItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    // --- 物品丟棄防護 ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player.getUUID())) {
            event.setCanceled(true);
            player.getInventory().add(event.getEntity().getItem());
        }
    }

    // --- 傷害防護（未登入玩家不可造成傷害亦不可受到傷害） ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player.getUUID())) {
            event.setCanceled(true);
        }
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    // --- 移動凍結效果維護 ---

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            if (!AuthManager.isLoggedIn(player.getUUID())) {
                Services.PLATFORM.applyFreezeEffects(player);
            }
        }
    }
}
