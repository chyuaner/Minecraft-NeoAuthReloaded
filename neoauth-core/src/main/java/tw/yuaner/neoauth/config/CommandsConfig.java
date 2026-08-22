package tw.yuaner.neoauth.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 指令設定類別 (對應 config/neoauth/commands.yml)。
 */
public class CommandsConfig {

    private List<String> unauthenticatedCommands = new ArrayList<>();
    private List<String> onLoginConsole = new ArrayList<>();
    private List<String> onLoginPlayer = new ArrayList<>();
    private List<String> onRegisterConsole = new ArrayList<>();
    private List<String> onRegisterPlayer = new ArrayList<>();
    private List<String> onLogoutConsole = new ArrayList<>();
    private List<String> onLogoutPlayer = new ArrayList<>();

    public CommandsConfig() {
        // 預設白名單指令
        unauthenticatedCommands.add("/login");
        unauthenticatedCommands.add("/l");
        unauthenticatedCommands.add("/register");
        unauthenticatedCommands.add("/reg");
        unauthenticatedCommands.add("/help");
        unauthenticatedCommands.add("/neoauth");
    }

    @SuppressWarnings("unchecked")
    public static CommandsConfig fromMap(Map<String, Object> map) {
        CommandsConfig config = new CommandsConfig();
        if (map == null) return config;

        // unauthenticatedCommands
        Object unauthObj = map.get("unauthenticatedCommands");
        if (unauthObj instanceof List<?> list) {
            config.unauthenticatedCommands.clear();
            for (Object item : list) {
                if (item != null) {
                    config.unauthenticatedCommands.add(String.valueOf(item).trim().toLowerCase());
                }
            }
        }

        // onLogin
        parseHooks(map.get("onLogin"), config.onLoginConsole, config.onLoginPlayer);

        // onRegister
        parseHooks(map.get("onRegister"), config.onRegisterConsole, config.onRegisterPlayer);

        // onLogout
        parseHooks(map.get("onLogout"), config.onLogoutConsole, config.onLogoutPlayer);

        return config;
    }

    private static void parseHooks(Object obj, List<String> consoleList, List<String> playerList) {
        if (obj instanceof Map<?, ?> hookMap) {
            Object conObj = hookMap.get("console");
            if (conObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) consoleList.add(String.valueOf(item));
                }
            }
            Object plyObj = hookMap.get("player");
            if (plyObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) playerList.add(String.valueOf(item));
                }
            }
        }
    }

    /**
     * 檢查輸入的指令是否在未登入允許清單內。
     *
     * @param rawCommand 輸入指令 (例如 "/login 123456" 或 "l 123456")
     * @return true 若允許執行
     */
    public boolean isCommandAllowed(String rawCommand) {
        if (rawCommand == null) return false;
        String cmd = rawCommand.trim();
        if (!cmd.startsWith("/")) {
            cmd = "/" + cmd;
        }
        String lower = cmd.toLowerCase();

        for (String allowed : unauthenticatedCommands) {
            String norm = allowed.startsWith("/") ? allowed : "/" + allowed;
            if (lower.equals(norm) || lower.startsWith(norm + " ")) {
                return true;
            }
        }
        return false;
    }

    public List<String> getUnauthenticatedCommands() {
        return Collections.unmodifiableList(unauthenticatedCommands);
    }

    public List<String> getOnLoginConsole() {
        return Collections.unmodifiableList(onLoginConsole);
    }

    public List<String> getOnLoginPlayer() {
        return Collections.unmodifiableList(onLoginPlayer);
    }

    public List<String> getOnRegisterConsole() {
        return Collections.unmodifiableList(onRegisterConsole);
    }

    public List<String> getOnRegisterPlayer() {
        return Collections.unmodifiableList(onRegisterPlayer);
    }

    public List<String> getOnLogoutConsole() {
        return Collections.unmodifiableList(onLogoutConsole);
    }

    public List<String> getOnLogoutPlayer() {
        return Collections.unmodifiableList(onLogoutPlayer);
    }
}
