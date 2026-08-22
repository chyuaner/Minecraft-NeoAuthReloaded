package tw.yuaner.neoauth.core;

import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.DatabaseManager;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.config.MessagesManager;
import tw.yuaner.neoauth.platform.Services;

import java.util.UUID;

/**
 * 伺服器驗證核心業務邏輯。
 * <p>
 * 提供跨版本共用的身分驗證邏輯，包含登入驗證、註冊驗證、指令白名單過濾與提示訊息生成。
 */
public class AuthLogic {

    /**
     * 登入結果列舉。
     */
    public enum LoginResult {
        SUCCESS("login.success"),
        ALREADY_LOGGED_IN("login.already_logged_in"),
        NOT_REGISTERED("register.register_prompt"),
        WRONG_PASSWORD("login.wrong_password"),
        DATABASE_ERROR("general.database_error");

        private final String messageKey;

        LoginResult(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public String getMessage() {
            return ConfigManager.getInstance().getMessagesManager().get(messageKey);
        }
    }

    /**
     * 註冊結果列舉。
     */
    public enum RegisterResult {
        SUCCESS("register.success"),
        ALREADY_LOGGED_IN("login.already_logged_in"),
        ALREADY_REGISTERED("register.already_registered"),
        PASSWORD_MISMATCH("register.password_mismatch"),
        PASSWORD_TOO_SHORT("register.password_too_short"),
        PASSWORD_TOO_LONG("register.password_too_long"),
        DATABASE_ERROR("general.database_error");

        private final String messageKey;

        RegisterResult(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public String getMessage() {
            IAuthConfig config = ConfigManager.getInstance().getConfig();
            MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
            if (this == PASSWORD_TOO_SHORT) {
                return msgMgr.get(messageKey, config.getMinPasswordLength());
            } else if (this == PASSWORD_TOO_LONG) {
                return msgMgr.get(messageKey, config.getMaxPasswordLength());
            }
            return msgMgr.get(messageKey);
        }
    }

    /**
     * 嘗試執行玩家登入流程。
     *
     * @param uuid     玩家 UUID
     * @param username 玩家名稱
     * @param ip       玩家 IP 位址
     * @param password 輸入的密碼
     * @return 登入結果 {@link LoginResult}
     */
    public static LoginResult attemptLogin(UUID uuid, String username, String ip, String password) {
        if (AuthManager.isLoggedIn(uuid)) {
            return LoginResult.ALREADY_LOGGED_IN;
        }

        if (!DatabaseManager.isRegistered(username)) {
            return LoginResult.NOT_REGISTERED;
        }

        boolean match = DatabaseManager.checkPassword(username, password);
        if (match) {
            AuthManager.setLoggedIn(uuid);
            DatabaseManager.updateLogin(username, ip);
            return LoginResult.SUCCESS;
        } else {
            return LoginResult.WRONG_PASSWORD;
        }
    }

    /**
     * 嘗試執行玩家註冊流程。
     *
     * @param uuid            玩家 UUID
     * @param username        玩家名稱
     * @param ip              玩家 IP 位址
     * @param password        輸入的密碼
     * @param confirmPassword 確認密碼
     * @return 註冊結果 {@link RegisterResult}
     */
    public static RegisterResult attemptRegister(UUID uuid, String username, String ip, String password, String confirmPassword) {
        if (AuthManager.isLoggedIn(uuid)) {
            return RegisterResult.ALREADY_LOGGED_IN;
        }

        if (DatabaseManager.isRegistered(username)) {
            return RegisterResult.ALREADY_REGISTERED;
        }

        if (!password.equals(confirmPassword)) {
            return RegisterResult.PASSWORD_MISMATCH;
        }

        IAuthConfig config = ConfigManager.getInstance().getConfig();
        if (config.getMinPasswordLength() > 0 && password.length() < config.getMinPasswordLength()) {
            return RegisterResult.PASSWORD_TOO_SHORT;
        }
        if (config.getMaxPasswordLength() > 0 && password.length() > config.getMaxPasswordLength()) {
            return RegisterResult.PASSWORD_TOO_LONG;
        }

        boolean success = DatabaseManager.registerPlayer(username, password, ip);
        if (success) {
            AuthManager.setLoggedIn(uuid);
            return RegisterResult.SUCCESS;
        } else {
            return RegisterResult.DATABASE_ERROR;
        }
    }

