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
 * 同時支援 Mojang 映射與 Forge 執行期 SRG 混淆映射，
 * 在 online-mode=true 時安全攔截離線模式玩家並允許混合登入。
 */
@Pseudo
@Mixin(ServerLoginPacketListenerImpl.class)
@SuppressWarnings({"mapping", "unresolvable-target", "MixinAnnotationTarget"})
public abstract class ForgeServerLoginMixin {

    /**
     * 攔截 Hello 登入握手封包 (Forge 1.20.1，m_5990_ 為 ServerLoginPacketListenerImpl.handleHello 的 SRG 名稱)。
     */
    @Inject(
            method = {
                    "handleHello(Lnet/minecraft/network/protocol/login/ServerboundHelloPacket;)V",
                    "handleHello",
                    "m_5990_"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void neoauth$handleHello(ServerboundHelloPacket packet, CallbackInfo ci) {
        String username = packet.name();
        if (username == null || username.isBlank()) return;

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
     * 攔截登入完成階段，標記通過 Mojang 線上驗證的正版玩家 (m_10055_ 為 1.20.1 handleAcceptedLogin 的 SRG 名稱)。
     */
    @Inject(
            method = {
                    "handleAcceptedLogin",
                    "m_10055_"
            },
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void neoauth$onAcceptedLogin(CallbackInfo ci) {
        GameProfile profile = getGameProfile();
        if (profile == null) return;

        // 若帶有 Mojang Session 簽署之 textures 屬性，表示為正版驗證登入
        if (profile.getProperties() != null && profile.getProperties().containsKey("textures")) {
            if (Services.PLATFORM.getConfig().isKeepOfflineUuidCompatibility()) {
                String username = profile.getName();
                UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
                AuthManager.markPremiumVerified(offlineUuid);

                // 保持離線 UUID 相容：建立帶有離線 UUID 的 Profile，但完整保留 Mojang 的 textures/Skin 屬性
                GameProfile offlineCompatibleProfile = new GameProfile(offlineUuid, username);
                offlineCompatibleProfile.getProperties().putAll(profile.getProperties());
                setGameProfile(offlineCompatibleProfile);
            } else {
                AuthManager.markPremiumVerified(profile.getId());
            }
        }
    }

    private boolean setGameProfile(GameProfile profile) {
        for (String name : new String[]{"f_10021_", "gameProfile", "profile", "authenticatedProfile", "field_14160"}) {
            try {
                Field f = ServerLoginPacketListenerImpl.class.getDeclaredField(name);
                f.setAccessible(true);
                f.set(this, profile);
                return true;
            } catch (Exception ignored) {
            }
        }
        for (Field field : ServerLoginPacketListenerImpl.class.getDeclaredFields()) {
            if (GameProfile.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    field.set(this, profile);
                    return true;
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    private MinecraftServer getServerInstance() {
        for (String name : new String[]{"f_10018_", "server", "field_14165"}) {
            try {
                Field f = ServerLoginPacketListenerImpl.class.getDeclaredField(name);
                f.setAccessible(true);
                Object obj = f.get(this);
                if (obj instanceof MinecraftServer ms) return ms;
            } catch (Exception ignored) {
            }
        }
        for (Field field : ServerLoginPacketListenerImpl.class.getDeclaredFields()) {
            if (MinecraftServer.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    Object obj = field.get(this);
                    if (obj instanceof MinecraftServer ms) return ms;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private GameProfile getGameProfile() {
        for (String name : new String[]{"f_10021_", "gameProfile", "profile", "authenticatedProfile", "field_14160"}) {
            try {
                Field f = ServerLoginPacketListenerImpl.class.getDeclaredField(name);
                f.setAccessible(true);
                Object obj = f.get(this);
                if (obj instanceof GameProfile gp) return gp;
            } catch (Exception ignored) {
            }
        }
        for (Field field : ServerLoginPacketListenerImpl.class.getDeclaredFields()) {
            if (GameProfile.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    Object obj = field.get(this);
                    if (obj instanceof GameProfile gp) return gp;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean acceptOfflineLogin(GameProfile offlineProfile) {
        Field stateField = null;
        for (String name : new String[]{"f_10019_", "state", "field_14163"}) {
            try {
                stateField = ServerLoginPacketListenerImpl.class.getDeclaredField(name);
                break;
            } catch (Exception ignored) {
            }
        }
        if (stateField == null) {
            for (Field field : ServerLoginPacketListenerImpl.class.getDeclaredFields()) {
                if (field.getType().isEnum()) {
                    stateField = field;
                    break;
                }
            }
        }

        Field profileField = null;
        for (String name : new String[]{"f_10021_", "gameProfile", "profile", "authenticatedProfile", "field_14160"}) {
            try {
                profileField = ServerLoginPacketListenerImpl.class.getDeclaredField(name);
                break;
            } catch (Exception ignored) {
            }
        }
        if (profileField == null) {
            for (Field field : ServerLoginPacketListenerImpl.class.getDeclaredFields()) {
                if (GameProfile.class.isAssignableFrom(field.getType())) {
                    profileField = field;
                    break;
                }
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
