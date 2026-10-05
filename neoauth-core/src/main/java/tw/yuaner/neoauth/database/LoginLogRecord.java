package tw.yuaner.neoauth.database;

/**
 * 玩家登入與登出審計日誌資料模型。
 */
public class LoginLogRecord {

    private final long id;
    private final String serverName;
    private final String username;
    private final String uuid;
    private final long loginTime;
    private final Long logoutTime;
    private final String ip;
    private final String loginMethod;
    private final String connectionChannel;
    private final String cdnPop;
    private final String serverHost;
    private final String serverIp;
    private final int serverPort;
    private final int loginOpLevel;
    private final int logoutOpLevel;

    public LoginLogRecord(long id, String serverName, String username, String uuid,
                          long loginTime, Long logoutTime, String ip,
                          String loginMethod, String connectionChannel, String cdnPop,
                          String serverHost, String serverIp, int serverPort,
                          int loginOpLevel, int logoutOpLevel) {
        this.id = id;
        this.serverName = serverName;
        this.username = username;
        this.uuid = uuid;
        this.loginTime = loginTime;
        this.logoutTime = logoutTime;
        this.ip = ip;
        this.loginMethod = loginMethod;
        this.connectionChannel = connectionChannel;
        this.cdnPop = cdnPop;
        this.serverHost = serverHost;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.loginOpLevel = loginOpLevel;
        this.logoutOpLevel = logoutOpLevel;
    }

    public long getId() {
        return id;
    }

    public String getServerName() {
        return serverName;
    }

    public String getUsername() {
        return username;
    }

    public String getUuid() {
        return uuid;
    }

    public long getLoginTime() {
        return loginTime;
    }

    public Long getLogoutTime() {
        return logoutTime;
    }

    public String getIp() {
        return ip;
    }

    public String getLoginMethod() {
        return loginMethod;
    }

    public String getConnectionChannel() {
        return connectionChannel;
    }

    public String getCdnPop() {
        return cdnPop;
    }

    public String getServerHost() {
        return serverHost;
    }

    public String getServerIp() {
        return serverIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getLoginOpLevel() {
        return loginOpLevel;
    }

    public int getLogoutOpLevel() {
        return logoutOpLevel;
    }

    /**
     * 檢查此筆日誌是否有正常的登出時間紀錄。
     */
    public boolean hasValidLogout() {
        return logoutTime != null && logoutTime >= loginTime && logoutTime > 0;
    }

    /**
     * 取得遊玩時間長度（毫秒）。
     *
     * @param currentSessionFallback 若尚未登出（如在線中），提供的當前時間戳記作為退守值
     * @return 遊玩時長毫秒數
     */
    public long getDurationMillis(long currentSessionFallback) {
        if (hasValidLogout()) {
            return Math.max(0, logoutTime - loginTime);
        }
        if (currentSessionFallback > loginTime) {
            return currentSessionFallback - loginTime;
        }
        return 0L;
    }
}
