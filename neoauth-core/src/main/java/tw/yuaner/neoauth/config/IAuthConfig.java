package tw.yuaner.neoauth.config;

/**
 * 伺服器驗證設定抽象介面。
 * <p>
 * 定義了連線至 MariaDB 資料庫所需的設定項目以及驗證邏輯開關。
 * 各 Mod Loader (Forge / NeoForge) 將透過各自的 ConfigSpec 實作本介面。
 */
public interface IAuthConfig {

    /**
     * 取得 MariaDB 主機位址 (例如: 127.0.0.1)
     *
     * @return 資料庫主機 IP 或域名
     */
    String getDbHost();

    /**
     * 取得 MariaDB 埠號 (預設: 3306)
     *
     * @return 資料庫連接埠字串
     */
    String getDbPort();

    /**
     * 取得資料庫名稱 (例如: authme)
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
     * 取得存放使用者帳號資料的資料表名稱 (例如: authme)
     *
     * @return 資料表名稱
     */
    String getDbTable();

    /**
     * 是否允許離線（盜版/非官方）玩家在線上伺服器模式下連線進入
     *
     * @return true 若允許離線玩家進入
     */
    boolean isAllowOfflinePlayers();
}
