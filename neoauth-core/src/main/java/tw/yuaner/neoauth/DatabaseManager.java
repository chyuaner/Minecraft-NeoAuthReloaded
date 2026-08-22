package tw.yuaner.neoauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.database.IDataSource;
import tw.yuaner.neoauth.database.MySqlDataSource;
import tw.yuaner.neoauth.database.PlayerAuthData;
import tw.yuaner.neoauth.database.SqliteDataSource;
import tw.yuaner.neoauth.platform.Services;

import java.util.Collections;
import java.util.List;

/**
 * NeoAuth 全域資料庫操作靜態門面 (Facade) 與工廠管理器。
 * <p>
 * 根據設定檔 {@code DataSource.backend} 動態實例化並管理對應的 {@link IDataSource} 實作
 * (SQLite, MariaDB, MySQL)，對外部業務層提供一致且執行緒安全的靜態存取介面。
 */
public class DatabaseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-Database");
    private static IDataSource activeDataSource;

    /**
     * 初始化資料庫連線並建立必要資料表。
     */
    public static synchronized void init() {
        close();

        IAuthConfig config = Services.PLATFORM.getConfig();
        String backend = config.getDbBackend() != null ? config.getDbBackend().trim().toUpperCase() : "SQLITE";

        switch (backend) {
            case "MARIADB", "MYSQL" -> activeDataSource = new MySqlDataSource();
            case "SQLITE" -> activeDataSource = new SqliteDataSource();
            default -> {
                LOGGER.warn("NeoAuth: 未知的資料庫類型 '{}'，將使用預設的 SQLITE！", backend);
                activeDataSource = new SqliteDataSource();
            }
        }

        try {
            activeDataSource.connect();
        } catch (Exception e) {
            LOGGER.error("NeoAuth: 無法連線至資料庫！請檢查 config/neoauth/config.yml 之資料庫設定。錯誤原因: {}", e.getMessage(), e);
            activeDataSource = null;
        }
    }

    /**
     * 關閉目前作用中的資料庫連線池。
     */
    public static synchronized void close() {
        if (activeDataSource != null) {
            activeDataSource.close();
            activeDataSource = null;
        }
    }

    /**
     * 取得目前作用中的資料庫來源實例。
     *
     * @return {@link IDataSource} 實例，若未連線則為 null
     */
    public static IDataSource getActiveDataSource() {
        return activeDataSource;
    }

    /**
     * 設定目前作用中的資料庫來源實例（供測試或外部自訂資料庫注入使用）。
     *
     * @param dataSource 資料庫來源實例
     */
    public static synchronized void setActiveDataSource(IDataSource dataSource) {
        activeDataSource = dataSource;
    }

    /**
     * 檢查資料庫連線是否正常可用。
     *
     * @return true 若連線有效可用
     */
    public static boolean isConnected() {
        return activeDataSource != null && activeDataSource.isConnected();
    }

    /**
     * 檢查指定的使用者名稱是否已經註冊。
     *
     * @param username 玩家名稱
     * @return true 若已註冊，否則為 false
     */
    public static boolean isRegistered(String username) {
        return activeDataSource != null && activeDataSource.isRegistered(username);
    }

    /**
     * 檢查指定使用者在資料庫中是否已設定密碼。
     *
     * @param username 玩家名稱
     * @return true 若已設定密碼，若為訪客（密碼為空）或帳號不存在則為 false
     */
    public static boolean hasPassword(String username) {
        return activeDataSource != null && activeDataSource.hasPassword(username);
    }

    /**
     * 檢查玩家輸入的密碼是否正確。
     *
     * @param username 玩家名稱
     * @param password 輸入的密碼
     * @return true 若密碼正確，否則為 false
     */
    public static boolean checkPassword(String username, String password) {
        return activeDataSource != null && activeDataSource.checkPassword(username, password);
    }

    /**
     * 註冊新玩家資料至資料庫中。
     *
     * @param username 玩家名稱
     * @param password 純文字密碼
     * @param ip       玩家註冊時的 IP 位址
     * @return true 若註冊成功，否則為 false
     */
    public static boolean registerPlayer(String username, String password, String ip) {
        return activeDataSource != null && activeDataSource.registerPlayer(username, password, ip);
    }

    /**
     * 更新玩家登入時間與登入 IP 位址。
     *
     * @param username 玩家名稱
     * @param ip       玩家當前 IP 位址
     */
    public static void updateLogin(String username, String ip) {
        if (activeDataSource != null) {
            activeDataSource.updateLogin(username, ip);
        }
    }

    /**
     * 更新玩家登出狀態。
     *
     * @param username 玩家名稱
     */
    public static void updateQuit(String username) {
        if (activeDataSource != null) {
            activeDataSource.updateQuit(username);
        }
    }

    /**
     * 修改玩家密碼（管理員或玩家本人修改）。
     *
     * @param username    玩家名稱
     * @param newPassword 新密碼
     * @return true 若更新成功，否則為 false
     */
    public static boolean changePassword(String username, String newPassword) {
        return activeDataSource != null && activeDataSource.changePassword(username, newPassword);
    }

    /**
     * 查詢指定玩家的詳細帳號資料。
     *
     * @param username 玩家名稱
     * @return {@link PlayerAuthData} 物件，若不存在則回傳 null
     */
    public static PlayerAuthData getPlayerData(String username) {
        return activeDataSource != null ? activeDataSource.getPlayerData(username) : null;
    }

    /**
     * 取得指定玩家的 Email 資訊。
     */
    public static String getEmail(String username) {
        return activeDataSource != null ? activeDataSource.getEmail(username) : null;
    }

    /**
     * 更新指定玩家的 Email 資訊。
     */
    public static boolean setEmail(String username, String email) {
        return activeDataSource != null && activeDataSource.setEmail(username, email);
    }

    /**
     * 取得指定玩家最後登入的 IP 位址。
     */
    public static String getIp(String username) {
        return activeDataSource != null ? activeDataSource.getIp(username) : null;
    }

    /**
     * 依據玩家名稱或 IP 位址，查詢所有關聯註冊/登入的帳號名稱。
     *
     * @param usernameOrIp 玩家名稱或 IP 字串
     * @return 關聯帳號名稱清單
     */
    public static List<String> getAccounts(String usernameOrIp) {
        return activeDataSource != null ? activeDataSource.getAccounts(usernameOrIp) : Collections.emptyList();
    }

    /**
     * 取得最近登入伺服器的玩家清單。
     *
     * @param limit 最大回傳數量
     * @return 玩家帳號資料清單
     */
    public static List<PlayerAuthData> getRecentPlayers(int limit) {
        return activeDataSource != null ? activeDataSource.getRecentPlayers(limit) : Collections.emptyList();
    }
}
