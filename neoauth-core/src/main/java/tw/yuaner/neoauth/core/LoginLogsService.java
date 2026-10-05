package tw.yuaner.neoauth.core;

import tw.yuaner.neoauth.AuthManager;
import tw.yuaner.neoauth.DatabaseManager;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.MessagesManager;
import tw.yuaner.neoauth.database.LoginLogRecord;
import tw.yuaner.neoauth.util.ArgumentTokenizer;
import tw.yuaner.neoauth.util.TimeSpanParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 遊戲內登入日誌查詢服務 (/loginlogs 與 /neoauth logs)。
 */
public final class LoginLogsService {

    public static class QueryResult {
        private final boolean success;
        private final List<String> lines;

        public QueryResult(boolean success, List<String> lines) {
            this.success = success;
            this.lines = lines != null ? lines : Collections.emptyList();
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> getLines() {
            return lines;
        }
    }

    private LoginLogsService() {
    }

    /**
     * 處理 /loginlogs 或 /neoauth logs 指令查詢請求 (使用預設當前伺服器)。
     *
     * @param callerName 發起查詢的玩家名稱 (若為 Console 則為 null)
     * @param isAdmin    發起者是否具備管理員權限 (Permission Level >= 2 或 Console)
     * @param rawArgs    原始參數字串
     * @return {@link QueryResult}
     */
    public static QueryResult handleCommand(String callerName, boolean isAdmin, String rawArgs) {
        return handleCommand(callerName, isAdmin, rawArgs, tw.yuaner.neoauth.util.ServerIdentifier.getCurrentServerName());
    }

    /**
     * 處理 /loginlogs 或 /neoauth logs 指令查詢請求。
     *
     * @param callerName 發起查詢的玩家名稱 (若為 Console 則為 null)
     * @param isAdmin    發起者是否具備管理員權限 (Permission Level >= 2 或 Console)
     * @param rawArgs    原始參數字串
     * @param serverName 指定要查詢的伺服器名稱 (若為 null 則自動推導當前伺服器)
     * @return {@link QueryResult}
     */
    public static QueryResult handleCommand(String callerName, boolean isAdmin, String rawArgs, String serverName) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();

        if (rawArgs != null && (rawArgs.trim().equalsIgnoreCase("help") || rawArgs.trim().equals("?"))) {
            return new QueryResult(true, buildHelpLines(isAdmin));
        }

        String targetPlayer = callerName;
        int limit = 6;
        long sinceTimestamp = 0L;
        boolean isDefault = false;

        List<String> tokens = ArgumentTokenizer.tokenize(rawArgs);

        if (tokens.isEmpty()) {
            // 無參數：預設指向 6 筆紀錄，並在輸出結尾附帶簡短使用說明
            if (targetPlayer == null) {
                return new QueryResult(false, Collections.singletonList(msgMgr.get("admin.player_not_specified")));
            }
            limit = 6;
            isDefault = true;
        } else if (tokens.size() == 1) {
            String token = tokens.get(0);
            if (token.equalsIgnoreCase("help") || token.equals("?")) {
                return new QueryResult(true, buildHelpLines(isAdmin));
            }

            if (TimeSpanParser.isCount(token)) {
                if (targetPlayer == null) {
                    return new QueryResult(false, Collections.singletonList(msgMgr.get("admin.player_not_specified")));
                }
                limit = TimeSpanParser.parseCount(token, 6, 100);
            } else if (TimeSpanParser.isDuration(token)) {
                if (targetPlayer == null) {
                    return new QueryResult(false, Collections.singletonList(msgMgr.get("admin.player_not_specified")));
                }
                TimeSpanParser.ParseResult pr = TimeSpanParser.parse(token);
                if (!pr.isValid()) {
                    return new QueryResult(false, Collections.singletonList(msgMgr.get("loginlogs.invalid_param", token)));
                }
                sinceTimestamp = System.currentTimeMillis() - pr.getDurationMillis();
                limit = 100;
            } else {
                // 非數字亦非時長代碼，判斷是否為管理員指定查詢對象玩家
                if (isAdmin) {
                    targetPlayer = token;
                    limit = 6;
                } else {
                    return new QueryResult(false, Collections.singletonList(msgMgr.get("loginlogs.no_permission_other")));
                }
            }
        } else {
            // 2 個以上參數 (例如: /loginlogs <玩家> <時間/筆數>)
            if (!isAdmin) {
                return new QueryResult(false, Collections.singletonList(msgMgr.get("loginlogs.no_permission_other")));
            }

            String arg0 = tokens.get(0);
            String arg1 = tokens.get(1);
            String filterArg;

            if (TimeSpanParser.isCount(arg0) || TimeSpanParser.isDuration(arg0)) {
                filterArg = arg0;
                targetPlayer = arg1;
            } else {
                targetPlayer = arg0;
                filterArg = arg1;
            }

            if (TimeSpanParser.isCount(filterArg)) {
                limit = TimeSpanParser.parseCount(filterArg, 6, 100);
            } else if (TimeSpanParser.isDuration(filterArg)) {
                TimeSpanParser.ParseResult pr = TimeSpanParser.parse(filterArg);
                if (!pr.isValid()) {
                    return new QueryResult(false, Collections.singletonList(msgMgr.get("loginlogs.invalid_param", filterArg)));
                }
                sinceTimestamp = System.currentTimeMillis() - pr.getDurationMillis();
                limit = 100;
            } else {
                return new QueryResult(false, Collections.singletonList(msgMgr.get("loginlogs.invalid_param", filterArg)));
            }
        }

