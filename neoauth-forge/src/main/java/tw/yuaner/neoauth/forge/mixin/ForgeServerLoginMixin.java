package tw.yuaner.neoauth.forge.mixin;

import com.google.common.primitives.Ints;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.Crypt;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.DatabaseManager;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.core.AuthLogic;
import tw.yuaner.neoauth.platform.Services;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 伺服器登入封包監聽器 Mixin (Forge 1.20.1)。
 * <p>
 * 同時支援 Mojang 映射與 Forge 執行期 SRG 混淆映射，
 * 支援在 online-mode=true 與 online-mode=false 下的動態正版驗證與免密登入。
 */
@Pseudo
@Mixin(ServerLoginPacketListenerImpl.class)
@SuppressWarnings({"mapping", "unresolvable-target", "MixinAnnotationTarget"})
public abstract class ForgeServerLoginMixin {

    @Unique
    private static final Logger neoauth$LOGGER = LoggerFactory.getLogger("NeoAuth-Login");

    @Unique
    private static KeyPair neoauth$KEY_PAIR;

    @Unique
    private static final Map<Object, ServerboundHelloPacket> neoauth$PENDING = new ConcurrentHashMap<>();

    @Unique
    private static final Map<Object, ScheduledFuture<?>> neoauth$WATCHDOG_TASKS = new ConcurrentHashMap<>();

    @Unique
    private static final Set<Object> neoauth$HANDLED = ConcurrentHashMap.newKeySet();

    @Unique
    private static final ScheduledExecutorService neoauth$WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NeoAuth-Forge-Login-Watchdog");
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
        MinecraftServer server = getServerInstance();
        if (server == null) return;

        // 情況 1：伺服器為 online-mode=true，離線客戶端連線
        if (server.usesAuthentication()) {
            if (offlineUuid.equals(uuid) || uuid == null) {
                if (Services.PLATFORM.getConfig().isAllowOfflinePlayers()) {
                    GameProfile offlineProfile = new GameProfile(offlineUuid, username);
                    if (acceptOfflineLogin(offlineProfile)) {
                        ci.cancel();
                    }
                }
            }
            return;
        }

