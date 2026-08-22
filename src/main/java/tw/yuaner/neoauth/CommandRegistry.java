package tw.yuaner.neoauth;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.world.effect.MobEffects;

@EventBusSubscriber(modid = NeoAuth.MODID)
public class CommandRegistry {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // /login and /l
        dispatcher.register(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.string())
                        .executes(context -> executeLogin(context.getSource(), StringArgumentType.getString(context, "password")))));
        dispatcher.register(Commands.literal("l")
                .then(Commands.argument("password", StringArgumentType.string())
                        .executes(context -> executeLogin(context.getSource(), StringArgumentType.getString(context, "password")))));

        // /register and /reg
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
    }

    private static int executeLogin(CommandSourceStack source, String password) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can login!"));
            return 0;
        }

        if (AuthManager.isLoggedIn(player)) {
            source.sendFailure(Component.literal("§cYou are already logged in!"));
            return 0;
        }

        String username = player.getGameProfile().getName();

        if (!DatabaseManager.isRegistered(username)) {
            source.sendFailure(Component.literal("§cYou are not registered. Use /register <password> <confirm>"));
            return 0;
        }

        boolean success = DatabaseManager.checkPassword(username, password);
        if (success) {
            AuthManager.setLoggedIn(player);
            DatabaseManager.updateLogin(username, player.getIpAddress());
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.removeEffect(MobEffects.JUMP);
            player.removeEffect(MobEffects.BLINDNESS);
            source.sendSuccess(() -> Component.literal("§aSuccessfully logged in!"), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("§cWrong password!"));
            return 0;
        }
    }

    private static int executeRegister(CommandSourceStack source, String password, String confirm) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can register!"));
            return 0;
        }

        if (AuthManager.isLoggedIn(player)) {
            source.sendFailure(Component.literal("§cYou are already logged in!"));
            return 0;
        }

        String username = player.getGameProfile().getName();

        if (DatabaseManager.isRegistered(username)) {
            source.sendFailure(Component.literal("§cYou are already registered. Use /login <password>"));
            return 0;
        }

        if (!password.equals(confirm)) {
            source.sendFailure(Component.literal("§cPasswords do not match!"));
            return 0;
        }

        boolean success = DatabaseManager.registerPlayer(username, password, player.getIpAddress());
        if (success) {
            AuthManager.setLoggedIn(player);
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.removeEffect(MobEffects.JUMP);
            player.removeEffect(MobEffects.BLINDNESS);
            source.sendSuccess(() -> Component.literal("§aSuccessfully registered and logged in!"), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("§cAn error occurred during registration."));
            return 0;
        }
    }
}
