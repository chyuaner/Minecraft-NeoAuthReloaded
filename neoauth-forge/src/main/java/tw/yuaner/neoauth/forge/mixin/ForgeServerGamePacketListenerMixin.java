package tw.yuaner.neoauth.forge.mixin;

import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.SignatureValidator;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * 攔截聊天 Session 更新，防止外置站 (Blessing Skin / authlib-injector) 與離線玩家因公鑰非 Mojang 簽發而遭伺服器誤踢 (Forge 1.20.1)。
 * <p>
 * 同時相容 Mojang 映射與 Forge 執行期 SRG 混淆映射，避免因缺少 refMap 導致 Shadow 欄位無法定位。
 */
@Pseudo
@Mixin(ServerGamePacketListenerImpl.class)
@SuppressWarnings({"mapping", "unresolvable-target", "MixinAnnotationTarget"})
public abstract class ForgeServerGamePacketListenerMixin {

    @Inject(
            method = {
                    "handleChatSessionUpdate(Lnet/minecraft/network/protocol/game/ServerboundChatSessionUpdatePacket;)V",
                    "handleChatSessionUpdate",
                    "m_252797_"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void neoauth$handleChatSessionUpdate(ServerboundChatSessionUpdatePacket packet, CallbackInfo ci) {
        ServerPlayer player = neoauth$getPlayer();
        MinecraftServer server = (player != null) ? player.getServer() : null;
        if (server != null && !server.enforceSecureProfile()) {
            try {
                RemoteChatSession.Data data = packet.chatSession();
                RemoteChatSession currentSession = neoauth$getChatSession();
                ProfilePublicKey.Data oldKey = currentSession != null ? currentSession.profilePublicKey().data() : null;
                ProfilePublicKey.Data newKey = data.profilePublicKey();
                if (!Objects.equals(oldKey, newKey)) {
                    SignatureValidator validator = server.getProfileKeySignatureValidator();
                    if (validator != null) {
                        try {
                            RemoteChatSession newSession = data.validate(player.getGameProfile(), validator, ProfilePublicKey.EXPIRY_GRACE_PERIOD);
                            neoauth$setChatSession(newSession);
                        } catch (ProfilePublicKey.ValidationException e) {
                            // 非 Mojang 官方簽名公鑰在非強制安全設定檔下靜默忽略，允許以一般模式聊天而不踢出
                            neoauth$setChatSession(null);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            ci.cancel();
        }
    }

    @Unique
    private ServerPlayer neoauth$getPlayer() {
        for (String name : new String[]{"f_9743_", "player", "field_9770"}) {
            try {
                Field f = ServerGamePacketListenerImpl.class.getDeclaredField(name);
                f.setAccessible(true);
                Object obj = f.get(this);
                if (obj instanceof ServerPlayer sp) return sp;
            } catch (Exception ignored) {
            }
        }
        for (Field field : ServerGamePacketListenerImpl.class.getDeclaredFields()) {
            if (ServerPlayer.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    Object obj = field.get(this);
                    if (obj instanceof ServerPlayer sp) return sp;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    @Unique
    private RemoteChatSession neoauth$getChatSession() {
        for (String name : new String[]{"f_252494_", "chatSession", "field_241804"}) {
            try {
                Field f = ServerGamePacketListenerImpl.class.getDeclaredField(name);
                f.setAccessible(true);
                Object obj = f.get(this);
                if (obj instanceof RemoteChatSession cs) return cs;
            } catch (Exception ignored) {
            }
        }
        for (Field field : ServerGamePacketListenerImpl.class.getDeclaredFields()) {
            if (RemoteChatSession.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    Object obj = field.get(this);
                    if (obj instanceof RemoteChatSession cs) return cs;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    @Unique
    private void neoauth$setChatSession(RemoteChatSession session) {
        for (String name : new String[]{"f_252494_", "chatSession", "field_241804"}) {
            try {
                Field f = ServerGamePacketListenerImpl.class.getDeclaredField(name);
                f.setAccessible(true);
                f.set(this, session);
                return;
            } catch (Exception ignored) {
            }
        }
        for (Field field : ServerGamePacketListenerImpl.class.getDeclaredFields()) {
            if (RemoteChatSession.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    field.set(this, session);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
    }
}
