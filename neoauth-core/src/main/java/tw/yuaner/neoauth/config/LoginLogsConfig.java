package tw.yuaner.neoauth.config;

import java.util.Map;

public class LoginLogsConfig {
    private boolean enabled = false;
    private String serverName = "AUTO";

    public static LoginLogsConfig fromMap(Map<String, Object> map) {
        LoginLogsConfig config = new LoginLogsConfig();
        if (map == null || !map.containsKey("login_logs")) {
            return config;
        }
        
        Object rootObj = map.get("login_logs");
        if (rootObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) rootObj;
            
            if (root.containsKey("enabled")) {
                Object val = root.get("enabled");
                if (val instanceof Boolean) {
                    config.enabled = (Boolean) val;
                }
            }
            if (root.containsKey("server_name")) {
                Object val = root.get("server_name");
                if (val instanceof String) {
                    config.serverName = (String) val;
                }
            }
        }
        
        return config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
}
