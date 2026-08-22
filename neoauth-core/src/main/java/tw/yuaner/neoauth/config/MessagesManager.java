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
            case "changepassword.success" -> "§a密碼修改成功！請牢記您的新密碼。";
            case "changepassword.wrong_old_password" -> "§c舊密碼輸入錯誤！";
            case "changepassword.not_logged_in" -> "§c您必須先完成登入才能修改密碼！";
            case "changepassword.password_same" -> "§c新密碼不可與舊密碼相同！";
            case "logout.success" -> "§a您已成功登出！請使用 §e/login <密碼> §a重新登入。";
            case "logout.not_logged_in" -> "§c您目前尚未登入！";
            case "email.show" -> "§e您目前綁定的電子郵件為: §f%s";
            case "email.none" -> "§c您目前尚未綁定任何電子郵件。";
            case "admin.register_success" -> "§a已成功為玩家 §e%s §a註冊帳號！";
            case "admin.register_already_registered" -> "§c玩家 §e%s §c已經註冊過帳號！";
            case "admin.forcelogin_success" -> "§a已成功強制玩家 §e%s §a登入！";
            case "admin.forcelogin_self_success" -> "§a您已成功強制登入！";
            case "admin.player_not_online" -> "§c玩家 §e%s §c目前不在線上！";
            case "admin.player_not_specified" -> "§c請指定欲操作的玩家名稱！";
            case "admin.player_not_found" -> "§c在資料庫中找不到玩家 §e%s §c的資料！";
            case "admin.password_changed_success" -> "§a已成功將玩家 §e%s §a的密碼修改！";
            case "admin.lastlogin_info" -> "§6[%s] §e最後登入: §f%s §e| IP: §f%s §e| 註冊時間: §f%s";
            case "admin.lastlogin_never" -> "從未登入";
            case "admin.accounts_header" -> "§6===== 關聯帳號清單 (%s) 共 %d 個帳號 =====";
            case "admin.accounts_item" -> "§e- §f%s";
            case "admin.accounts_none" -> "§c找不到任何關聯帳號。";
            case "admin.email_info" -> "§6[%s] §e電子郵件: §f%s";
            case "admin.email_none" -> "§6[%s] §c未設定電子郵件。";
            case "admin.email_updated" -> "§a已成功將玩家 §e%s §a的電子郵件設定為: §f%s";
            case "admin.getip_info" -> "§6[%s] §eIP 位址: §f%s";
            case "admin.getip_unknown" -> "§c無法取得玩家 §e%s §c的 IP 位址！";
            case "admin.spawn_set_success" -> "§a已成功將一般登入點設定為您當前的位置！";
            case "admin.firstspawn_set_success" -> "§a已成功將首次進入登入點設定為您當前的位置！";
            case "admin.spawn_teleport_success" -> "§a已將您傳送至登入點。";
            case "admin.spawn_not_enabled" -> "§c登入點尚未設定或未啟用！請先使用 §e/neoauth setspawn §c設定。";
            case "admin.firstspawn_not_enabled" -> "§c首次登入點尚未設定或未啟用！請先使用 §e/neoauth setfirstspawn §c設定。";
            case "admin.resetpos_player_success" -> "§a已重設玩家 §e%s §a的離線座標！下次進入伺服器時將於重生點出現。";
            case "admin.resetpos_all_success" -> "§a已重設全體玩家 (%d 筆) 的離線座標！";
            case "admin.recent_header" -> "§6===== 最近登入玩家清單 =====";
            case "admin.recent_item" -> "§e- §f%s §7(%s) - §7IP: %s";
            case "admin.recent_none" -> "§c目前尚無任何登入紀錄。";
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
