package tw.yuaner.neoauth.config;

/**
 * 伺服器驗證設定抽象介面。
 * <p>
 * 定義了連線至 MariaDB / MySQL 資料庫所需的設定項目、防護開關、密碼長度與訊息語言設定。
 */
public interface IAuthConfig {

    /**
     * 取得資料庫後端類型 (例如: MARIADB, MYSQL)
     *
     * @return 後端類型字串
     */
    String getDbBackend();

    /**
     * 取得資料庫主機位址 (例如: 127.0.0.1)
     *
     * @return 資料庫主機 IP 或域名
     */
    String getDbHost();

    /**
     * 取得資料庫埠號 (預設: 3306)
     *
     * @return 資料庫連接埠字串
     */
    String getDbPort();

    /**
     * 取得資料庫名稱 (例如: neoauth)
     *
     * @return 資料庫名稱
     */
    String getDbName();

    /**
     * 取得資料庫連線使用者名稱
     *
     * @return 使用者名稱
     */
    String getDbUsername();

    /**
     * 取得資料庫連線密碼
     *
     * @return 密碼字串
     */
    String getDbPassword();

    /**
     * 取得存放使用者帳號資料的資料表名稱 (例如: neoauth)
     *
     * @return 資料表名稱
     */
    String getDbTable();

    /**
     * 取得連線池最大連線數
     *
     * @return 最大連線數
     */
    int getDbPoolSize();

    /**
     * 取得連線最大存活時間 (秒)
     *
     * @return 存活秒數
     */
    int getDbMaxLifetime();

    /**
     * 是否允許離線（盜版/非官方）玩家在線上伺服器模式下連線進入
     *
     * @return true 若允許離線玩家進入
     */
    boolean isAllowOfflinePlayers();

    /**
     * 取得訊息語言代碼 (例如: "zhtw", "en")
     *
     * @return 語言代碼
     */
    String getMessagesLanguage();

    /**
     * 取得密碼雜湊演算法 (例如: "BCRYPT")
     *
     * @return 雜湊演算法
     */
    String getPasswordHash();

    /**
     * 取得允許的密碼最短長度
     *
     * @return 密碼最短字元數
     */
    int getMinPasswordLength();

    /**
     * 取得允許的密碼最長長度
     *
     * @return 密碼最長字元數
     */
    int getMaxPasswordLength();

    /**
     * 取得登入超時時間 (秒，0 為不限制)
     *
     * @return 超時秒數
     */
    int getTimeout();

    /**
     * 是否在輸入錯誤密碼時直接踢出玩家
     *
     * @return true 若踢出
     */
    boolean isKickOnWrongPassword();

    /**
     * 取得最大允許密碼錯誤次數
     *
     * @return 最大錯誤嘗試次數
     */
    int getMaxLoginTries();

    /**
     * 是否對未登入玩家施加失明效果
     *
     * @return true 若啟用
     */
    boolean isBlindnessEnabled();

    /**
     * 是否對未登入玩家施加緩速與跳躍抑制
     *
     * @return true 若啟用
     */
    boolean isSlownessEnabled();

    /**
     * 是否在玩家進服時顯示 welcome.txt 歡迎文字公告
     *
     * @return true 若啟用
     */
    boolean isDisplayWelcomeMessage();

    /**
     * 是否在未登入時傳送玩家至 spawn.yml 指定之登入點
     *
     * @return true 若啟用
     */
    boolean isTeleportUnAuthedToSpawn();

    /**
     * 登入成功後是否傳送回玩家登出時的位置
     *
     * @return true 若啟用
     */
    boolean isSaveQuitLocation();
}

