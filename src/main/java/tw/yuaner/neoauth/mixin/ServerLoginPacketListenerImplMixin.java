package tw.yuaner.neoauth.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.Config;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {

    @Shadow
    @Final
    MinecraftServer server;

    @Shadow
    abstract void startClientVerification(GameProfile authenticatedProfile);

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void neoauth$handleHello(ServerboundHelloPacket packet, CallbackInfo ci) {
        String username = packet.name();
        UUID uuid = packet.profileId();
        if (username == null) return;

        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));

        // Intercept offline-mode players connecting to an online-mode server
        if (offlineUuid.equals(uuid) || uuid == null) {
            if (this.server.usesAuthentication()) {
                if (Config.SERVER.allowOfflinePlayers.get()) {
                    GameProfile offlineProfile = new GameProfile(offlineUuid, username);
                    this.startClientVerification(offlineProfile);
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "finishLoginAndWaitForClient", at = @At("HEAD"))
    private void neoauth$onMojangVerifiedProfile(GameProfile profile, CallbackInfo ci) {
        if (profile == null) return;

        // Premium profiles authenticated by Mojang session service carry texture properties
        if (profile.getProperties() != null && profile.getProperties().containsKey("textures")) {
            AuthManager.markPremiumVerified(profile.getId());
        }
    }
}
