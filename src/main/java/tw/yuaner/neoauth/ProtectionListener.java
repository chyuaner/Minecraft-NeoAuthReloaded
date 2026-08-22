package tw.yuaner.neoauth;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;

@EventBusSubscriber(modid = NeoAuth.MODID)
public class ProtectionListener {

    private static void promptAuth(ServerPlayer player) {
        if (DatabaseManager.isRegistered(player.getGameProfile().getName())) {
            player.displayClientMessage(Component.literal("§cYou must log in first! Use /login <password>"), true);
        } else {
            player.displayClientMessage(Component.literal("§cYou must register first! Use /register <password> <confirm>"), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AuthManager.setLoggedOut(player);
            AuthManager.clearPremiumVerified(player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String username = player.getGameProfile().getName();
            UUID uuid = player.getUUID();
            boolean isOffline = AuthManager.isOfflineUuid(username, uuid);
            boolean isPremium = !isOffline && AuthManager.isPremiumVerified(uuid);

            if (isPremium) {
                AuthManager.setLoggedIn(player);
                player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
                player.removeEffect(net.minecraft.world.effect.MobEffects.JUMP);
                player.removeEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
                player.sendSystemMessage(Component.literal("§aWelcome back, " + username + "! §7(Authenticated via Mojang Online-Mode)"));
            } else {
                AuthManager.setLoggedOut(player); // ensure they are logged out
                if (DatabaseManager.isRegistered(username)) {
                    player.sendSystemMessage(Component.literal("§aWelcome back! Please /login <password>"));
                } else {
                    player.sendSystemMessage(Component.literal("§aWelcome! Please /register <password> <confirm>"));
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        if (!AuthManager.isLoggedIn(event.getPlayer())) {
            event.setCanceled(true);
            promptAuth(event.getPlayer());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            if (!AuthManager.isLoggedIn(player)) {
                String cmd = event.getParseResults().getReader().getString().trim();
                if (cmd.startsWith("/")) {
                    cmd = cmd.substring(1);
                }
                String lower = cmd.toLowerCase();
                if (!lower.startsWith("login") && !lower.startsWith("l ") && !lower.equals("l")
                        && !lower.startsWith("register") && !lower.startsWith("reg ") && !lower.equals("reg")) {
                    event.setCanceled(true);
                    promptAuth(player);
                }
            }
        }
    }

    // --- Interaction Protection ---
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractLeftBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractRightBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractRightItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player)) {
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player)) {
            event.setCanceled(true);
        }
    }

    // --- Item Protection ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemDrop(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player)) {
            event.setCanceled(true);
            player.getInventory().add(event.getEntity().getItem());
        }
    }



    // --- Damage Protection ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player)) {
            event.setCanceled(true); // Can't attack others
        }
        if (event.getEntity() instanceof ServerPlayer player && !AuthManager.isLoggedIn(player)) {
            event.setCanceled(true); // Can't be attacked
        }
    }

    // --- Movement Protection ---
    // Since canceling movement packets directly is tricky without mixins,
    // we use a simple approach: if they move, teleport them back to the spawn/login point.
    // AuthMe usually stores exact location, but here we can just keep them stationary or give them Slowness 255 and Jump Boost 250 (which stops movement in vanilla).
    
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!AuthManager.isLoggedIn(player)) {
                // Apply max slowness and negative jump boost to freeze
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 2, 255, false, false, false));
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP, 2, 250, false, false, false));
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 2, 255, false, false, false));
            }
        }
    }
}
