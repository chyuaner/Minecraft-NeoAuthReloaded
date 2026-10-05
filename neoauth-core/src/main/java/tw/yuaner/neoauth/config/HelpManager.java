package tw.yuaner.neoauth.config;

import java.util.*;

/**
 * 指令幫助訊息管理器 (支援 help.yml / help_<lang>.yml 載入、動態解析與後備文案)。
 */
public class HelpManager {

    public static class CommandHelpEntry {
        private final String usage;
        private final String description;

        public CommandHelpEntry(String usage, String description) {
            this.usage = usage != null ? usage : "";
            this.description = description != null ? description : "";
        }

        public String getUsage() {
            return usage;
        }

        public String getDescription() {
            return description;
        }

        public String formatLine() {
            return "§e" + usage + " §7- " + description;
        }
    }

    private String title = "§6--- NeoAuthReloaded 指令幫助指南 ---";
    private String adminHeader = "§6===== §eNeoAuth 管理員指令清單 §6=====";
    private String notFoundMessage = "§c找不到符合指令「§e%s§c」的幫助資訊！輸入 §e/neoauth help §c查看清單。";
    private final Map<String, CommandHelpEntry> commands = new LinkedHashMap<>();

    public HelpManager() {
        initDefaultFallbacks();
    }

    public void loadHelp(Map<String, Object> map) {
        if (map == null) return;
        Object helpObj = map.get("help");
        if (helpObj instanceof Map<?, ?> helpMap) {
            if (helpMap.get("title") != null) {
                this.title = MessagesManager.colorize(String.valueOf(helpMap.get("title")));
            }
            if (helpMap.get("admin_header") != null) {
                this.adminHeader = MessagesManager.colorize(String.valueOf(helpMap.get("admin_header")));
            }
            if (helpMap.get("not_found") != null) {
                this.notFoundMessage = MessagesManager.colorize(String.valueOf(helpMap.get("not_found")));
            }

            Object commandsObj = helpMap.get("commands");
            if (commandsObj instanceof Map<?, ?> cmdMap) {
                commands.clear();
                for (Map.Entry<?, ?> entry : cmdMap.entrySet()) {
                    String cmdKey = String.valueOf(entry.getKey()).toLowerCase();
                    if (entry.getValue() instanceof Map<?, ?> valMap) {
                        String usage = valMap.get("usage") != null ? MessagesManager.colorize(String.valueOf(valMap.get("usage"))) : "";
                        String desc = valMap.get("description") != null ? MessagesManager.colorize(String.valueOf(valMap.get("description"))) : "";
                        commands.put(cmdKey, new CommandHelpEntry(usage, desc));
                    }
                }
            }
        }
    }

    public String getTitle() {
        return title;
    }

    public String getAdminHeader() {
        return adminHeader;
    }

    public Map<String, CommandHelpEntry> getCommands() {
        return Collections.unmodifiableMap(commands);
    }

    /**
     * 取得管理員指令幫助清單文字行（包含 header 與所有管理員指令）。
     */
    public List<String> getAdminHelpLines() {
        boolean logsEnabled = ConfigManager.getInstance().getLoginLogsConfig() != null
                && ConfigManager.getInstance().getLoginLogsConfig().isEnabled();
        List<String> lines = new ArrayList<>();
        lines.add(adminHeader);
        for (Map.Entry<String, CommandHelpEntry> entry : commands.entrySet()) {
            if (entry.getKey().startsWith("neoauth_") || entry.getKey().startsWith("neoauth")) {
                if (!logsEnabled && (entry.getKey().equals("neoauth_logs") || entry.getKey().equals("neoauth_log"))) {
                    continue;
                }
                lines.add(entry.getValue().formatLine());
            }
        }
        return lines;
    }

    /**
     * 查詢特定指令的幫助訊息行。
     */
    public List<String> getCommandHelpLines(String query) {
        if (query == null || query.isBlank()) {
            return getAdminHelpLines();
        }
        String cleanQuery = query.trim().toLowerCase();
        if (cleanQuery.startsWith("/")) {
            cleanQuery = cleanQuery.substring(1);
        }
        if (cleanQuery.startsWith("neoauth ")) {
            cleanQuery = cleanQuery.substring("neoauth ".length()).trim();
        }

        List<String> result = new ArrayList<>();
        CommandHelpEntry entry = commands.get(cleanQuery);
        if (entry == null) {
            entry = commands.get("neoauth_" + cleanQuery);
        }
        if (entry == null) {
            for (Map.Entry<String, CommandHelpEntry> e : commands.entrySet()) {
                if (e.getKey().equalsIgnoreCase(cleanQuery) || e.getKey().endsWith("_" + cleanQuery)) {
                    entry = e.getValue();
                    break;
                }
            }
        }

        if (entry != null) {
            result.add(title);
            result.add(entry.formatLine());
        } else {
            result.add(String.format(notFoundMessage, query));
        }
        return result;
    }

