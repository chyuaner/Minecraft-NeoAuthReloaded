package tw.yuaner.neoauth.forge.mixin;

import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.yuaner.neoauth.util.VanishmodIntegration;

import java.lang.reflect.Field;

/**
 * 攔截由 NeoAuth 靜默控制 Vanishmod 時，伺服器傳給玩家的 ActionBar 封包 (Forge 1.20.1)。
 * 1.20.1 尚無 ServerCommonPacketListenerImpl，send() 方法在 ServerGamePacketListenerImpl 的父類別中。
 * 透過 @Pseudo 允許在 SRG 混淆映射下找到此方法。
 */
@Pseudo
@Mixin(ServerGamePacketListenerImpl.class)
@SuppressWarnings({"mapping", "unresolvable-target", "MixinAnnotationTarget"})
public abstract class ForgeServerCommonPacketListenerMixin {

    /**
     * 抑制由 NeoAuth 靜默控制 Vanishmod 時產生的 ActionBar 訊息。
     */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void neoauth$suppressVanishActionBar(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (packet instanceof ClientboundSetActionBarTextPacket) {
            ServerPlayer player = neoauth$getPlayer();
            if (player != null && VanishmodIntegration.isSilentlyVanishing(player.getUUID())) {
                ci.cancel();
            }
        }
    }

    @Unique
    private ServerPlayer neoauth$getPlayer() {
        for (String name : new String[]{"f_9743_", "player", "field_9770"}) {
            try {
                Field f = ServerGamePacketListenerImpl.class.getDeclaredField(name);
                f.setAccessible(true);
                Object val = f.get(this);
                if (val instanceof ServerPlayer sp) return sp;
            } catch (Exception ignored) {
            }
        }
        for (Field field : ServerGamePacketListenerImpl.class.getDeclaredFields()) {
            if (ServerPlayer.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    Object val = field.get(this);
                    if (val instanceof ServerPlayer sp) return sp;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
