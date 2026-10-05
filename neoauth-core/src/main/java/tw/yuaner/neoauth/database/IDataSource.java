package tw.yuaner.neoauth.database;

import java.util.List;

/**
 * 伺服器身分驗證資料庫來源介面。
 * <p>
 * 定義了帳號註冊、密碼校驗、登入記錄更新與帳號查詢等標準業務操作。
 */
public interface IDataSource {

    /**
     * 連線並初始化資料庫與資料表（使用全域平台設定）。
     *
     * @throws Exception 連線或初始化失敗時拋出例外
     */
    void connect() throws Exception;

    /**
     * 連線並初始化資料庫與資料表（使用指定設定）。
     *
     * @param config 資料庫設定
     * @throws Exception 連線或初始化失敗時拋出例外
     */
    void connect(tw.yuaner.neoauth.config.IAuthConfig config) throws Exception;

    /**
     * 關閉資料庫連線或連線池。
     */
    void close();

    /**
     * 檢查當前資料庫連線是否可用。
     *
     * @return true 若連線有效可用
     */
    boolean isConnected();

    /**
     * 檢查目前是否正處於備援資料庫 (Fallback SQLite) 運作狀態。
     *
     * @return true 若主資料庫異常且正使用備援資料庫，否則為 false
     */
    default boolean isFallbackActive() {
        return false;
    }

    /**
     * 檢查指定使用者名稱是否已註冊。
     *
     * @param username 玩家名稱
     * @return true 若已註冊，否則為 false
     */
    boolean isRegistered(String username);

    /**
     * 檢查指定使用者名稱在資料庫中是否已有設定密碼。
     *
     * @param username 玩家名稱
     * @return true 若已設定密碼，若為訪客（密碼為空）或帳號不存在則為 false
     */
    boolean hasPassword(String username);

    /**
     * 檢查玩家輸入的密碼是否正確。
     *
     * @param username 玩家名稱
     * @param password 輸入的明文密碼
     * @return true 若密碼正確，否則為 false
     */
    boolean checkPassword(String username, String password);

    /**
     * 註冊新玩家至資料庫中。
     *
     * @param username 玩家名稱
     * @param password 明文密碼
     * @param ip       註冊時 IP
     * @return true 若註冊成功，否則為 false
     */
    boolean registerPlayer(String username, String password, String ip);

    /**
     * 更新玩家最後登入時間與 IP。
     *
     * @param username 玩家名稱
     * @param ip       當前登入 IP
     */
    void updateLogin(String username, String ip);

    /**
     * 更新玩家最後登出狀態 (將 isLogged 設為 0)。
     *
     * @param username 玩家名稱
     */
    void updateQuit(String username);

    /**
     * 修改玩家密碼。
     *
     * @param username    玩家名稱
     * @param newPassword 新明文密碼
     * @return true 若更新成功，否則為 false
     */
    boolean changePassword(String username, String newPassword);

    /**
     * 取得指定玩家之詳細帳號驗證資料。
     *
     * @param username 玩家名稱
     * @return {@link PlayerAuthData}，若不存在則為 null
     */
    PlayerAuthData getPlayerData(String username);

    /**
     * 取得指定玩家之電子信箱。
     *
     * @param username 玩家名稱
     * @return 信箱字串，若無則為 null
     */
    String getEmail(String username);

    /**
     * 設定或更新指定玩家之電子信箱。
     *
     * @param username 玩家名稱
     * @param email    新電子信箱
     * @return true 若更新成功，否則為 false
     */
    boolean setEmail(String username, String email);

    /**
     * 取得指定玩家最後登入之 IP。
     *
     * @param username 玩家名稱
     * @return IP 字串，若無則為 null
     */
    String getIp(String username);

    /**
     * 依據玩家名稱或 IP 查詢關聯的所有玩家帳號名稱。
     *
     * @param usernameOrIp 玩家名稱或 IP
     * @return 帳號名稱清單
     */
    List<String> getAccounts(String usernameOrIp);

