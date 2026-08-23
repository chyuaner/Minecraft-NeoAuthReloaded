package tw.yuaner.neoauth.neoforge.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.platform.Services;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 伺服器登入封包監聽器 Mixin (NeoForge 1.21.1)。
 * <p>
 * 用於在線上伺服器模式 (online-mode=true) 下，攔截離線玩家連線並允許其以離線 UUID 進入伺服器（混合模式），
 * 同時標記具有官方 Skin / 紋理的正版玩家。
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class NeoForgeServerLoginMixin {

    @Shadow
    @Final
    MinecraftServer server;

    @Shadow
    abstract void startClientVerification(GameProfile authenticatedProfile);

    /**
     * 攔截 Hello 登入握手封包。
     */
    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void neoauth$handleHello(ServerboundHelloPacket packet, CallbackInfo ci) {
        String username = packet.name();
        UUID uuid = packet.profileId();
        if (username == null) return;

        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));

        // 若為離線客戶端嘗試連線至 online-mode=true 伺服器
        if (offlineUuid.equals(uuid) || uuid == null) {
            if (this.server.usesAuthentication()) {
                if (Services.PLATFORM.getConfig().isAllowOfflinePlayers()) {
                    GameProfile offlineProfile = new GameProfile(offlineUuid, username);
                    this.startClientVerification(offlineProfile);
                    ci.cancel();
                }
            }
        }
    }

    /**
     * 攔截登入完成階段，標記通過 Mojang 線上驗證的正版玩家，
     * 若啟用 keepOfflineUuidCompatibility 則動態替換為離線 UUID 相容 Profile。
     */
    @ModifyVariable(method = "finishLoginAndWaitForClient", at = @At("HEAD"), argsOnly = true)
    private GameProfile neoauth$onMojangVerifiedProfile(GameProfile profile) {
        if (profile == null) return null;

        // 若帶有 Mojang Session 簽署之 textures 屬性，表示為正版驗證登入
        if (profile.getProperties() != null && profile.getProperties().containsKey("textures")) {
            if (Services.PLATFORM.getConfig().isKeepOfflineUuidCompatibility()) {
                String username = profile.getName();
                UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
                AuthManager.markPremiumVerified(offlineUuid);

                // 保持離線 UUID 相容：建立帶有離線 UUID 的 Profile，但完整保留 Mojang 的 textures/Skin 屬性
                GameProfile offlineCompatibleProfile = new GameProfile(offlineUuid, username);
                offlineCompatibleProfile.getProperties().putAll(profile.getProperties());
                return offlineCompatibleProfile;
            } else {
                AuthManager.markPremiumVerified(profile.getId());
            }
        }
        return profile;
    }
}
