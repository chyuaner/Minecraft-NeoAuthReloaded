package tw.yuaner.neoauth.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.IAuthConfig;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Mojang 官方驗證伺服器熔斷保護器 (Circuit Breaker)。
 * <p>
 * 當偵測到 Mojang Session 伺服器掛掉、超時或客戶端連續在握手階段異常中斷時，
 * 本熔斷器將自動開啟保護（預設 180 秒）。在熔斷保護期間，伺服器將暫停向正版客戶端發送握手封包，
 * 直接將玩家以傳統離線密碼身分放行進服，徹底杜絕正版玩家被其客戶端彈出「無法連線到驗證伺服器」的窘境。
 */
public class MojangCircuitBreaker {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-CircuitBreaker");

    /**
     * 熔斷截止時間戳記 (毫秒)。0 代表未處於熔斷狀態。
     */
    private static volatile long trippedUntil = 0;

    /**
     * 是否曾經處於熔斷中（用於在熔斷結束時輸出恢復日誌）。
     */
    private static volatile boolean wasTripped = false;

    /**
     * 最近一次觸發熔斷的原因描述。
     */
    private static volatile String lastFailureReason = "無";

    /**
     * 記錄最近 60 秒內於握手階段（發送 ClientboundHelloPacket 後）中斷連線的時間戳記佇列。
     */
    private static final ConcurrentLinkedQueue<Long> RECENT_DISCONNECTIONS = new ConcurrentLinkedQueue<>();

    /**
     * 檢查當前是否處於熔斷保護狀態。
     *
     * @return true 若處於熔斷保護期間，否則為 false
     */
    public static boolean isTripped() {
        IAuthConfig cfg = ConfigManager.getInstance().getConfig();
        if (cfg != null && !cfg.isCircuitBreakerEnabled()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < trippedUntil) {
            return true;
        }

        if (wasTripped) {
            synchronized (MojangCircuitBreaker.class) {
                if (wasTripped && now >= trippedUntil) {
                    wasTripped = false;
                    trippedUntil = 0;
                    LOGGER.info("NeoAuth: Mojang 驗證伺服器熔斷保護時間已結束，恢復正版動態握手驗證。");
                }
            }
        }
        return false;
    }

    /**
     * 記錄一次 Mojang 伺服器查詢失敗或異常，並觸發/延長熔斷保護。
     *
     * @param reason 異常原因說明
     */
    public static void recordFailure(String reason) {
        IAuthConfig cfg = ConfigManager.getInstance().getConfig();
        if (cfg != null && !cfg.isCircuitBreakerEnabled()) {
            return;
        }

        int duration = (cfg != null && cfg.getCircuitBreakerDurationSeconds() > 0)
                ? cfg.getCircuitBreakerDurationSeconds()
                : 180;
        tripInternal(duration, reason, false);
    }

    /**
     * 管理員手動觸發熔斷狀態並指定持續時間。
     *
     * @param seconds 持續秒數 (<=0 則採用配置預設值)
     * @param reason  觸發理由
     */
    public static void tripManually(int seconds, String reason) {
        int duration = seconds > 0 ? seconds : 180;
        tripInternal(duration, reason, true);
    }

    private static synchronized void tripInternal(int durationSeconds, String reason, boolean manual) {
        long now = System.currentTimeMillis();
        trippedUntil = now + (durationSeconds * 1000L);
        lastFailureReason = reason != null ? reason : "未知原因";
        wasTripped = true;

        if (manual) {
            LOGGER.warn("NeoAuth: 管理員手動啟動 Mojang 驗證熔斷保護 (持續 {} 秒)。理由: {}", durationSeconds, lastFailureReason);
        } else {
            LOGGER.warn("NeoAuth: 偵測到 Mojang 驗證服務異常 [{}]，已啟動自動熔斷保護 (持續 {} 秒)。熔斷期間將自動跳過正版握手，改由傳統密碼方式放行進服。",
                    lastFailureReason, durationSeconds);
        }
    }

    /**
     * 記錄客戶端在發送 ClientboundHelloPacket 之後突發中斷連線。
     * 若短時間內達標 (60 秒內達 2 次)，則判定 Mojang 官方 joinServer 服務已掛掉並觸發熔斷。
     *
     * @param username 斷線玩家名稱
     */
    public static void recordClientDisconnection(String username) {
        IAuthConfig cfg = ConfigManager.getInstance().getConfig();
        if (cfg != null && !cfg.isCircuitBreakerEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        RECENT_DISCONNECTIONS.add(now);

        // 清理超過 60 秒的過期紀錄
        while (!RECENT_DISCONNECTIONS.isEmpty() && now - RECENT_DISCONNECTIONS.peek() > 60000L) {
            RECENT_DISCONNECTIONS.poll();
        }

        int threshold = (cfg != null && cfg.getRetry() > 0)
                ? cfg.getRetry()
                : 2;

        if (RECENT_DISCONNECTIONS.size() >= threshold) {
            RECENT_DISCONNECTIONS.clear();
            recordFailure("60 秒內連續 " + threshold + " 次客戶端於正版握手階段中斷連線 (" + username + " 等，疑似 Mojang Session 伺服器不可用)");
        }
    }

    /**
     * 成功完成一次 Mojang 官方驗證時呼叫。若處於熔斷狀態可提前復原。
     */
    public static void recordSuccess() {
        RECENT_DISCONNECTIONS.clear();
        if (isTripped()) {
            reset();
            LOGGER.info("NeoAuth: 成功完成 Mojang 官方 Session 驗證，提前重置熔斷狀態。");
        }
    }

    /**
     * 重置熔斷狀態。
     */
    public static synchronized void reset() {
        trippedUntil = 0;
        wasTripped = false;
        RECENT_DISCONNECTIONS.clear();
    }

    /**
     * 取得熔斷剩餘秒數。若未熔斷則回傳 0。
     */
    public static int getCooldownSecondsRemaining() {
        long now = System.currentTimeMillis();
        if (now < trippedUntil) {
            return (int) Math.max(0, (trippedUntil - now) / 1000L);
        }
        return 0;
    }

    /**
     * 取得最近一次觸發熔斷的原因。
     */
    public static String getLastFailureReason() {
        return lastFailureReason;
    }
}