        String srv = serverName != null && !serverName.isBlank() ? serverName : tw.yuaner.neoauth.util.ServerIdentifier.getCurrentServerName();
        return executeQuery(srv, targetPlayer, sinceTimestamp, limit, isDefault);
    }

    private static QueryResult executeQuery(String serverName, String targetPlayer, long sinceTimestamp, int limit, boolean isDefault) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        List<LoginLogRecord> logs = DatabaseManager.getLoginLogs(serverName, targetPlayer, sinceTimestamp, limit);

        List<String> lines = new ArrayList<>();
        if (logs.isEmpty()) {
            lines.add(msgMgr.get("loginlogs.none"));
            if (ConfigManager.getInstance().getLoginLogsConfig() != null && !ConfigManager.getInstance().getLoginLogsConfig().isEnabled()) {
                lines.add(msgMgr.get("loginlogs.disabled_hint"));
            }
            if (isDefault) {
                appendDefaultTips(lines, msgMgr);
            }
            return new QueryResult(true, lines);
        }

        String displayTarget = targetPlayer != null && !targetPlayer.isBlank() ? targetPlayer : "All";
        lines.add(msgMgr.get("loginlogs.header", displayTarget, logs.size()));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        boolean isZh = isTraditionalChinese();

        for (LoginLogRecord record : logs) {
            String loginTimeStr = sdf.format(new Date(record.getLoginTime()));
            String playTimeStr;

            if (record.hasValidLogout()) {
                long duration = Math.max(0, record.getLogoutTime() - record.getLoginTime());
                playTimeStr = TimeSpanParser.formatDuration(duration, isZh);
            } else {
                // 檢查是否為當前在線上的活躍會話
                PlayerSessionData session = AuthManager.getSessionByName(record.getUsername());
                if (session != null && (session.getLoginLogId() == record.getId() || session.getLoginLogId() <= 0)) {
                    long duration = Math.max(0, System.currentTimeMillis() - record.getLoginTime());
                    playTimeStr = TimeSpanParser.formatDuration(duration, isZh) + " (" + msgMgr.get("loginlogs.status_online") + ")";
                } else {
                    playTimeStr = msgMgr.get("loginlogs.unknown_duration");
                }
            }

            String ip = record.getIp() != null && !record.getIp().isBlank() ? record.getIp() : "127.0.0.1";
            lines.add(msgMgr.get("loginlogs.item", loginTimeStr, playTimeStr, ip));
        }

        if (isDefault) {
            appendDefaultTips(lines, msgMgr);
        }

        return new QueryResult(true, lines);
    }

    private static void appendDefaultTips(List<String> lines, MessagesManager msgMgr) {
        lines.add(msgMgr.get("loginlogs.tip_header"));
        lines.add(msgMgr.get("loginlogs.tip_footer"));
    }

    public static List<String> buildHelpLines(boolean isAdmin) {
        MessagesManager msgMgr = ConfigManager.getInstance().getMessagesManager();
        List<String> lines = new ArrayList<>();
        lines.add(msgMgr.get("loginlogs.help_title"));
        lines.add(msgMgr.get("loginlogs.help_desc"));
        lines.add(msgMgr.get("loginlogs.help_format_title"));
        lines.add(msgMgr.get("loginlogs.help_units"));
        lines.add(msgMgr.get("loginlogs.help_example_title"));
        lines.add(msgMgr.get("loginlogs.help_ex_default"));
        lines.add(msgMgr.get("loginlogs.help_ex_count"));
        lines.add(msgMgr.get("loginlogs.help_ex_1d"));
        lines.add(msgMgr.get("loginlogs.help_ex_1h"));
        lines.add(msgMgr.get("loginlogs.help_ex_3w"));
        lines.add(msgMgr.get("loginlogs.help_ex_1m"));
        lines.add(msgMgr.get("loginlogs.help_ex_2M"));
        if (isAdmin) {
            lines.add(msgMgr.get("loginlogs.help_ex_admin"));
        }
        return lines;
    }

    public static List<String> getSuggestions(boolean isAdmin) {
        List<String> suggestions = new ArrayList<>(Arrays.asList(
                "help", "6", "10", "20",
                "1h", "2h", "1d", "3d", "5d", "1w", "3w", "1M", "3mo", "1y"
        ));
        return suggestions;
    }

    private static boolean isTraditionalChinese() {
        if (ConfigManager.getInstance().getConfig() != null) {
            String lang = ConfigManager.getInstance().getConfig().getMessagesLanguage();
            return lang == null || lang.equalsIgnoreCase("zhtw") || lang.equalsIgnoreCase("zh_tw") || lang.equalsIgnoreCase("zh");
        }
        return true;
    }
}
