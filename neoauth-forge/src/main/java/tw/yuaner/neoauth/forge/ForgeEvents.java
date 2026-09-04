package tw.yuaner.neoauth.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.DatabaseManager;
import tw.yuaner.neoauth.database.PlayerAuthData;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.config.MessagesManager;
import tw.yuaner.neoauth.core.AuthLogic;
import tw.yuaner.neoauth.platform.Services;
import tw.yuaner.neoauth.util.ArgumentTokenizer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Minecraft 1.20.1 Forge 事件監聽器。
 * <p>
 * 監聽 Forge 匯流排事件，包含：
 * <ul>
 *   <li>Brigadier 指令註冊 (/login, /register, /changepassword, /logout, /email, /neoauth 及所有管理子指令)</li>
 *   <li>玩家進出伺服器事件與歡迎公告發送</li>
 *   <li>未登入玩家的行為防護（阻擋對話、指令、方塊破壞/放置、互動、丟棄物品、傷害及移動）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "neoauthreloaded", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvents {

    /**
     * 註冊登入、註冊與管理指令。
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 註冊 /login 與 /l (使用 greedyString 支援含 @, !, # 等特殊符號之密碼)
        dispatcher.register(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(context -> executeLogin(context.getSource(), ArgumentTokenizer.cleanArgument(StringArgumentType.getString(context, "password"))))));
        dispatcher.register(Commands.literal("l")
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(context -> executeLogin(context.getSource(), ArgumentTokenizer.cleanArgument(StringArgumentType.getString(context, "password"))))));

        // 註冊 /register 與 /reg (使用 greedyString 支援單密碼或雙密碼確認，並相容特殊符號)
        dispatcher.register(Commands.literal("register")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(context -> executeRegisterArgs(context.getSource(), StringArgumentType.getString(context, "args")))));
        dispatcher.register(Commands.literal("reg")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(context -> executeRegisterArgs(context.getSource(), StringArgumentType.getString(context, "args")))));

        // 註冊 /changepassword 與 /cp (<舊密碼> <新密碼> <確認新密碼>)
        dispatcher.register(Commands.literal("changepassword")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(context -> executeChangePasswordArgs(context.getSource(), StringArgumentType.getString(context, "args")))));
        dispatcher.register(Commands.literal("cp")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(context -> executeChangePasswordArgs(context.getSource(), StringArgumentType.getString(context, "args")))));

        // 註冊 /logout
        dispatcher.register(Commands.literal("logout")
                .executes(context -> executeLogout(context.getSource())));

        // 註冊 /lastlogin (開放給一般玩家查看自己的最後登入)
        dispatcher.register(Commands.literal("lastlogin")
                .executes(context -> executePlayerLastLogin(context.getSource())));

        // 註冊 /getip (開放給一般玩家查看自己的連線 IP)
        dispatcher.register(Commands.literal("getip")
                .executes(context -> executePlayerGetIp(context.getSource())));

        // 註冊 /email (支援 /email, /email show, /email set <新地址>)
        dispatcher.register(Commands.literal("email")
                .executes(context -> executeEmailShow(context.getSource()))
                .then(Commands.literal("show")
                        .executes(context -> executeEmailShow(context.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("email", StringArgumentType.greedyString())
                                .executes(context -> executeEmailSet(context.getSource(), ArgumentTokenizer.cleanArgument(StringArgumentType.getString(context, "email")))))));

        // 註冊 /neoauth 管理指令根節點
        dispatcher.register(Commands.literal("neoauth")
                .requires(source -> source.hasPermission(2))
                .executes(context -> executeAdminHelp(context.getSource(), null))
                .then(Commands.literal("help")
                        .executes(context -> executeAdminHelp(context.getSource(), null))
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(context -> executeAdminHelp(context.getSource(), ArgumentTokenizer.cleanArgument(StringArgumentType.getString(context, "query"))))))
                .then(Commands.literal("register")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Services.PLATFORM.getOnlinePlayerNames(c.getSource()), b))
                                .then(Commands.argument("password", StringArgumentType.greedyString())
                                        .executes(context -> executeAdminRegister(context.getSource(), StringArgumentType.getString(context, "player"), ArgumentTokenizer.cleanArgument(StringArgumentType.getString(context, "password")))))))
                .then(Commands.literal("forcelogin")
                        .executes(context -> executeAdminForceLoginSelf(context.getSource()))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Services.PLATFORM.getOnlinePlayerNames(c.getSource()), b))
                                .executes(context -> executeAdminForceLogin(context.getSource(), StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("password")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Services.PLATFORM.getOnlinePlayerNames(c.getSource()), b))
                                .then(Commands.argument("newPassword", StringArgumentType.greedyString())
                                        .executes(context -> executeAdminPassword(context.getSource(), StringArgumentType.getString(context, "player"), ArgumentTokenizer.cleanArgument(StringArgumentType.getString(context, "newPassword")))))))
                .then(Commands.literal("changepassword")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Services.PLATFORM.getOnlinePlayerNames(c.getSource()), b))
                                .then(Commands.argument("newPassword", StringArgumentType.greedyString())
                                        .executes(context -> executeAdminPassword(context.getSource(), StringArgumentType.getString(context, "player"), ArgumentTokenizer.cleanArgument(StringArgumentType.getString(context, "newPassword")))))))
                .then(Commands.literal("pass")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Services.PLATFORM.getOnlinePlayerNames(c.getSource()), b))
                                .then(Commands.argument("newPassword", StringArgumentType.greedyString())
                                        .executes(context -> executeAdminPassword(context.getSource(), StringArgumentType.getString(context, "player"), ArgumentTokenizer.cleanArgument(StringArgumentType.getString(context, "newPassword")))))))
                .then(Commands.literal("lastlogin")
                        .executes(context -> executeAdminLastLoginSelf(context.getSource()))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Services.PLATFORM.getOnlinePlayerNames(c.getSource()), b))
                                .executes(context -> executeAdminLastLogin(context.getSource(), StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("accounts")
                        .executes(context -> executeAdminAccountsSelf(context.getSource()))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Services.PLATFORM.getOnlinePlayerNames(c.getSource()), b))
                                .executes(context -> executeAdminAccounts(context.getSource(), StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("email")
                        .executes(context -> executeAdminEmailSelf(context.getSource()))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Services.PLATFORM.getOnlinePlayerNames(c.getSource()), b))
                                        .then(Commands.argument("email", StringArgumentType.greedyString())
                                                .executes(context -> executeAdminSetEmail(context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        ArgumentTokenizer.cleanArgument(StringArgumentType.getString(context, "email")))))))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Services.PLATFORM.getOnlinePlayerNames(c.getSource()), b))
                                .executes(context -> executeAdminEmail(context.getSource(), StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("setemail")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Services.PLATFORM.getOnlinePlayerNames(c.getSource()), b))
                                .then(Commands.argument("email", StringArgumentType.greedyString())
                                        .executes(context -> executeAdminSetEmail(context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                ArgumentTokenizer.cleanArgument(StringArgumentType.getString(context, "email")))))))
                .then(Commands.literal("getip")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Services.PLATFORM.getOnlinePlayerNames(c.getSource()), b))
                                .executes(context -> executeAdminGetIp(context.getSource(), StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("reload")
                        .executes(context -> executeReload(context.getSource())))
                .then(Commands.literal("version")
                        .executes(context -> executeAdminVersion(context.getSource())))
                .then(Commands.literal("recent")
                        .executes(context -> executeAdminRecent(context.getSource())))
                .then(Commands.literal("setenableregister")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> executeAdminSetEnableRegister(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("circuitbreaker")
                        .executes(context -> executeCircuitBreakerStatus(context.getSource()))
                        .then(Commands.literal("status")
                                .executes(context -> executeCircuitBreakerStatus(context.getSource())))
                        .then(Commands.literal("reset")
                                .executes(context -> executeCircuitBreakerReset(context.getSource())))
                        .then(Commands.literal("trip")
                                .executes(context -> executeCircuitBreakerTrip(context.getSource(), 0))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> executeCircuitBreakerTrip(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> executeCircuitBreakerTrip(context.getSource(), IntegerArgumentType.getInteger(context, "seconds"))))))
                .then(Commands.literal("cb")
                        .executes(context -> executeCircuitBreakerStatus(context.getSource()))
                        .then(Commands.literal("status")
                                .executes(context -> executeCircuitBreakerStatus(context.getSource())))
                        .then(Commands.literal("reset")
                                .executes(context -> executeCircuitBreakerReset(context.getSource())))
                        .then(Commands.literal("trip")
                                .executes(context -> executeCircuitBreakerTrip(context.getSource(), 0))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> executeCircuitBreakerTrip(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> executeCircuitBreakerTrip(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))));
    }

    private static int executeRegisterArgs(CommandSourceStack source, String rawArgs) {
        List<String> tokens = ArgumentTokenizer.tokenize(rawArgs);
        if (tokens.isEmpty()) {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("register.register_prompt")));
            return 0;
        }
        String password = tokens.get(0);
        String confirm = tokens.size() > 1 ? tokens.get(1) : password;
        return executeRegister(source, password, confirm);
    }

    private static int executeChangePasswordArgs(CommandSourceStack source, String rawArgs) {
        List<String> tokens = ArgumentTokenizer.tokenize(rawArgs);
        if (tokens.size() < 3) {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("help.changepassword")));
            return 0;
        }
        return executeChangePassword(source, tokens.get(0), tokens.get(1), tokens.get(2));
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
            AuthLogic.executeHooks(source.getServer(), player, username,
                    ConfigManager.getInstance().getCommandsConfig().getOnLoginConsole(),
                    ConfigManager.getInstance().getCommandsConfig().getOnLoginPlayer());
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
            IAuthConfig config = ConfigManager.getInstance().getConfig();
            if (config != null && config.isForceKickAfterRegister()) {
                player.connection.disconnect(Component.literal(result.getMessage()));
                return 1;
            }
            if (config != null && config.isForceLoginAfterRegister()) {
                source.sendSuccess(() -> Component.literal(result.getMessage()), false);
                source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("login.login_prompt")));
                return 1;
            }
            Services.PLATFORM.removeFreezeEffects(player);
            source.sendSuccess(() -> Component.literal(result.getMessage()), false);
            AuthLogic.executeHooks(source.getServer(), player, username,
                    ConfigManager.getInstance().getCommandsConfig().getOnRegisterConsole(),
                    ConfigManager.getInstance().getCommandsConfig().getOnRegisterPlayer());
            return 1;
        } else {
            source.sendFailure(Component.literal(result.getMessage()));
            return 0;
        }
    }

    private static int executeChangePassword(CommandSourceStack source, String oldPassword, String newPassword, String confirmPassword) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.only_players")));
            return 0;
        }

        String username = player.getGameProfile().getName();
        AuthLogic.ChangePasswordResult result = AuthLogic.attemptChangePassword(player.getUUID(), username, oldPassword, newPassword, confirmPassword);

        if (result == AuthLogic.ChangePasswordResult.SUCCESS) {
            source.sendSuccess(() -> Component.literal(result.getMessage()), false);
            return 1;
        } else {
            source.sendFailure(Component.literal(result.getMessage()));
            return 0;
        }
    }

    private static int executeLogout(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.only_players")));
            return 0;
        }

        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        UUID uuid = player.getUUID();
        if (!AuthManager.isLoggedIn(uuid)) {
            source.sendFailure(Component.literal(msgMgr.get("logout.not_logged_in")));
            return 0;
        }

        String username = player.getGameProfile().getName();
        AuthLogic.attemptLogout(uuid, username);
        Services.PLATFORM.applyFreezeEffects(player);
        source.sendSuccess(() -> Component.literal(msgMgr.get("logout.success")), false);
        promptAuth(player);

        AuthLogic.executeHooks(source.getServer(), player, username,
                ConfigManager.getInstance().getCommandsConfig().getOnLogoutConsole(),
                ConfigManager.getInstance().getCommandsConfig().getOnLogoutPlayer());
        return 1;
    }

    private static int executePlayerLastLogin(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.only_players")));
            return 0;
        }

        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (!AuthManager.isLoggedIn(player.getUUID())) {
            source.sendFailure(Component.literal(msgMgr.get("login.login_prompt")));
            return 0;
        }

        String username = player.getGameProfile().getName();
        PlayerAuthData data = DatabaseManager.getPlayerData(username);
        if (data == null) {
            source.sendFailure(Component.literal(msgMgr.get("general.database_error")));
            return 0;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String lastLoginStr = data.getLastLogin() > 0 ? sdf.format(new Date(data.getLastLogin())) : msgMgr.get("admin.lastlogin_never");
        String regDateStr = data.getRegDate() > 0 ? sdf.format(new Date(data.getRegDate())) : "Unknown";

        source.sendSuccess(() -> Component.literal(msgMgr.get("player.lastlogin_info", lastLoginStr, regDateStr)), false);
        return 1;
    }

    private static int executePlayerGetIp(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.only_players")));
            return 0;
        }

        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (!AuthManager.isLoggedIn(player.getUUID())) {
            source.sendFailure(Component.literal(msgMgr.get("login.login_prompt")));
            return 0;
        }

        String ip = player.getIpAddress();
        if (ip == null || ip.isBlank()) {
            ip = DatabaseManager.getIp(player.getGameProfile().getName());
        }
        if (ip == null || ip.isBlank()) {
            ip = "127.0.0.1";
        }

        String finalIp = ip;
        source.sendSuccess(() -> Component.literal(msgMgr.get("player.getip_info", finalIp)), false);
        return 1;
    }

    private static int executeEmailShow(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.only_players")));
            return 0;
        }

        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        String username = player.getGameProfile().getName();
        String email = DatabaseManager.getEmail(username);

        if (email != null && !email.isBlank()) {
            source.sendSuccess(() -> Component.literal(msgMgr.get("email.show", email)), false);
            return 1;
        } else {
            source.sendFailure(Component.literal(msgMgr.get("email.none")));
            return 0;
        }
    }

    private static int executeEmailSet(CommandSourceStack source, String email) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.only_players")));
            return 0;
        }

        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (!AuthManager.isLoggedIn(player.getUUID())) {
            source.sendFailure(Component.literal(msgMgr.get("changepassword.not_logged_in")));
            return 0;
        }

        if (email == null || !email.contains("@") || !email.contains(".")) {
            source.sendFailure(Component.literal(msgMgr.get("email.invalid")));
            return 0;
        }

        String username = player.getGameProfile().getName();
        boolean success = DatabaseManager.setEmail(username, email);
        if (success) {
            source.sendSuccess(() -> Component.literal(msgMgr.get("email.set_success", email)), false);
            return 1;
        } else {
            source.sendFailure(Component.literal(msgMgr.get("general.database_error")));
            return 0;
        }
    }

    private static int executeReload(CommandSourceStack source) {
        boolean success = ConfigManager.getInstance().reload();
        if (success) {
            tw.yuaner.neoauth.util.BlueMapIntegration.clearCache();
            source.sendSuccess(() -> Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.reload_success")), true);
            return 1;
        } else {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("general.reload_failed", "Unknown error")));
            return 0;
        }
    }

    private static int executeAdminRegister(CommandSourceStack source, String targetPlayer, String password) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (DatabaseManager.isRegistered(targetPlayer)) {
            source.sendFailure(Component.literal(msgMgr.get("admin.register_already_registered", targetPlayer)));
            return 0;
        }

        int min = ConfigManager.getInstance().getConfig().getMinPasswordLength();
        int max = ConfigManager.getInstance().getConfig().getMaxPasswordLength();
        if (min > 0 && password.length() < min) {
            source.sendFailure(Component.literal(msgMgr.get("register.password_too_short", min)));
            return 0;
        }
        if (max > 0 && password.length() > max) {
            source.sendFailure(Component.literal(msgMgr.get("register.password_too_long", max)));
            return 0;
        }

        boolean success = DatabaseManager.registerPlayer(targetPlayer, password, "127.0.0.1");
        if (success) {
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.register_success", targetPlayer)), true);
            return 1;
        } else {
            source.sendFailure(Component.literal(msgMgr.get("general.database_error")));
            return 0;
        }
    }

    private static int executeAdminForceLogin(CommandSourceStack source, String targetPlayerName) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        Object targetObj = Services.PLATFORM.getOnlinePlayer(source, targetPlayerName);
        if (targetObj instanceof ServerPlayer target) {
            AuthManager.setLoggedIn(target.getUUID());
            Services.PLATFORM.removeFreezeEffects(target);
            target.sendSystemMessage(Component.literal(msgMgr.get("login.success")));
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.forcelogin_success", target.getGameProfile().getName())), true);
            AuthLogic.executeHooks(source.getServer(), target, target.getGameProfile().getName(),
                    ConfigManager.getInstance().getCommandsConfig().getOnLoginConsole(),
                    ConfigManager.getInstance().getCommandsConfig().getOnLoginPlayer());
            return 1;
        } else {
            source.sendFailure(Component.literal(msgMgr.get("admin.player_not_online", targetPlayerName)));
            return 0;
        }
    }

    private static int executeAdminForceLoginSelf(CommandSourceStack source) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (source.getEntity() instanceof ServerPlayer player) {
            AuthManager.setLoggedIn(player.getUUID());
            Services.PLATFORM.removeFreezeEffects(player);
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.forcelogin_self_success")), true);
            AuthLogic.executeHooks(source.getServer(), player, player.getGameProfile().getName(),
                    ConfigManager.getInstance().getCommandsConfig().getOnLoginConsole(),
                    ConfigManager.getInstance().getCommandsConfig().getOnLoginPlayer());
            return 1;
        } else {
            source.sendFailure(Component.literal(msgMgr.get("admin.player_not_specified")));
            return 0;
        }
    }

    private static int executeAdminPassword(CommandSourceStack source, String targetPlayer, String newPassword) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (!DatabaseManager.isRegistered(targetPlayer)) {
            source.sendFailure(Component.literal(msgMgr.get("admin.player_not_found", targetPlayer)));
            return 0;
        }

        AuthLogic.ChangePasswordResult res = AuthLogic.attemptAdminChangePassword(targetPlayer, newPassword);
        if (res == AuthLogic.ChangePasswordResult.SUCCESS) {
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.password_changed_success", targetPlayer)), true);
            return 1;
        } else {
            source.sendFailure(Component.literal(res.getMessage()));
            return 0;
        }
    }

    private static int executeAdminLastLogin(CommandSourceStack source, String targetPlayer) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        PlayerAuthData data = DatabaseManager.getPlayerData(targetPlayer);
        if (data == null) {
            source.sendFailure(Component.literal(msgMgr.get("admin.player_not_found", targetPlayer)));
            return 0;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String lastLoginStr = data.getLastLogin() > 0 ? sdf.format(new Date(data.getLastLogin())) : msgMgr.get("admin.lastlogin_never");
        String regDateStr = data.getRegDate() > 0 ? sdf.format(new Date(data.getRegDate())) : "Unknown";
        String ipStr = data.getIp() != null && !data.getIp().isBlank() ? data.getIp() : "127.0.0.1";

        source.sendSuccess(() -> Component.literal(msgMgr.get("admin.lastlogin_info", data.getRealName(), lastLoginStr, ipStr, regDateStr)), false);
        return 1;
    }

    private static int executeAdminLastLoginSelf(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return executeAdminLastLogin(source, player.getGameProfile().getName());
        } else {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("admin.player_not_specified")));
            return 0;
        }
    }

    private static int executeAdminAccounts(CommandSourceStack source, String targetPlayerOrIp) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        List<String> accounts = DatabaseManager.getAccounts(targetPlayerOrIp);
        if (accounts.isEmpty()) {
            source.sendFailure(Component.literal(msgMgr.get("admin.accounts_none")));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(msgMgr.get("admin.accounts_header", targetPlayerOrIp, accounts.size())), false);
        for (String acc : accounts) {
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.accounts_item", acc)), false);
        }
        return 1;
    }

    private static int executeAdminAccountsSelf(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return executeAdminAccounts(source, player.getGameProfile().getName());
        } else {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("admin.player_not_specified")));
            return 0;
        }
    }

    private static int executeAdminEmail(CommandSourceStack source, String targetPlayer) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        String email = DatabaseManager.getEmail(targetPlayer);
        if (email != null && !email.isBlank()) {
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.email_info", targetPlayer, email)), false);
        } else {
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.email_none", targetPlayer)), false);
        }
        return 1;
    }

    private static int executeAdminEmailSelf(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return executeAdminEmail(source, player.getGameProfile().getName());
        } else {
            source.sendFailure(Component.literal(ConfigManager.getInstance().getMessagesManager().get("admin.player_not_specified")));
            return 0;
        }
    }

    private static int executeAdminSetEmail(CommandSourceStack source, String targetPlayer, String email) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (!DatabaseManager.isRegistered(targetPlayer)) {
            source.sendFailure(Component.literal(msgMgr.get("admin.player_not_found", targetPlayer)));
            return 0;
        }

        boolean success = DatabaseManager.setEmail(targetPlayer, email);
        if (success) {
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.email_updated", targetPlayer, email)), true);
            return 1;
        } else {
            source.sendFailure(Component.literal(msgMgr.get("general.database_error")));
            return 0;
        }
    }

    private static int executeAdminGetIp(CommandSourceStack source, String targetPlayer) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        Object targetObj = Services.PLATFORM.getOnlinePlayer(source, targetPlayer);
        String ip = null;
        if (targetObj instanceof ServerPlayer target) {
            ip = target.getIpAddress();
        } else {
            ip = DatabaseManager.getIp(targetPlayer);
        }

        if (ip != null && !ip.isBlank()) {
            String finalIp = ip;
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.getip_info", targetPlayer, finalIp)), false);
            return 1;
        } else {
            source.sendFailure(Component.literal(msgMgr.get("admin.getip_unknown", targetPlayer)));
            return 0;
        }
    }

    private static int executeAdminVersion(CommandSourceStack source) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        String platform = Services.PLATFORM.getPlatformName();
        String version = Services.PLATFORM.getModVersion();
        source.sendSuccess(() -> Component.literal(msgMgr.get("admin.version_info", version, platform)), false);
        return 1;
    }

    private static int executeAdminRecent(CommandSourceStack source) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        List<PlayerAuthData> recent = DatabaseManager.getRecentPlayers(10);
        if (recent.isEmpty()) {
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.recent_none")), false);
            return 1;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        source.sendSuccess(() -> Component.literal(msgMgr.get("admin.recent_header")), false);
        for (PlayerAuthData data : recent) {
            String timeStr = data.getLastLogin() > 0 ? sdf.format(new Date(data.getLastLogin())) : msgMgr.get("admin.lastlogin_never");
            String ipStr = data.getIp() != null && !data.getIp().isBlank() ? data.getIp() : "127.0.0.1";
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.recent_item", data.getRealName(), timeStr, ipStr)), false);
        }
        return 1;
    }

    private static int executeAdminHelp(CommandSourceStack source, String query) {
        source.sendSuccess(() -> Component.literal("§6===== §eNeoAuth Admin Commands §6====="), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth register <player> <pwd> §7- Register account"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth forcelogin [player] §7- Force login player"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth password <player> <pwd> §7- Change player password"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth lastlogin [player] §7- View last login info"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth accounts [player/IP] §7- View associated accounts"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth email [player] §7- View player email"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth email set <player> <email> §7- Set player email"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth setemail <player> <email> §7- Set player email"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth getip <player> §7- Get player IP"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth reload §7- Reload configs and messages"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth version §7- Show version info"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth recent §7- Show recent logged in players"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth setenableregister <true|false> §7- Enable/disable registration"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth cb [status] §7- Check circuit breaker status"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth cb reset §7- Reset circuit breaker"), false);
        source.sendSuccess(() -> Component.literal("§e/neoauth cb trip [seconds] §7- Trip circuit breaker manually"), false);
        return 1;
    }

    private static int executeCircuitBreakerStatus(CommandSourceStack source) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        boolean isTripped = tw.yuaner.neoauth.util.MojangCircuitBreaker.isTripped();
        if (isTripped) {
            int remaining = tw.yuaner.neoauth.util.MojangCircuitBreaker.getCooldownSecondsRemaining();
            String reason = tw.yuaner.neoauth.util.MojangCircuitBreaker.getLastFailureReason();
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.circuitbreaker_status_tripped", remaining, reason)), false);
        } else {
            boolean enabled = ConfigManager.getInstance().getConfig().isCircuitBreakerEnabled();
            int duration = ConfigManager.getInstance().getConfig().getCircuitBreakerDurationSeconds();
            String reason = tw.yuaner.neoauth.util.MojangCircuitBreaker.getLastFailureReason();
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.circuitbreaker_status_closed", enabled ? "啟用" : "關閉", duration, reason)), false);
        }
        return 1;
    }

    private static int executeCircuitBreakerReset(CommandSourceStack source) {
        tw.yuaner.neoauth.util.MojangCircuitBreaker.reset();
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        source.sendSuccess(() -> Component.literal(msgMgr.get("admin.circuitbreaker_reset_success")), true);
        return 1;
    }

    private static int executeCircuitBreakerTrip(CommandSourceStack source, int seconds) {
        int duration = seconds > 0 ? seconds : ConfigManager.getInstance().getConfig().getCircuitBreakerDurationSeconds();
        tw.yuaner.neoauth.util.MojangCircuitBreaker.tripManually(duration, "管理員指令手動觸發");
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        source.sendSuccess(() -> Component.literal(msgMgr.get("admin.circuitbreaker_trip_success", duration)), true);
        return 1;
    }

    private static int executeAdminSetEnableRegister(CommandSourceStack source, boolean enabled) {
        boolean success = ConfigManager.getInstance().setEnableRegister(enabled);
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (success) {
            source.sendSuccess(() -> Component.literal(msgMgr.get("admin.setenableregister_success", String.valueOf(enabled))), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("§c設定儲存失敗，請檢查伺服器日誌！"));
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

            // 確保玩家實體 GameProfile 注入已驗證的 Textures (防護並免除 SkinRestorer 重複抓取)
            tw.yuaner.neoauth.util.TextureProperty tex = AuthManager.getVerifiedTextures(uuid);
            if (tex == null && username != null) {
                tex = AuthManager.getVerifiedTextures(username);
            }
            if (tex != null && tex.value() != null && player.getGameProfile().getProperties() != null
                    && !player.getGameProfile().getProperties().containsKey("textures")) {
                com.mojang.authlib.properties.Property prop = tex.hasSignature()
                        ? new com.mojang.authlib.properties.Property("textures", tex.value(), tex.signature())
                        : new com.mojang.authlib.properties.Property("textures", tex.value());
                player.getGameProfile().getProperties().put("textures", prop);
            }

            boolean isOffline = AuthManager.isOfflineUuid(username, uuid);
            boolean hasTextures = player.getGameProfile().getProperties() != null
                    && player.getGameProfile().getProperties().containsKey("textures");
            boolean isPremium = AuthManager.isPremiumVerified(uuid) || (!isOffline && hasTextures);

            // 發送 welcome.txt 歡迎公告 (若啟用)
            if (ConfigManager.getInstance().getConfig().isDisplayWelcomeMessage()) {
                String welcome = ConfigManager.getInstance().getFormattedWelcomeMessage(username);
                if (!welcome.isEmpty()) {
                    for (String line : welcome.split("\r?\n")) {
                        Services.PLATFORM.sendMessage(player, line);
                    }
                }
            }

            // 若玩家為 Mojang 伺服器異常或熔斷降級進入，發送專屬提示文案
            if (AuthManager.isMojangFallback(uuid)) {
                String fallbackMsg = ConfigManager.getInstance().getMessagesManager().get("general.mojang_unavailable_fallback");
                Services.PLATFORM.sendMessage(player, fallbackMsg);
                AuthManager.clearMojangFallback(uuid);
            }

            boolean autoLoggedIn = AuthLogic.handlePlayerJoin(uuid, username, player.getIpAddress(), isPremium);
            if (autoLoggedIn) {
                // 已註冊之正版驗證玩家 或 非強制註冊模式下的訪客玩家：放行並登入
                Services.PLATFORM.removeFreezeEffects(player);
                if (DatabaseManager.isRegistered(username)) {
                    String msg = ConfigManager.getInstance().getMessagesManager().get("general.welcome_premium", username);
                    Services.PLATFORM.sendMessage(player, msg);
                    AuthLogic.executeHooks(player.getServer(), player, username,
                            ConfigManager.getInstance().getCommandsConfig().getOnLoginConsole(),
                            ConfigManager.getInstance().getCommandsConfig().getOnLoginPlayer());
                }
            } else {
                // 未註冊玩家（強制註冊模式下）或離線玩家：進入待註冊/待登入狀態
                Services.PLATFORM.applyFreezeEffects(player);
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
            String username = player.getGameProfile().getName();
            AuthLogic.attemptLogout(player.getUUID(), username);
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

    // --- 容器與背包開啟防護 ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player.getUUID())) {
            if (event.getContainer() != player.inventoryMenu) {
                player.closeContainer();
            }
        }
    }

    // --- 地面物品拾取防護 ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(EntityItemPickupEvent event) {
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
                int interval = ConfigManager.getInstance().getConfig().getRegistrationMessageInterval();
                if (interval > 0 && player.tickCount % (interval * 20) == 0) {
                    promptAuth(player);
                }
            }
        }
    }
}
