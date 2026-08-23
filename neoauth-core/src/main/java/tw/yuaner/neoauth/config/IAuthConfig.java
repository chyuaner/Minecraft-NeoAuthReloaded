package tw.yuaner.neoauth.config;

/**
 * 伺服器驗證設定抽象介面。
 * <p>
 * 定義了連線至 MariaDB / MySQL 資料庫所需的設定項目、防護開關、密碼長度與訊息語言設定。
 */
public interface IAuthConfig {

    /**
     * 取得資料庫後端類型 (例如: SQLITE, MARIADB, MYSQL)
     *
     * @return 後端類型字串
     */
    String getDbBackend();

    /**
     * 取得 SQLite 資料庫檔案路徑 (預設: config/neoauth/neoauth.db)
     *
     * @return SQLite 檔案路徑
     */
    String getSqLiteFile();

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
     * 是否啟用 SSL 連線至 MySQL/MariaDB 資料庫
     */
    boolean isMySqlUseSSL();

    /**
     * 是否驗證資料庫伺服器 SSL 憑證
     */
    boolean isMySqlCheckServerCertificate();

    /**
     * 是否允許客戶端取得 RSA 伺服器公鑰
     */
    boolean isMySqlAllowPublicKeyRetrieval();

    /**
     * 取得主鍵 ID 欄位名稱
     */
    String getMySqlColumnId();

    /**
     * 取得玩家帳號使用者名稱欄位名稱
     */
    String getMySqlColumnName();

    /**
     * 取得玩家 RealName 欄位名稱
     */
    String getMySqlRealName();

    /**
     * 取得密碼欄位名稱
     */
    String getMySqlColumnPassword();

    /**
     * 取得密碼鹽值欄位名稱
     */
    String getMySqlColumnSalt();

    /**
     * 取得電子郵件欄位名稱
     */
    String getMySqlColumnEmail();

    /**
     * 取得是否登入狀態欄位名稱
     */
    String getMySqlColumnLogged();

    /**
     * 取得 Session 狀態欄位名稱
     */
    String getMySqlColumnHasSession();

    /**
     * 取得 TOTP 雙層驗證密鑰欄位名稱
     */
    String getMySqlTotpKey();

    /**
     * 取得最後登入 IP 欄位名稱
     */
    String getMySqlColumnIp();

    /**
     * 取得最後登入時間戳記欄位名稱
     */
    String getMySqlColumnLastLogin();

    /**
     * 取得註冊時間戳記欄位名稱
     */
    String getMySqlColumnRegisterDate();

    /**
     * 取得註冊時 IP 欄位名稱
     */
    String getMySqlColumnRegisterIp();

    /**
     * 取得最後位置 X 座標欄位名稱
     */
    String getMySqlLastLocX();

    /**
     * 取得最後位置 Y 座標欄位名稱
     */
    String getMySqlLastLocY();

    /**
     * 取得最後位置 Z 座標欄位名稱
     */
    String getMySqlLastLocZ();

    /**
     * 取得最後位置世界名稱欄位名稱
     */
    String getMySqlLastLocWorld();

    /**
     * 取得最後位置 Yaw 偏航角欄位名稱
     */
    String getMySqlLastLocYaw();

    /**
     * 取得最後位置 Pitch 俯仰角欄位名稱
     */
    String getMySqlLastLocPitch();

    /**
     * 取得玩家 UUID 欄位名稱
     */
    String getMySqlPlayerUUID();

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
     * 是否強制將所有通過 Mojang 驗證的正版玩家之 UUID 保持/改寫為離線版 UUID (v3)。
     * 啟用後，可確保玩家無論使用離線版或正版登入，存檔與插件資料皆使用同一組離線 UUID，
     * 正版驗證僅作為免密碼自動登入之憑證（且官方皮膚仍會正常保留）。
     *
     * @return true 若啟用離線 UUID 相容模式 (預設為 false)
     */
    boolean isDynamicPremiumVerification();

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
     * 取得 SALTED2MD5 加鹽雙重 MD5 的鹽值長度 (預設: 6)
     *
     * @return 鹽值長度
     */
    int getDoubleMD5SaltLength();

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
     * 是否開放伺服器遊戲內註冊 (對應 settings.registration.enabled，預設: true)
     */
    boolean isRegistrationEnabled();

    /**
     * 提醒玩家登入/註冊的時間間隔秒數 (對應 settings.registration.messageInterval，預設: 5，0 為不定期提醒)
     */
    int getRegistrationMessageInterval();

    /**
     * 是否強制所有玩家必須註冊與登入才能遊玩 (對應 settings.registration.force，預設: true)
     */
    boolean isRegistrationForced();

    /**
     * 玩家成功註冊後是否直接踢出伺服器 (對應 settings.registration.forceKickAfterRegister，預設: false)
     */
    boolean isForceKickAfterRegister();

    /**
     * 玩家成功註冊後是否強制要求重新執行 /login 進行登入 (對應 settings.registration.forceLoginAfterRegister，預設: false)
     */
    boolean isForceLoginAfterRegister();
}

