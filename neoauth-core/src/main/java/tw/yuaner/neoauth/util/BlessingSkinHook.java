package tw.yuaner.neoauth.util;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.yuaner.neoauth.config.BsConfig;
import tw.yuaner.neoauth.config.ConfigManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 負責處理將正版玩家的皮膚推播至 Blessing Skin 網站的非同步任務。
 */
public class BlessingSkinHook {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-BS");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 若已在設定檔中啟用，則發送皮膚同步 Webhook 到 Blessing Skin。
     *
     * @param uuid  玩家 UUID
     * @param name  玩家名稱
     * @param value Base64 Textures JSON
     */
    public static void syncSkinAsync(UUID uuid, String name, String value) {
        if (uuid == null || name == null || value == null || value.isBlank()) return;

        BsConfig config = ConfigManager.getInstance().getBsConfig();
        if (config == null || !config.isEnabled()) return;

        String apiUrl = config.getApiUrl();
        String token = config.getToken();

        if (apiUrl == null || apiUrl.isBlank()) return;

        CompletableFuture.runAsync(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("uuid", uuid.toString());
                json.addProperty("username", name);
                json.addProperty("texture_value", value);
                json.addProperty("token", token);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    LOGGER.info("成功同步玩家 {} 的正版皮膚至 Blessing Skin。", name);
                } else {
                    LOGGER.warn("同步玩家 {} 皮膚至 Blessing Skin 失敗，狀態碼: {} - 回應: {}", name, response.statusCode(), response.body());
                }
            } catch (Exception e) {
                LOGGER.error("同步玩家 {} 皮膚至 Blessing Skin 發生錯誤: {}", name, e.getMessage());
            }
        });
    }
}