    /**
     * 修改密碼結果列舉。
     */
    public enum ChangePasswordResult {
        SUCCESS("changepassword.success"),
        NOT_LOGGED_IN("changepassword.not_logged_in"),
        WRONG_OLD_PASSWORD("changepassword.wrong_old_password"),
        PASSWORD_MISMATCH("changepassword.password_mismatch"),
        PASSWORD_SAME("changepassword.password_same"),
        PASSWORD_TOO_SHORT("register.password_too_short"),
        PASSWORD_TOO_LONG("register.password_too_long"),
        DATABASE_ERROR("general.database_error");

        private final String messageKey;

        ChangePasswordResult(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public String getMessage() {
            IAuthConfig config = ConfigManager.getInstance().getConfig();
            MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
            if (this == PASSWORD_TOO_SHORT) {
                return msgMgr.get(messageKey, config.getMinPasswordLength());
            } else if (this == PASSWORD_TOO_LONG) {
                return msgMgr.get(messageKey, config.getMaxPasswordLength());
            }
            return msgMgr.get(messageKey);
        }
    }

    /**
     * 嘗試執行玩家自訂修改密碼流程。
     *
     * @param uuid        玩家 UUID
     * @param username    玩家名稱
     * @param oldPassword 舊密碼
     * @param newPassword 新密碼
     * @return 修改結果 {@link ChangePasswordResult}
     */
    public static ChangePasswordResult attemptChangePassword(UUID uuid, String username, String oldPassword, String newPassword) {
        return attemptChangePassword(uuid, username, oldPassword, newPassword, newPassword);
    }

    /**
     * 嘗試執行玩家自訂修改密碼流程 (含新密碼二次確認)。
     *
     * @param uuid            玩家 UUID
     * @param username        玩家名稱
     * @param oldPassword     舊密碼
     * @param newPassword     新密碼
     * @param confirmPassword 確認新密碼
     * @return 修改結果 {@link ChangePasswordResult}
     */
    public static ChangePasswordResult attemptChangePassword(UUID uuid, String username, String oldPassword, String newPassword, String confirmPassword) {
        if (!AuthManager.isLoggedIn(uuid)) {
            return ChangePasswordResult.NOT_LOGGED_IN;
        }

        if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
            return ChangePasswordResult.PASSWORD_MISMATCH;
        }

        if (!DatabaseManager.checkPassword(username, oldPassword)) {
            return ChangePasswordResult.WRONG_OLD_PASSWORD;
        }

        if (oldPassword.equals(newPassword)) {
            return ChangePasswordResult.PASSWORD_SAME;
        }

        IAuthConfig config = ConfigManager.getInstance().getConfig();
        if (config.getMinPasswordLength() > 0 && newPassword.length() < config.getMinPasswordLength()) {
            return ChangePasswordResult.PASSWORD_TOO_SHORT;
        }
        if (config.getMaxPasswordLength() > 0 && newPassword.length() > config.getMaxPasswordLength()) {
            return ChangePasswordResult.PASSWORD_TOO_LONG;
        }

        boolean success = DatabaseManager.changePassword(username, newPassword);
        return success ? ChangePasswordResult.SUCCESS : ChangePasswordResult.DATABASE_ERROR;
    }

    /**
     * 管理員強制修改指定玩家密碼。
     *
     * @param username    玩家名稱
     * @param newPassword 新密碼
     * @return 修改結果 {@link ChangePasswordResult}
     */
    public static ChangePasswordResult attemptAdminChangePassword(String username, String newPassword) {
        if (!DatabaseManager.isRegistered(username)) {
            return ChangePasswordResult.DATABASE_ERROR;
        }

        IAuthConfig config = ConfigManager.getInstance().getConfig();
        if (config.getMinPasswordLength() > 0 && newPassword.length() < config.getMinPasswordLength()) {
            return ChangePasswordResult.PASSWORD_TOO_SHORT;
        }
        if (config.getMaxPasswordLength() > 0 && newPassword.length() > config.getMaxPasswordLength()) {
            return ChangePasswordResult.PASSWORD_TOO_LONG;
        }

        boolean success = DatabaseManager.changePassword(username, newPassword);
        return success ? ChangePasswordResult.SUCCESS : ChangePasswordResult.DATABASE_ERROR;
    }