    private void initDefaultFallbacks() {
        commands.put("login", new CommandHelpEntry("/login <密碼>", "使用已註冊的密碼登入伺服器。"));
        commands.put("l", new CommandHelpEntry("/l <密碼>", "/login 的簡寫別名。"));
        commands.put("register", new CommandHelpEntry("/register <密碼> [確認密碼]", "為您的帳號註冊一組新密碼。"));
        commands.put("reg", new CommandHelpEntry("/reg <密碼> [確認密碼]", "/register 的簡寫別名。"));
        commands.put("changepassword", new CommandHelpEntry("/changepassword <舊密碼> <新密碼> <確認新密碼>", "修改已登入帳號的密碼。"));
        commands.put("cp", new CommandHelpEntry("/cp <舊密碼> <新密碼> <確認新密碼>", "/changepassword 的簡寫別名。"));
        commands.put("logout", new CommandHelpEntry("/logout", "登出目前帳號。"));
        commands.put("email", new CommandHelpEntry("/email [show | set <電子郵件>]", "查詢或設定您綁定的電子郵件。"));
        commands.put("lastlogin", new CommandHelpEntry("/lastlogin", "查詢您自己的最後登入時間與註冊日期。"));
        commands.put("getip", new CommandHelpEntry("/getip", "查詢您目前的連線 IP 位址。"));
        commands.put("neoauth_register", new CommandHelpEntry("/neoauth register <玩家> <密碼>", "為指定玩家手動註冊帳號與密碼（管理員）。"));
        commands.put("neoauth_forcelogin", new CommandHelpEntry("/neoauth forcelogin [玩家]", "強制登入指定玩家或自己（管理員）。"));
        commands.put("neoauth_password", new CommandHelpEntry("/neoauth password <玩家> <新密碼>", "修改指定玩家的密碼（管理員）。"));
        commands.put("neoauth_lastlogin", new CommandHelpEntry("/neoauth lastlogin [玩家]", "查詢指定玩家最後登入時間與 IP（管理員）。"));
        commands.put("neoauth_accounts", new CommandHelpEntry("/neoauth accounts [玩家/IP]", "查詢同 IP 所關聯的所有帳號名稱（管理員）。"));
        commands.put("neoauth_email", new CommandHelpEntry("/neoauth email [show <玩家> | set <玩家> <電子郵件>]", "查詢或設定指定玩家的電子郵件（管理員）。"));
        commands.put("neoauth_setemail", new CommandHelpEntry("/neoauth setemail <玩家> <電子郵件>", "設定指定玩家的電子郵件（管理員）。"));
        commands.put("neoauth_getip", new CommandHelpEntry("/neoauth getip <玩家>", "取得指定玩家的連線 IP（管理員）。"));
        commands.put("neoauth_reload", new CommandHelpEntry("/neoauth reload", "重新載入 NeoAuthReloaded 所有設定檔與語言訊息（管理員）。"));
        commands.put("neoauth_version", new CommandHelpEntry("/neoauth version", "顯示 NeoAuthReloaded 模組版本與運行資訊（管理員）。"));
        commands.put("neoauth_recent", new CommandHelpEntry("/neoauth recent", "顯示最近登入伺服器的玩家清單（管理員）。"));
        commands.put("neoauth_setenableregister", new CommandHelpEntry("/neoauth setenableregister <true|false>", "設定伺服器是否開放遊戲內註冊，並自動保存至設定檔（管理員）。"));
        commands.put("neoauth_cb_status", new CommandHelpEntry("/neoauth cb [status]", "檢查 Mojang 驗證熔斷狀態（管理員）。"));
        commands.put("neoauth_cb_reset", new CommandHelpEntry("/neoauth cb reset", "手動重置 Mojang 驗證熔斷狀態（管理員）。"));
        commands.put("neoauth_cb_trip", new CommandHelpEntry("/neoauth cb trip [秒數]", "手動觸發 Mojang 驗證熔斷狀態（管理員）。"));
        commands.put("loginlogs", new CommandHelpEntry("/loginlogs [時間範圍/筆數]", "查詢自己近期登入紀錄、遊玩時間與當時 IP 位址。"));
        commands.put("neoauth_logs", new CommandHelpEntry("/neoauth logs [玩家] [時間範圍/筆數]", "查詢指定玩家或自己的歷史登入紀錄（管理員）。"));
    }
}
