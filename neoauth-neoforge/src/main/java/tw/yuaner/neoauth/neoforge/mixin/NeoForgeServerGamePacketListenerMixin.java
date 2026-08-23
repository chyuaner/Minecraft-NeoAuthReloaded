package tw.yuaner.neoauth.neoforge.mixin;

import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.SignatureValidator;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 攔截聊天 Session 更新，防止外置站 (Blessing Skin / authlib-injector) 與離線玩家因公鑰非 Mojang 簽發而遭伺服器誤踢。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class NeoForgeServerGamePacketListenerMixin {

    @Shadow
    public ServerPlayer player;

    @Shadow
    @Nullable
    private RemoteChatSession chatSession;

    @Inject(method = "handleChatSessionUpdate", at = @At("HEAD"), cancellable = true)
    private void neoauth$handleChatSessionUpdate(ServerboundChatSessionUpdatePacket packet, CallbackInfo ci) {
        MinecraftServer server = (this.player != null) ? this.player.getServer() : null;
        if (server != null && !server.enforceSecureProfile()) {
            try {
                RemoteChatSession.Data data = packet.chatSession();
                ProfilePublicKey.Data oldKey = this.chatSession != null ? this.chatSession.profilePublicKey().data() : null;
                ProfilePublicKey.Data newKey = data.profilePublicKey();
                if (!Objects.equals(oldKey, newKey)) {
                    SignatureValidator validator = server.getProfileKeySignatureValidator();
                    if (validator != null) {
                        try {
                            this.chatSession = data.validate(this.player.getGameProfile(), validator);
                        } catch (ProfilePublicKey.ValidationException e) {
                            // 非 Mojang 官方簽名公鑰在非強制安全設定檔下靜默忽略，允許以一般模式聊天而不踢出
                            this.chatSession = null;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            ci.cancel();
        }
    }
}
