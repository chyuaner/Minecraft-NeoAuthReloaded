package tw.yuaner.neoauth.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.platform.IPlatformHelper;

/**
 * Minecraft 1.21.1 NeoForge 平台的服務實作。
 * <p>
 * 負責透過 1.21.1 的 NeoForge / Minecraft API 實作效果給予、解除與訊息發送。
 */
public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public IAuthConfig getConfig() {
        return NeoForgeConfig.INSTANCE;
    }

    @Override
    public String getPlatformName() {
        return "NeoForge (1.21.1)";
    }

    @Override
    public void applyFreezeEffects(Object playerObj) {
        if (playerObj instanceof ServerPlayer player) {
            // 在 1.21.1 中，MobEffects 均以 Holder<MobEffect> 形式提供
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 255, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 2, 250, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 2, 255, false, false, false));
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
            player.sendSystemMessage(Component.literal(message));
        }
    }

    @Override
    public void sendActionBar(Object playerObj, String message) {
        if (playerObj instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }
}
