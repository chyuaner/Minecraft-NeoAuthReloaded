package tw.yuaner.neoauth.forge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.platform.IPlatformHelper;

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
