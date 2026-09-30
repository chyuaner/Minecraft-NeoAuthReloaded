package tw.yuaner.neoauth.forge.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.yuaner.neoauth.util.ServerHostResolver;

/**
 * 攔截客戶端連線握手意圖封包 (ClientIntentionPacket)，記錄玩家連線時所輸入的伺服器網址 (Server Host)。
 */
@Mixin(ServerHandshakePacketListenerImpl.class)
public class ForgeServerHandshakePacketListenerMixin {

    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "handleIntention", at = @At("HEAD"))
    private void onHandleIntention(ClientIntentionPacket packet, CallbackInfo ci) {
        ServerHostResolver.recordHandshake(this.connection, packet);
    }
}
