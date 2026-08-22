package tw.yuaner.neoauth.core;

import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.DatabaseManager;

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
        SUCCESS("§a成功登入！祝您遊戲愉快。"),
        ALREADY_LOGGED_IN("§c您已經處於登入狀態！"),
        NOT_REGISTERED("§c此帳號尚未註冊！請使用 /register <密碼> <確認密碼> 進行註冊。"),
        WRONG_PASSWORD("§c密碼錯誤！請重新嘗試。"),
        DATABASE_ERROR("§c資料庫連線異常，請聯繫管理員。");

        private final String message;

        LoginResult(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 註冊結果列舉。
     */
    public enum RegisterResult {
        SUCCESS("§a註冊並登入成功！祝您遊戲愉快。"),
        ALREADY_LOGGED_IN("§c您已經處於登入狀態！"),
        ALREADY_REGISTERED("§c此帳號已被註冊！請使用 /login <密碼> 進行登入。"),
        PASSWORD_MISMATCH("§c兩次輸入的密碼不相符！"),
        DATABASE_ERROR("§c資料庫連線異常，請聯繫管理員。");

        private final String message;

        RegisterResult(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
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

        boolean success = DatabaseManager.registerPlayer(username, password, ip);
        if (success) {
            AuthManager.setLoggedIn(uuid);
            return RegisterResult.SUCCESS;
        } else {
            return RegisterResult.DATABASE_ERROR;
        }
    }

    /**
     * 檢查未登入狀態下執行的指令是否為合法的驗證指令（例如 /login, /register, /l, /reg）。
     *
     * @param rawCommand 玩家輸入的指令字串
     * @return true 若允許未登入玩家執行
     */
    public static boolean isCommandAllowed(String rawCommand) {
        if (rawCommand == null) return false;
        String cmd = rawCommand.trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        String lower = cmd.toLowerCase();
        return lower.startsWith("login") || lower.startsWith("l ") || lower.equals("l")
                || lower.startsWith("register") || lower.startsWith("reg ") || lower.equals("reg");
    }

    /**
     * 取得給未登入玩家的提示訊息。
     *
     * @param username 玩家名稱
     * @return 提示文字
     */
    public static String getPromptMessage(String username) {
        if (DatabaseManager.isRegistered(username)) {
            return "§c請先登入！使用指令: /login <密碼>";
        } else {
            return "§c請先註冊！使用指令: /register <密碼> <確認密碼>";
        }
    }
}