    /**
     * 嘗試執行玩家登出流程。
     *
     * @param uuid     玩家 UUID
     * @param username 玩家名稱
     * @return true 若原本已登入並成功登出，否則為 false
     */
    public static boolean attemptLogout(UUID uuid, String username) {
        if (!AuthManager.isLoggedIn(uuid)) {
            return false;
        }
        AuthManager.setLoggedOut(uuid);
        if (username != null) {
            DatabaseManager.updateQuit(username);
        }
        return true;
    }

    /**
     * 嘗試執行玩家登出流程。
     *
     * @param uuid 玩家 UUID
     * @return true 若原本已登入並成功登出，否則為 false
     */
    public static boolean attemptLogout(UUID uuid) {
        return attemptLogout(uuid, null);
    }

    /**
     * 執行登入、註冊或登出所觸發的自訂指令掛鉤 (Hooks)。
     *
     * @param serverOrSource 伺服器物件或指令來源
     * @param playerObj      玩家物件 (可為 null)
     * @param username       玩家名稱
     * @param consoleCmds    Console 執行的指令清單
     * @param playerCmds     以玩家身分執行的指令清單
     */
    public static void executeHooks(Object serverOrSource, Object playerObj, String username, java.util.List<String> consoleCmds, java.util.List<String> playerCmds) {
        if (username == null) return;
        if (consoleCmds != null && !consoleCmds.isEmpty() && serverOrSource != null) {
            for (String cmd : consoleCmds) {
                if (cmd != null && !cmd.isBlank()) {
                    String formatted = cmd.replace("{PLAYER}", username).replace("%player%", username);
                    Services.PLATFORM.executeConsoleCommand(serverOrSource, formatted);
                }
            }
        }
        if (playerCmds != null && !playerCmds.isEmpty() && playerObj != null) {
            for (String cmd : playerCmds) {
                if (cmd != null && !cmd.isBlank()) {
                    String formatted = cmd.replace("{PLAYER}", username).replace("%player%", username);
                    Services.PLATFORM.executePlayerCommand(playerObj, formatted);
                }
            }
        }
    }

    /**
     * 檢查未登入狀態下執行的指令是否為合法的驗證指令。
     *
     * @param rawCommand 玩家輸入的指令字串
     * @return true 若允許未登入玩家執行
     */
    public static boolean isCommandAllowed(String rawCommand) {
        return ConfigManager.getInstance().getCommandsConfig().isCommandAllowed(rawCommand);
    }

    /**
     * 處理玩家登入進服事件的核心判定。
     * <p>
     * 規則：
     * 1. 若玩家為正版（isPremium）且已在資料庫完成註冊，則自動放行並登入，且更新登入時間與 IP。
     * 2. 若玩家為正版但「第一次進入」（資料庫無帳號紀錄），仍然要求先執行 /register 註冊。
     * 3. 若玩家為離線玩家，進入待登入/註冊狀態。
     *
     * @param uuid      玩家 UUID
     * @param username  玩家名稱
     * @param ip        玩家連線 IP
     * @param isPremium 是否為通過 Mojang 驗證之正版玩家
     * @return true 若玩家成功自動正版登入，false 若需要玩家手動註冊或登入
     */
    public static boolean handlePlayerJoin(UUID uuid, String username, String ip, boolean isPremium) {
        if (isPremium && DatabaseManager.isRegistered(username)) {
            AuthManager.setLoggedIn(uuid);
            DatabaseManager.updateLogin(username, ip);
            return true;
        } else {
            AuthManager.setLoggedOut(uuid);
            return false;
        }
    }

    /**
     * 取得給未登入玩家的提示訊息。
     *
     * @param username 玩家名稱
     * @return 提示文字
     */
    public static String getPromptMessage(String username) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        if (DatabaseManager.isRegistered(username)) {
            return msgMgr.get("login.login_prompt");
        } else {
            return msgMgr.get("register.register_prompt");
        }
    }
}
