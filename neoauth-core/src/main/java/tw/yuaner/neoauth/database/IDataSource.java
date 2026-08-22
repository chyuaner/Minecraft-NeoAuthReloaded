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
     * 檢查指定使用者名稱是否已註冊。
     *
     * @param username 玩家名稱
     * @return true 若已註冊，否則為 false
     */
    boolean isRegistered(String username);

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
}
