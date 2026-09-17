package tw.yuaner.neoauth.core;

import java.util.UUID;

/**
 * 線上玩家會話記憶體資料模型。
 * <p>
 * 在玩家進入伺服器與登入時暫存其連線與驗證資訊，供 TAB 模組及其他外部整合高頻輪詢使用，
 * 避免每次請求都進行資料庫磁碟 I/O 查詢。
 */
public class PlayerSessionData {

    private final UUID uuid;
    private final String username;
    private volatile String ip;
    private volatile boolean isIpv6;
    private volatile boolean isPremium;
    private volatile long loginTime;
    private volatile String email;

    public PlayerSessionData(UUID uuid, String username, String ip, boolean isPremium, long loginTime, String email) {
        this.uuid = uuid;
        this.username = username;
        this.ip = ip;
        this.isIpv6 = ip != null && ip.contains(":");
        this.isPremium = isPremium;
        this.loginTime = loginTime;
        this.email = email;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
        this.isIpv6 = ip != null && ip.contains(":");
    }

    public boolean isIpv6() {
        return isIpv6;
    }

    public boolean isPremium() {
        return isPremium;
    }

    public void setPremium(boolean premium) {
        this.isPremium = premium;
    }

    public long getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(long loginTime) {
        this.loginTime = loginTime;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
