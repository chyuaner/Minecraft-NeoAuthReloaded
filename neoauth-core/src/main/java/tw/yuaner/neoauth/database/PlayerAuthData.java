package tw.yuaner.neoauth.database;

/**
 * 玩家驗證與帳號資料模型。
 */
public class PlayerAuthData {

    private final String username;
    private final String realName;
    private final String ip;
    private final String regIp;
    private final long lastLogin;
    private final long regDate;
    private final String email;

    public PlayerAuthData(String username, String realName, String ip, String regIp, long lastLogin, long regDate, String email) {
        this.username = username;
        this.realName = realName;
        this.ip = ip;
        this.regIp = regIp;
        this.lastLogin = lastLogin;
        this.regDate = regDate;
        this.email = email;
    }

    public String getUsername() { return username; }
    public String getRealName() { return realName; }
    public String getIp() { return ip; }
    public String getRegIp() { return regIp; }
    public long getLastLogin() { return lastLogin; }
    public long getRegDate() { return regDate; }
    public String getEmail() { return email; }
}
