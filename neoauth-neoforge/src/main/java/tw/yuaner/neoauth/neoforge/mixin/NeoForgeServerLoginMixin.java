package tw.yuaner.neoauth.neoforge.mixin;

import com.google.common.primitives.Ints;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.Crypt;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.platform.Services;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 伺服器登入封包監聽器 Mixin (NeoForge 1.21.1)。
 * <p>
 * 支援在線上伺服器模式 (online-mode=true) 與離線伺服器模式 (online-mode=false) 下的動態正版驗證。
 * 在離線模式下發起加密握手以自動識別正版玩家並免密登入，皮膚由 SkinRestorer 等皮膚模組接管。
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class NeoForgeServerLoginMixin {

    @Shadow
    @Final
    MinecraftServer server;

    @Shadow
    @Final
    Connection connection;

    @Shadow
    private byte[] challenge;

    @Shadow
    abstract void startClientVerification(GameProfile authenticatedProfile);

    @Shadow
    public abstract void handleHello(ServerboundHelloPacket packet);

    @Unique
    private static KeyPair neoauth$KEY_PAIR;

    @Unique
    private static final Map<Object, ServerboundHelloPacket> neoauth$PENDING = new ConcurrentHashMap<>();

    @Unique
    private static final Set<Object> neoauth$HANDLED = ConcurrentHashMap.newKeySet();

    @Unique
    private static final ScheduledExecutorService neoauth$WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NeoAuth-Login-Watchdog");
        t.setDaemon(true);
        return t;
    });

    @Unique
    private static synchronized KeyPair neoauth$getKeyPair(MinecraftServer server) {
        KeyPair kp = server.getKeyPair();
        if (kp != null) return kp;
        if (neoauth$KEY_PAIR == null) {
            try {
                neoauth$KEY_PAIR = Crypt.generateKeyPair();
            } catch (Exception e) {
                return null;
            }
        }
        return neoauth$KEY_PAIR;
    }

    /**
     * 攔截 Hello 登入握手封包。
     */
    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void neoauth$handleHello(ServerboundHelloPacket packet, CallbackInfo ci) {
        String username = packet.name();
        if (username == null || username.isBlank()) return;

        UUID uuid = packet.profileId();
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));

        // 情況 1：伺服器為 online-mode=true（線上模式），離線客戶端嘗試連線
        if (this.server.usesAuthentication()) {
            if (offlineUuid.equals(uuid) || uuid == null) {
                if (Services.PLATFORM.getConfig().isAllowOfflinePlayers()) {
                    GameProfile offlineProfile = new GameProfile(offlineUuid, username);
                    this.startClientVerification(offlineProfile);
                    ci.cancel();
                }
            }
            return;
        }

        // 情況 2：伺服器為 online-mode=false（離線模式），啟用動態正版握手
        if (Services.PLATFORM.getConfig().isDynamicPremiumVerification()) {
            if (neoauth$HANDLED.contains(this)) {
                return; // 已經驗證過或回退，放行原版離線處理
            }

            try {
                KeyPair keyPair = neoauth$getKeyPair(this.server);
                if (this.challenge == null || this.challenge.length == 0) {
                    this.challenge = Ints.toByteArray(RandomSource.create().nextInt());
                }

                PublicKey publicKey = keyPair.getPublic();
                ClientboundHelloPacket helloPacket = new ClientboundHelloPacket("", publicKey.getEncoded(), this.challenge, true);
                this.connection.send(helloPacket);
                neoauth$PENDING.put(this, packet);
                neoauth$setLoginState("KEY");
                ci.cancel();

                // 看門狗定時器：若客戶端為離線版且未回應密鑰握手，6 秒後自動回退至離線模式
                neoauth$WATCHDOG.schedule(() -> neoauth$fallbackToOffline(this, packet), 6, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 攔截 Key 密鑰回應封包（動態正版握手階段）。
     */
    @Inject(method = "handleKey", at = @At("HEAD"), cancellable = true)
    private void neoauth$handleKey(ServerboundKeyPacket packet, CallbackInfo ci) {
        ServerboundHelloPacket hello = neoauth$PENDING.remove(this);
        if (hello == null) {
            return; // 非動態握手，走原版流程
        }

        ci.cancel();

        try {
            KeyPair keyPair = neoauth$getKeyPair(this.server);
            PrivateKey privateKey = keyPair.getPrivate();
            PublicKey publicKey = keyPair.getPublic();

            if (!packet.isChallengeValid(this.challenge, privateKey)) {
                neoauth$fallbackToOffline(this, hello);
                return;
            }

            SecretKey secretKey = packet.getSecretKey(privateKey);
            Cipher decrypt = Crypt.getCipher(2, secretKey);
            Cipher encrypt = Crypt.getCipher(1, secretKey);
            this.connection.setEncryptionKey(decrypt, encrypt);

            byte[] digestBytes = Crypt.digestData("", publicKey, secretKey);
            String digest = new BigInteger(digestBytes).toString(16);

            String username = hello.name();
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));

            SocketAddress remote = this.connection.getRemoteAddress();
            InetAddress address = (remote instanceof InetSocketAddress isa) ? isa.getAddress() : null;

            CompletableFuture.runAsync(() -> {
                try {
                    ProfileResult profileResult = this.server.getSessionService().hasJoinedServer(username, digest, address);
                    if (profileResult != null && profileResult.profile() != null) {
                        AuthManager.markPremiumVerified(offlineUuid);
                    }
                } catch (Exception ignored) {
                } finally {
                    neoauth$HANDLED.add(this);
                    neoauth$replayHello(this, hello);
                }
            });
        } catch (Exception e) {
            neoauth$fallbackToOffline(this, hello);
        }
    }

    /**
     * 攔截斷線清理。
     */
    @Inject(method = "onDisconnect", at = @At("HEAD"), require = 0)
    private void neoauth$onDisconnect(DisconnectionDetails details, CallbackInfo ci) {
        neoauth$PENDING.remove(this);
        neoauth$HANDLED.remove(this);
    }

    /**
     * 線上模式 (online-mode=true) 下標記通過 Mojang 驗證的正版玩家以實現免密自動登入。
     */
    @Inject(method = "startClientVerification", at = @At("HEAD"))
    private void neoauth$onStartClientVerification(GameProfile profile, CallbackInfo ci) {
        if (profile != null && profile.getProperties() != null && profile.getProperties().containsKey("textures")) {
            AuthManager.markPremiumVerified(profile.getId());
        }
    }

    @Unique
    private void neoauth$fallbackToOffline(Object handler, ServerboundHelloPacket hello) {
        if (neoauth$PENDING.remove(handler) != null || !neoauth$HANDLED.contains(handler)) {
            neoauth$HANDLED.add(handler);
            neoauth$replayHello(handler, hello);
        }
    }

    @Unique
    private void neoauth$replayHello(Object handler, ServerboundHelloPacket hello) {
        Runnable task = () -> {
            try {
                neoauth$setLoginState("HELLO");
                this.handleHello(hello);
            } catch (Exception ignored) {
            }
        };

        if (this.connection.channel() != null && this.connection.channel().eventLoop() != null) {
            this.connection.channel().eventLoop().execute(task);
        } else {
            task.run();
        }
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean neoauth$setLoginState(String stateName) {
        for (String name : new String[]{"state", "f_10019_", "field_14163"}) {
            try {
                Field f = ServerLoginPacketListenerImpl.class.getDeclaredField(name);
                f.setAccessible(true);
                Object val = Enum.valueOf((Class<? extends Enum>) f.getType(), stateName);
                f.set(this, val);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
