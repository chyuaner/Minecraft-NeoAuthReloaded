package tw.yuaner.neoauth.forge.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.platform.Services;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 伺服器登入封包監聽器 Mixin (Forge 1.20.1)。
 * <p>
 * 使用 @Pseudo 與反射存取內部欄位，完全相容 Forge 執行期的混淆欄位映射，
 * 避免因缺少 RefMap 或欄位混淆導致伺服器崩潰。
 */
@Pseudo
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ForgeServerLoginMixin {

    /**
     * 攔截 Hello 登入握手封包 (Forge 1.20.1)。
     */
    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true, require = 0)
    private void neoauth$handleHello(ServerboundHelloPacket packet, CallbackInfo ci) {
        String username = packet.name();
        if (username == null) return;

        UUID uuid = packet.profileId().orElse(null);
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));

        // 若為離線客戶端嘗試連線至 online-mode=true 伺服器
        if (offlineUuid.equals(uuid) || uuid == null) {
            MinecraftServer server = getServerInstance();
            if (server != null && server.usesAuthentication()) {
                if (Services.PLATFORM.getConfig().isAllowOfflinePlayers()) {
                    GameProfile offlineProfile = new GameProfile(offlineUuid, username);
                    if (acceptOfflineLogin(offlineProfile)) {
                        ci.cancel();
                    }
                }
            }
        }
    }

    /**
     * 攔截登入完成階段，標記通過 Mojang 線上驗證的正版玩家 (相容多種 Forge/Mojang 方法名稱)。
     */
    @Inject(method = {"handleAcceptedLogin", "verifyLoginAndFinishConnectionSetup"}, at = @At("HEAD"), require = 0)
    private void neoauth$onAcceptedLogin(CallbackInfo ci) {
        GameProfile profile = getGameProfile();
        if (profile == null) return;

        // 若帶有 Mojang Session 簽署之 textures 屬性，表示為正版驗證登入
        if (profile.getProperties() != null && profile.getProperties().containsKey("textures")) {
            AuthManager.markPremiumVerified(profile.getId());
        }
    }

    private MinecraftServer getServerInstance() {
        for (Field field : ServerLoginPacketListenerImpl.class.getDeclaredFields()) {
            if (MinecraftServer.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return (MinecraftServer) field.get(this);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private GameProfile getGameProfile() {
        for (Field field : ServerLoginPacketListenerImpl.class.getDeclaredFields()) {
            if (GameProfile.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return (GameProfile) field.get(this);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean acceptOfflineLogin(GameProfile offlineProfile) {
        Field stateField = null;
        Field profileField = null;

        for (Field field : ServerLoginPacketListenerImpl.class.getDeclaredFields()) {
            if (field.getType().isEnum() && field.getType().getName().contains("State")) {
                stateField = field;
            } else if (GameProfile.class.isAssignableFrom(field.getType())) {
                profileField = field;
            }
        }

        if (stateField != null && profileField != null) {
            try {
                stateField.setAccessible(true);
                profileField.setAccessible(true);
                Object readyState = Enum.valueOf((Class<? extends Enum>) stateField.getType(), "READY_TO_ACCEPT");
                stateField.set(this, readyState);
                profileField.set(this, offlineProfile);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
