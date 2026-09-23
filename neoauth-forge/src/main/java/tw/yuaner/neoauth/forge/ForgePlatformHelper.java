package tw.yuaner.neoauth.forge;

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
 * Minecraft 1.20.1 Forge 平台的服務實作。
 * <p>
 * 負責透過 1.20.1 的 Forge / Minecraft API 實作效果給予、解除與訊息發送。
 */
public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public IAuthConfig getConfig() {
        return ConfigManager.getInstance().getConfig();
    }

    @Override
    public String getPlatformName() {
        return "Forge (1.20.1)";
    }

    @Override
    public String getModVersion() {
        try {
            if (net.minecraftforge.fml.ModList.get() != null) {
                var container = net.minecraftforge.fml.ModList.get().getModContainerById("neoauthreloaded");
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
            // 根據設定施加緩速、跳躍抑制與失明效果
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
                ResourceLocation dimLoc = new ResourceLocation(worldName);
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
}
