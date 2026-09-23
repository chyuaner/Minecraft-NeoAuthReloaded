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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.Crypt;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.DatabaseManager;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.core.AuthLogic;
import tw.yuaner.neoauth.platform.Services;

import tw.yuaner.neoauth.neoforge.login.NeoForgeLoginStateHolder;

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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 伺服器登入封包監聽器 Mixin (NeoForge 1.21.1)。
 * <p>
 * 支援在線上伺服器模式 (online-mode=true) 與離線伺服器模式 (online-mode=false) 下的動態正版驗證。
 * 在離線模式下發起加密握手以自動識別正版玩家並免密登入，皮膚由 SkinRestorer 等皮膚模組接管。
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class NeoForgeServerLoginMixin {

    @Unique
    private static final Logger neoauth$LOGGER = LoggerFactory.getLogger("NeoAuth-Login");

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
    private static final Map<Object, NeoForgeLoginStateHolder> neoauth$STATES = new ConcurrentHashMap<>();

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
            String customUrl = Services.PLATFORM.getConfig().getCustomYggdrasilUrl();
            // 若未設定自訂 Yggdrasil 且客戶端明確是離線 UUID，直接跳過發送握手避免純離線客戶端因缺乏 Token 觸發「無效的 session」
            if ((customUrl == null || customUrl.isBlank()) && (offlineUuid.equals(uuid) || uuid == null)) {
                return;
            }

            // 【熔斷保護檢查】
            // 若未設定自訂 Yggdrasil (即走 Mojang 官方驗證)，且 Mojang 熔斷保護啟動中：
            if ((customUrl == null || customUrl.isBlank()) && tw.yuaner.neoauth.util.MojangCircuitBreaker.isTripped()) {
                AuthManager.markMojangFallback(offlineUuid);
                neoauth$LOGGER.warn("NeoAuth: Mojang 驗證伺服器處於熔斷保護中，直接放行玩家 {} 進入傳統密碼登入 (避免客戶端拋錯斷線)。", username);
                return; // 直接放行原版離線處理，不發送 ClientboundHelloPacket！
            }

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

                NeoForgeLoginStateHolder state = new NeoForgeLoginStateHolder(packet);
                neoauth$STATES.put(this, state);
                neoauth$setLoginState("KEY");
                ci.cancel();

                // 看門狗定時器：若客戶端為離線版或網路延遲超時，自動先回退放行進入遊戲要求密碼，避免玩家卡死
                int rawTimeout = Services.PLATFORM.getConfig().getDynamicVerificationTimeout();
                final int timeout = rawTimeout <= 0 ? 7 : rawTimeout;

                state.setWatchdogTask(neoauth$WATCHDOG.schedule(() -> neoauth$fallbackToOffline(this, state, "握手階段客戶端未回應"), timeout, TimeUnit.SECONDS));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 攔截 Key 密鑰回應封包（動態正版握手階段）。
     */
    @Inject(method = "handleKey", at = @At("HEAD"), cancellable = true)
    private void neoauth$handleKey(ServerboundKeyPacket packet, CallbackInfo ci) {
        NeoForgeLoginStateHolder state = neoauth$STATES.get(this);
        if (state == null) {
            return; // 非動態握手，走原版流程
        }

        ci.cancel();

        // 收到 Key 封包，先取消 Stage 1 Watchdog
        state.cancelWatchdog();

        try {
            KeyPair keyPair = neoauth$getKeyPair(this.server);
            PrivateKey privateKey = keyPair.getPrivate();
            PublicKey publicKey = keyPair.getPublic();

            if (!packet.isChallengeValid(this.challenge, privateKey)) {
                neoauth$LOGGER.warn("NeoAuth: 連線 challenge 驗證不匹配，降級為離線玩家: {}", state.getHelloPacket().name());
                neoauth$fallbackToOffline(this, state, "Challenge 不匹配");
                return;
            }

            SecretKey secretKey = packet.getSecretKey(privateKey);
            Cipher decrypt = Crypt.getCipher(2, secretKey);
            Cipher encrypt = Crypt.getCipher(1, secretKey);
            this.connection.setEncryptionKey(decrypt, encrypt);

            byte[] digestBytes = Crypt.digestData("", publicKey, secretKey);
            String digest = new BigInteger(digestBytes).toString(16);

            String username = state.getHelloPacket().name();
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));

            int rawTimeout = Services.PLATFORM.getConfig().getDynamicVerificationTimeout();
            final int timeout = rawTimeout <= 0 ? 7 : rawTimeout;

            // 啟動 Stage 2 (Session 驗證查詢) 專屬超時 Watchdog
            state.setWatchdogTask(neoauth$WATCHDOG.schedule(() -> neoauth$fallbackToOffline(this, state, "Session 驗證回應逾時 (" + timeout + " 秒)"), timeout, TimeUnit.SECONDS));

            CompletableFuture.runAsync(() -> {
                try {
                    boolean verified = false;
                    String authSource = "Mojang 官方";
                    String texturesValue = null;
                    String texturesSignature = null;

                    boolean isCustomAuth = false;

                    // 1. 優先向 Mojang 官方 Session 伺服器查詢 (傳入 null 避免 IPv4/IPv6 雙棧網路不匹配問題)
                    int maxRetries = Services.PLATFORM.getConfig().getRetry();
                    if (maxRetries < 1) maxRetries = 1;
                    int attempts = 0;

                    while (attempts < maxRetries && !verified) {
                        attempts++;
                        try {
                            ProfileResult profileResult = this.server.getSessionService().hasJoinedServer(username, digest, null);
                            if (profileResult != null && profileResult.profile() != null) {
                                verified = true;
                                GameProfile mojangProfile = profileResult.profile();
                                if (mojangProfile.getProperties() != null && mojangProfile.getProperties().containsKey("textures")) {
                                    for (com.mojang.authlib.properties.Property prop : mojangProfile.getProperties().get("textures")) {
                                        texturesValue = prop.value();
                                        texturesSignature = prop.signature();
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            if (attempts >= maxRetries) {
                                throw e;
                            }
                            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        }
                    }

                    // 2. 若 Mojang 未確認，且有設定自訂 Yggdrasil 外置驗證伺服器，向自訂伺服器查詢
                    String customYggdrasilUrl = Services.PLATFORM.getConfig().getCustomYggdrasilUrl();
                    if (!verified && customYggdrasilUrl != null && !customYggdrasilUrl.isBlank()) {
                        tw.yuaner.neoauth.util.YggdrasilService.YggdrasilAuthResult yggResult =
                                tw.yuaner.neoauth.util.YggdrasilService.verifyJoined(customYggdrasilUrl, username, digest, null);
                        if (yggResult.success()) {
                            verified = true;
                            isCustomAuth = true;
                            authSource = "自訂 Yggdrasil (" + customYggdrasilUrl + ")";
                            texturesValue = yggResult.texturesValue();
                            texturesSignature = yggResult.texturesSignature();
                        }
                    }

                    if (verified) {
                        tw.yuaner.neoauth.util.MojangCircuitBreaker.recordSuccess();
                        AuthManager.markPremiumVerifiedWithTextures(offlineUuid, username, texturesValue, texturesSignature);
                        neoauth$LOGGER.info("NeoAuth: {} 驗證成功 ({})，已標記免密自動登入與皮膚保留。", authSource, username);

                        // 延遲晉升機制：若玩家在握手超時放行後已進入遊戲且尚未手動登入，即時升級為已登入狀態並解除限制
                        this.server.execute(() -> {
                            ServerPlayer player = this.server.getPlayerList().getPlayer(offlineUuid);
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
                        neoauth$LOGGER.info("NeoAuth: Mojang / 自訂 Yggdrasil 未確認玩家 ({}) 的正版 Session，以離線模式登入。", username);
                    }
                } catch (Exception e) {
                    neoauth$LOGGER.error("NeoAuth: 查詢 Session 驗證伺服器時發生異常 (" + username + "): " + e.getMessage(), e);
                    String customUrl = Services.PLATFORM.getConfig().getCustomYggdrasilUrl();
                    if (customUrl == null || customUrl.isBlank()) {
                        tw.yuaner.neoauth.util.MojangCircuitBreaker.recordFailure(
                                e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "null"));
                    }
                    AuthManager.markMojangFallback(offlineUuid);
                } finally {
                    // 原子放行判定：若尚未被 Watchdog 放行過，在此處放行進入 HELLO 重放
                    if (state.getReleased().compareAndSet(false, true)) {
                        state.cancelWatchdog();
                        neoauth$STATES.remove(this);
                        neoauth$HANDLED.add(this);
                        neoauth$replayHello(this, state.getHelloPacket());
                    }
                }
            });
        } catch (Exception e) {
            neoauth$LOGGER.error("NeoAuth: 密鑰握手解密發生錯誤: " + e.getMessage(), e);
            neoauth$fallbackToOffline(this, state, "密鑰握手解密異常");
        }
    }

    /**
     * 攔截斷線清理。
     */
    @Inject(method = "onDisconnect", at = @At("HEAD"), require = 0)
    private void neoauth$onDisconnect(DisconnectionDetails details, CallbackInfo ci) {
        NeoForgeLoginStateHolder state = neoauth$STATES.remove(this);
        if (state != null) {
            state.getReleased().set(true);
            state.cancelWatchdog();
            String customUrl = Services.PLATFORM.getConfig().getCustomYggdrasilUrl();
            if (customUrl == null || customUrl.isBlank()) {
                tw.yuaner.neoauth.util.MojangCircuitBreaker.recordClientDisconnection(state.getHelloPacket().name());
            }
        }
        neoauth$HANDLED.remove(this);
    }

    /**
     * 線上模式 (online-mode=true) 或離線模式下注入皮膚並標記通過驗證之正版玩家。
     */
    @Inject(method = "startClientVerification", at = @At("HEAD"))
    private void neoauth$onStartClientVerification(GameProfile profile, CallbackInfo ci) {
        if (profile != null) {
            UUID id = profile.getId();
            String name = profile.getName();
            if (profile.getProperties() != null && profile.getProperties().containsKey("textures")) {
                AuthManager.markPremiumVerified(id);
            } else {
                tw.yuaner.neoauth.util.TextureProperty tex = AuthManager.getVerifiedTextures(id);
                if (tex == null && name != null) {
                    tex = AuthManager.getVerifiedTextures(name);
                }
                if (tex != null && tex.value() != null && profile.getProperties() != null) {
                    com.mojang.authlib.properties.Property prop = tex.hasSignature()
                            ? new com.mojang.authlib.properties.Property("textures", tex.value(), tex.signature())
                            : new com.mojang.authlib.properties.Property("textures", tex.value());
                    profile.getProperties().put("textures", prop);
                }
            }
        }
    }

    @Unique
    private void neoauth$fallbackToOffline(Object handler, NeoForgeLoginStateHolder state, String reason) {
        if (state == null) return;
        state.cancelWatchdog();
        if (state.getReleased().compareAndSet(false, true)) {
            neoauth$STATES.remove(handler);
            neoauth$HANDLED.add(handler);
            String username = state.getHelloPacket().name();
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            AuthManager.markMojangFallback(offlineUuid);
            neoauth$LOGGER.warn("NeoAuth: 動態正版驗證已先降級為離線模式放行 [{}] (玩家: {})", reason, username);
            neoauth$replayHello(handler, state.getHelloPacket());
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
