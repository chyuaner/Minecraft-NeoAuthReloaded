package tw.yuaner.neoauth.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.platform.IPlatformHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Minecraft 1.21.1 NeoForge 平台的服務實作。
 * <p>
 * 負責透過 1.21.1 的 NeoForge / Minecraft API 實作效果給予、解除與訊息發送。
 */
public class NeoForgePlatformHelper implements IPlatformHelper {

    private final java.util.Map<java.util.UUID, net.minecraft.server.level.ServerBossEvent> bossBars = new java.util.concurrent.ConcurrentHashMap<>();


    @Override
    public IAuthConfig getConfig() {
        return ConfigManager.getInstance().getConfig();
    }

    @Override
    public String getPlatformName() {
        return "NeoForge (1.21.1)";
    }

    @Override
    public String getModVersion() {
        try {
            if (net.neoforged.fml.ModList.get() != null) {
                var container = net.neoforged.fml.ModList.get().getModContainerById("neoauthreloaded");
                if (container.isPresent()) {
                    String v = container.get().getModInfo().getVersion().toString();
                    if (v != null && !v.isBlank() && !"NONE".equalsIgnoreCase(v)) {
                        return v.trim();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return IPlatformHelper.super.getModVersion();
    }

    @Override
    public void applyFreezeEffects(Object playerObj) {
        if (playerObj instanceof ServerPlayer player) {
            IAuthConfig config = getConfig();
            // 在 1.21.1 中，MobEffects 均以 Holder<MobEffect> 形式提供
            if (config.isSlownessEnabled()) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 255, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 2, 250, false, false, false));
            }
            if (config.isBlindnessEnabled()) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 2, 255, false, false, false));
            }
        }
    }

    @Override
    public void removeFreezeEffects(Object playerObj) {
        if (playerObj instanceof ServerPlayer player) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.removeEffect(MobEffects.JUMP);
            player.removeEffect(MobEffects.BLINDNESS);
        }
    }

    @Override
    public void sendMessage(Object playerObj, String message) {
        if (playerObj instanceof ServerPlayer player) {
            player.sendSystemMessage(parseLinks(message));
        }
    }

    private Component parseLinks(String message) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^)]+)\\)|<(https?://[^>]+)>");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        net.minecraft.network.chat.MutableComponent result = Component.empty();
        int lastEnd = 0;
        
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                result.append(Component.literal(message.substring(lastEnd, matcher.start())));
            }
            String text;
            String url;
            if (matcher.group(1) != null && matcher.group(2) != null) {
                // 配對到 [text](url)
                text = matcher.group(1).replace("http:", "http\u200B:").replace("https:", "https\u200B:");
                url = matcher.group(2);
            } else {
                // 配對到 <url>
                url = matcher.group(3);
                text = url.replace("http:", "http\u200B:").replace("https:", "https\u200B:");
            }
            
            result.append(Component.literal(text).withStyle(style -> style
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, url))
                    .withUnderlined(true)));
            lastEnd = matcher.end();
        }
        
        if (lastEnd < message.length()) {
            result.append(Component.literal(message.substring(lastEnd)));
        }
        
        return result;
    }

    @Override
    public void sendActionBar(Object playerObj, String message) {
        if (playerObj instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    @Override
    public boolean teleportPlayer(Object playerObj, String worldName, double x, double y, double z, float yaw, float pitch) {
        if (playerObj instanceof ServerPlayer player) {
            ServerLevel targetLevel = null;
            if (worldName != null && !worldName.isBlank() && player.getServer() != null) {
                ResourceLocation dimLoc = ResourceLocation.parse(worldName);
                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
                targetLevel = player.getServer().getLevel(dimKey);
            }
            if (targetLevel == null) {
                targetLevel = player.serverLevel();
            }
            player.teleportTo(targetLevel, x, y, z, yaw, pitch);
            return true;
        }
        return false;
    }

    @Override
    public void executeConsoleCommand(Object serverOrSource, String command) {
        MinecraftServer server = resolveServer(serverOrSource);
        if (server != null && command != null && !command.isBlank()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }
    }

    @Override
    public void executePlayerCommand(Object playerObj, String command) {
        if (playerObj instanceof ServerPlayer player && command != null && !command.isBlank()) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
            }
        }
    }

    @Override
    public Object getOnlinePlayer(Object serverOrSource, String username) {
        MinecraftServer server = resolveServer(serverOrSource);
        if (server != null && username != null && !username.isBlank()) {
            return server.getPlayerList().getPlayerByName(username);
        }
        return null;
    }

    @Override
    public List<String> getOnlinePlayerNames(Object serverOrSource) {
        MinecraftServer server = resolveServer(serverOrSource);
        if (server != null) {
            return Arrays.asList(server.getPlayerNames());
        }
        return Collections.emptyList();
    }

    private MinecraftServer resolveServer(Object obj) {
        if (obj instanceof MinecraftServer ms) return ms;
        if (obj instanceof CommandSourceStack css) return css.getServer();
        if (obj instanceof ServerPlayer sp) return sp.getServer();
        return null;
    }

    @Override
    public void updateTimeoutDisplay(Object playerObj, int remainingSeconds, int totalSeconds) {
        if (playerObj instanceof ServerPlayer player) {
            String displayType = getConfig().getTimeoutDisplay();
            if ("BOSS_BAR".equals(displayType)) {
                net.minecraft.server.level.ServerBossEvent bossEvent = bossBars.computeIfAbsent(player.getUUID(), uuid -> {
                    net.minecraft.server.level.ServerBossEvent event = new net.minecraft.server.level.ServerBossEvent(
                            Component.empty(),
                            net.minecraft.world.BossEvent.BossBarColor.RED,
                            net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS
                    );
                    event.addPlayer(player);
                    return event;
                });
                String msg = tw.yuaner.neoauth.config.ConfigManager.getInstance().getMessagesManager().get("login.timeout_bossbar");
                if (msg.contains("{time}")) {
                    msg = msg.replace("{time}", String.valueOf(remainingSeconds));
                } else {
                    msg = tw.yuaner.neoauth.config.ConfigManager.getInstance().getMessagesManager().get("login.timeout_bossbar", remainingSeconds);
                }
                bossEvent.setName(Component.literal(msg));
                if (totalSeconds > 0) {
                    bossEvent.setProgress((float) remainingSeconds / totalSeconds);
                }
            } else if ("ACTION_BAR".equals(displayType)) {
                boolean registered = tw.yuaner.neoauth.DatabaseManager.isRegistered(player.getGameProfile().getName());
                boolean hasPassword = tw.yuaner.neoauth.DatabaseManager.hasPassword(player.getGameProfile().getName());
                String key = (registered && hasPassword) ? "login.timeout_actionbar" : "register.timeout_actionbar";
                String msg = tw.yuaner.neoauth.config.ConfigManager.getInstance().getMessagesManager().get(key);
                if (msg.contains("{time}")) {
                    msg = msg.replace("{time}", String.valueOf(remainingSeconds));
                } else {
                    msg = tw.yuaner.neoauth.config.ConfigManager.getInstance().getMessagesManager().get(key, remainingSeconds);
                }
                sendActionBar(player, msg);
            }
        }
    }

    @Override
    public void clearTimeoutDisplay(Object playerObj) {
        if (playerObj instanceof ServerPlayer player) {
            net.minecraft.server.level.ServerBossEvent bossEvent = bossBars.remove(player.getUUID());
            if (bossEvent != null) {
                bossEvent.removePlayer(player);
            }
        }
    }

    @Override
    public void kickPlayer(Object playerObj, String reason) {
        if (playerObj instanceof ServerPlayer player) {
            player.connection.disconnect(Component.literal(reason));
        }
    }
}
