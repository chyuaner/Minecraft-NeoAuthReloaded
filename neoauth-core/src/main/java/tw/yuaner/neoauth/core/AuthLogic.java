package tw.yuaner.neoauth.core;

import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.DatabaseManager;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.IAuthConfig;
import tw.yuaner.neoauth.config.MessagesManager;

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
     * 檢查未登入狀態下執行的指令是否為合法的驗證指令。
     *
     * @param rawCommand 玩家輸入的指令字串
     * @return true 若允許未登入玩家執行
     */
    public static boolean isCommandAllowed(String rawCommand) {
        return ConfigManager.getInstance().getCommandsConfig().isCommandAllowed(rawCommand);
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