    /**
     * 取得最近登入伺服器的玩家帳號清單。
     *
     * @param limit 最大數量
     * @return 玩家驗證資料清單
     */
    List<PlayerAuthData> getRecentPlayers(int limit);

    /**
     * 新增一筆登入日誌。
     *
     * @param username          玩家名稱
     * @param uuid              玩家 UUID
     * @param ip                玩家客戶端 IP
     * @param serverIp          伺服器端連線 IP
     * @param serverPort        伺服器端連線 Port
     * @param loginMethod       登入驗證方式 (例如: Password, Premium)
     * @param connectionChannel 連線通道方式 (例如: TCP, WebSocket, TCP+zstd)
     * @param serverName        伺服器名稱
     * @param loginOpLevel      登入時之最高 OP 等級
     * @return 該筆日誌在資料庫的 id (流水號)，若失敗則傳回 -1
     */
    default long insertLoginLog(String username, String uuid, String ip, String cdnPop, String serverHost, String serverIp, int serverPort, String loginMethod, String connectionChannel, String serverName, int loginOpLevel) {
        return insertLoginLog(username, uuid, ip, serverHost, serverIp, serverPort, loginMethod, connectionChannel, serverName, loginOpLevel);
    }

    /**
     * 新增一筆登入日誌。
     */
    default long insertLoginLog(String username, String uuid, String ip, String serverHost, String serverIp, int serverPort, String loginMethod, String connectionChannel, String serverName, int loginOpLevel) {
        return -1;
    }

    /**
     * 新增一筆登入日誌 (舊版相容方法)。
     */
    default long insertLoginLog(String username, String uuid, String ip, String serverIp, int serverPort, String loginMethod, String connectionChannel, String serverName, int loginOpLevel) {
        return insertLoginLog(username, uuid, ip, serverIp, serverIp, serverPort, loginMethod, connectionChannel, serverName, loginOpLevel);
    }

    /**
     * 更新登入日誌的登出資訊 (登出時間與登出時最高 OP 等級)。
     *
     * @param logId         日誌的流水號 (id)
     * @param logoutOpLevel 登出時之最高 OP 等級
     */
    default void updateLoginLogLogout(long logId, int logoutOpLevel) {
    }

    /**
     * 更新登入日誌的登出時間 (相容方法)。
     *
     * @param logId 日誌的流水號 (id)
     */
    default void updateLoginLogLogoutTime(long logId) {
        updateLoginLogLogout(logId, 0);
    }

    /**
     * 將離線/備援期間產生的登入日誌回補同步至主資料庫。
     */
    default void syncLoginLogs() {
    }

    /**
     * 依伺服器名稱、玩家名稱與時間範圍或筆數查詢登入日誌紀錄。
     *
     * @param serverName     伺服器識別名稱 (若為 null 則不限制伺服器)
     * @param username       玩家名稱 (若為 null 則查詢全部玩家)
     * @param sinceTimestamp 起始時間戳記 (毫秒，0 表示不限時間)
     * @param limit          最大查詢筆數
     * @return 登入日誌紀錄清單 (依登入時間降冪排序)
     */
    default List<LoginLogRecord> getLoginLogs(String serverName, String username, long sinceTimestamp, int limit) {
        return java.util.Collections.emptyList();
    }

    /**
     * 依玩家名稱與時間範圍或筆數查詢登入日誌紀錄 (不限伺服器相容方法)。
     *
     * @param username       玩家名稱 (若為 null 則查詢全部玩家)
     * @param sinceTimestamp 起始時間戳記 (毫秒，0 表示不限時間)
     * @param limit          最大查詢筆數
     * @return 登入日誌紀錄清單 (依登入時間降冪排序)
     */
    default List<LoginLogRecord> getLoginLogs(String username, long sinceTimestamp, int limit) {
        return getLoginLogs(null, username, sinceTimestamp, limit);
    }
}
