package tw.yuaner.neoauth.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 語言訊息管理器 (支援 AuthMe 風格多國語言、顏色碼轉換與參數格式化)。
 */
public class MessagesManager {

    private final Map<String, String> messages = new HashMap<>();

    public MessagesManager() {}

    /**
     * 載入 messages_<lang>.yml 的 Map 內容並扁平化鍵值。
     *
     * @param map YAML 解析出的樹狀結構
     */
    public void loadMessages(Map<String, Object> map) {
        if (map == null) return;
        flattenMap("", map, messages);
    }

    @SuppressWarnings("unchecked")
    private void flattenMap(String prefix, Map<String, Object> current, Map<String, String> target) {
        for (Map.Entry<String, Object> entry : current.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> subMap) {
                flattenMap(key, (Map<String, Object>) subMap, target);
            } else if (entry.getValue() != null) {
                target.put(key, colorize(String.valueOf(entry.getValue())));
            }
        }
    }

    /**
     * 取得指定鍵值的提示訊息，並進行變數代換與格式化。
     *
     * @param key  訊息鍵值 (例如 "login.success")
     * @param args 格式化參數
     * @return 格式化後的彩色文字
     */
    public String get(String key, Object... args) {
        String msg = messages.get(key);
        if (msg == null) {
            msg = getFallback(key);
        }
        if (args != null && args.length > 0) {
            try {
                return String.format(msg, args);
            } catch (Exception e) {
                return msg;
            }
        }
        return msg;
    }

    /**
     * 將 Minecraft 常用色彩代碼符號 & 轉換為樣式符號 §。
     */
    public static String colorize(String text) {
        if (text == null) return "";
        return text.replace('&', '§');
    }

    /**
     * 預設內建後備訊息（若檔案中缺少某個 key 時使用）。
     */
    private String getFallback(String key) {
        return switch (key) {
            case "login.success" -> "§a登入成功！祝您遊戲愉快。";
            case "login.wrong_password" -> "§c密碼錯誤！請重新嘗試。";
            case "login.already_logged_in" -> "§c您已經處於登入狀態！";
            case "login.login_prompt" -> "§c請先登入！使用指令: §e/login <密碼>";
            case "login.timeout" -> "§c登入超時！您已花費過多時間未登入。";
            case "login.max_tries" -> "§c您已超過密碼嘗試次數上限！";
            case "register.success" -> "§a註冊並登入成功！祝您遊戲愉快。";
            case "register.already_registered" -> "§c此帳號已被註冊！請使用 §e/login <密碼> §c進行登入。";
            case "register.password_mismatch" -> "§c兩次輸入的密碼不相符！";
            case "register.password_too_short" -> "§c密碼長度過短！最少需要 %d 個字元。";
            case "register.password_too_long" -> "§c密碼長度過長！最多允許 %d 個字元。";
            case "register.register_prompt" -> "§c請先註冊！使用指令: §e/register <密碼> <確認密碼>";
            case "general.only_players" -> "§c只有玩家可以使用此指令！";
            case "general.database_error" -> "§c資料庫連線異常，請聯繫管理員。";
            case "general.welcome_premium" -> "§a歡迎回來，%s！ §7(已通過 Mojang 官方正版驗證)";
            case "general.command_not_allowed" -> "§c您必須先完成登入驗證才能使用其他指令！";
            case "general.action_blocked" -> "§c您必須先完成登入驗證才能執行此動作！";
            case "general.reload_success" -> "§aNeoAuth 設定檔與語言訊息已成功重新載入。";
            case "general.reload_failed" -> "§c重新載入 NeoAuth 設定檔失敗：%s";
            case "general.no_permission" -> "§c您沒有權限執行此指令！";
            default -> "§7[" + key + "]";
        };
    }
}
