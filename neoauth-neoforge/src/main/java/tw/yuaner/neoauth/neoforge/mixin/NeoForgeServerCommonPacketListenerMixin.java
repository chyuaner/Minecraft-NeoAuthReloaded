package tw.yuaner.neoauth.neoforge.mixin;

import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.yuaner.neoauth.util.VanishmodIntegration;

/**
 * 攔截由 NeoAuth 靜默控制 Vanishmod 時，伺服器傳給玩家的 ActionBar 封包。
 * send() 方法定義在 ServerCommonPacketListenerImpl。
 * 不使用 @Shadow，改以 instanceof 向下轉型取得 player（與 Vanishmod 自身的 ServerCommonPacketListenerImplMixin 同樣做法）。
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class NeoForgeServerCommonPacketListenerMixin {

    /**
     * 抑制由 NeoAuth 靜默控制 Vanishmod 時產生的 ActionBar 訊息。
     * 僅在 VanishmodIntegration.isSilentlyVanishing(uuid) 回傳 true 時取消封包，
     * 不影響玩家自行操作 /v 所產生的 Vanishmod 原生提示。
     */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void neoauth$suppressVanishActionBar(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (packet instanceof ClientboundSetActionBarTextPacket
                && (Object) this instanceof ServerGamePacketListenerImpl gameListener) {
            ServerPlayer player = gameListener.player;
            if (player != null && VanishmodIntegration.isSilentlyVanishing(player.getUUID())) {
                ci.cancel();
            }
        }
    }
}