        // 情況 2：伺服器為 online-mode=false，啟用動態正版握手
        if (Services.PLATFORM.getConfig().isDynamicPremiumVerification()) {
            if (neoauth$HANDLED.contains(this)) {
                return;
            }

            try {
                KeyPair keyPair = neoauth$getKeyPair(server);
                byte[] challenge = getChallenge();
                if (challenge == null || challenge.length == 0) {
                    challenge = Ints.toByteArray(RandomSource.create().nextInt());
                    setChallenge(challenge);
                }

                Connection connection = getConnection();
                if (keyPair != null && connection != null) {
                    PublicKey publicKey = keyPair.getPublic();
                    ClientboundHelloPacket helloPacket = new ClientboundHelloPacket("", publicKey.getEncoded(), challenge);
                    connection.send(helloPacket);
                    neoauth$PENDING.put(this, packet);
                    setLoginState("KEY");
                    ci.cancel();

                    int timeout = Services.PLATFORM.getConfig().getDynamicVerificationTimeout();
                    if (timeout <= 0) timeout = 15;

                    ScheduledFuture<?> watchdogTask = neoauth$WATCHDOG.schedule(() -> neoauth$fallbackToOffline(this, packet), timeout, TimeUnit.SECONDS);
                    neoauth$WATCHDOG_TASKS.put(this, watchdogTask);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 攔截 Key 密鑰回應封包 (Forge 1.20.1，m_7223_ 為 handleKey 的 SRG 名稱)。
     */
    @Inject(
            method = {
                    "handleKey(Lnet/minecraft/network/protocol/login/ServerboundKeyPacket;)V",
                    "handleKey",
                    "m_7223_"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void neoauth$handleKey(ServerboundKeyPacket packet, CallbackInfo ci) {
        ServerboundHelloPacket hello = neoauth$PENDING.remove(this);
        ScheduledFuture<?> watchdog = neoauth$WATCHDOG_TASKS.remove(this);
        if (watchdog != null) {
            watchdog.cancel(false);
        }

        if (hello == null) {
            return;
        }

        ci.cancel();

        try {
            MinecraftServer server = getServerInstance();
            Connection connection = getConnection();
            if (server == null || connection == null) {
                neoauth$fallbackToOffline(this, hello);
                return;
            }

            KeyPair keyPair = neoauth$getKeyPair(server);
            PrivateKey privateKey = keyPair.getPrivate();
            PublicKey publicKey = keyPair.getPublic();
            byte[] challenge = getChallenge();

            if (!packet.isChallengeValid(challenge, privateKey)) {
                neoauth$LOGGER.warn("NeoAuth: 連線 challenge 驗證不匹配，降級為離線玩家: {}", hello.name());
                neoauth$fallbackToOffline(this, hello);
                return;
            }

            SecretKey secretKey = packet.getSecretKey(privateKey);
            Cipher decrypt = Crypt.getCipher(2, secretKey);
            Cipher encrypt = Crypt.getCipher(1, secretKey);
            connection.setEncryptionKey(decrypt, encrypt);

            byte[] digestBytes = Crypt.digestData("", publicKey, secretKey);
            String digest = new BigInteger(digestBytes).toString(16);

            String username = hello.name();
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));

            SocketAddress remote = connection.getRemoteAddress();
            InetAddress address = (remote instanceof InetSocketAddress isa) ? isa.getAddress() : null;

            CompletableFuture.runAsync(() -> {
                try {
                    GameProfile profile = server.getSessionService().hasJoinedServer(new GameProfile(null, username), digest, address);
                    if (profile != null) {
                        AuthManager.markPremiumVerified(offlineUuid);
                        neoauth$LOGGER.info("NeoAuth: 正版驗證成功 ({})，已標記免密自動登入。", username);

                        server.execute(() -> {
                            ServerPlayer player = server.getPlayerList().getPlayer(offlineUuid);
                            if (player != null && !AuthManager.isLoggedIn(offlineUuid)) {
                                boolean autoLoggedIn = AuthLogic.handlePlayerJoin(offlineUuid, username, player.getIpAddress(), true);
                                if (autoLoggedIn) {
                                    Services.PLATFORM.removeFreezeEffects(player);
                                    if (DatabaseManager.isRegistered(username)) {
                                        String msg = ConfigManager.getInstance().getMessagesManager().get("general.welcome_premium", username);
                                        Services.PLATFORM.sendMessage(player, msg);
                                        AuthLogic.executeHooks(player.getServer(), player, username,
                                                ConfigManager.getInstance().getCommandsConfig().getOnLoginConsole(),
                                                ConfigManager.getInstance().getCommandsConfig().getOnLoginPlayer());
                                    }
                                }
                            }
                        });
                    } else {
                        neoauth$LOGGER.info("NeoAuth: Mojang 未確認玩家 ({}) 的正版 Session，以離線模式登入。", username);
                    }
                } catch (Exception e) {
                    neoauth$LOGGER.error("NeoAuth: 查詢 Mojang Session 伺服器時發生異常 (" + username + "): " + e.getMessage(), e);
                } finally {
                    neoauth$HANDLED.add(this);
                    neoauth$replayHello(this, hello);
                }
            });
        } catch (Exception e) {
            neoauth$LOGGER.error("NeoAuth: 密鑰握手解密發生錯誤: " + e.getMessage(), e);
            neoauth$fallbackToOffline(this, hello);
        }
    }

    /**
     * 攔截斷線清理。
     */
    @Inject(
            method = {
                    "onDisconnect(Lnet/minecraft/network/chat/Component;)V",
                    "onDisconnect",
                    "m_7026_"
            },
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void neoauth$onDisconnect(CallbackInfo ci) {
        ScheduledFuture<?> watchdog = neoauth$WATCHDOG_TASKS.remove(this);
        if (watchdog != null) {
            watchdog.cancel(false);
        }
        neoauth$PENDING.remove(this);
        neoauth$HANDLED.remove(this);
    }

    /**
     * 線上模式 (online-mode=true) 下標記通過 Mojang 驗證的正版玩家以實現免密自動登入。
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
        if (profile != null && profile.getProperties() != null && profile.getProperties().containsKey("textures")) {
            AuthManager.markPremiumVerified(profile.getId());
        }
    }

    @Unique
    private void neoauth$fallbackToOffline(Object handler, ServerboundHelloPacket hello) {
        ScheduledFuture<?> watchdog = neoauth$WATCHDOG_TASKS.remove(handler);
        if (watchdog != null) {
            watchdog.cancel(false);
        }
        if (neoauth$PENDING.remove(handler) != null) {
            neoauth$HANDLED.add(handler);
            neoauth$replayHello(handler, hello);
        }
    }

    @Unique
    private void neoauth$replayHello(Object handler, ServerboundHelloPacket hello) {
        Runnable task = () -> {
            try {
                setLoginState("HELLO");
                Method handleHelloMethod = null;
                for (String name : new String[]{"handleHello", "m_5990_"}) {
                    try {
                        handleHelloMethod = ServerLoginPacketListenerImpl.class.getDeclaredMethod(name, ServerboundHelloPacket.class);
                        break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                if (handleHelloMethod != null) {
                    handleHelloMethod.setAccessible(true);
                    handleHelloMethod.invoke(handler, hello);
                }
            } catch (Exception ignored) {
            }
        };

        Connection connection = getConnection();
        if (connection != null) {
            Channel ch = connection.channel();
            if (ch != null && ch.eventLoop() != null) {
                ch.eventLoop().execute(task);
                return;
            }
        }
        task.run();
    }

    private byte[] getChallenge() {
        for (String name : new String[]{"f_10014_", "challenge", "nonce", "field_14168"}) {
            try {
                Field f = ServerLoginPacketListenerImpl.class.getDeclaredField(name);
                f.setAccessible(true);
                return (byte[]) f.get(this);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void setChallenge(byte[] challenge) {
        for (String name : new String[]{"f_10014_", "challenge", "nonce", "field_14168"}) {
            try {
                Field f = ServerLoginPacketListenerImpl.class.getDeclaredField(name);
                f.setAccessible(true);
                f.set(this, challenge);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private Connection getConnection() {
        for (String name : new String[]{"f_10017_", "connection", "field_14162"}) {
            try {
                Field f = ServerLoginPacketListenerImpl.class.getDeclaredField(name);
                f.setAccessible(true);
                return (Connection) f.get(this);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean setLoginState(String stateName) {
        for (String name : new String[]{"f_10019_", "state", "field_14163"}) {
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
