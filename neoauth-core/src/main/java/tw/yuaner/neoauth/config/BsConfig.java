package tw.yuaner.neoauth.config;

import java.util.Map;

/**
 * Blessing Skin 整合設定類別 (對應 config/neoauth/bs.yml)。
 */
public class BsConfig {

    private boolean enabled = false;
    private String apiUrl = "https://skin.example.com/api/premium-skin-sync";
    private String token = "your-secret-token";

    public BsConfig() {
    }

    public static BsConfig fromMap(Map<String, Object> map) {
        BsConfig config = new BsConfig();
        if (map == null) return config;

        if (map.get("enabled") instanceof Boolean b) config.enabled = b;
        if (map.get("apiUrl") instanceof String s) config.apiUrl = s;
        if (map.get("token") instanceof String s) config.token = s;

        return config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getToken() {
        return token;
    }
}
