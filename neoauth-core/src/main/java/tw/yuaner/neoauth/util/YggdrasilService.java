package tw.yuaner.neoauth.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 自訂 Yggdrasil API (hasJoined) Session 驗證服務。
 * <p>
 * 支援向第三方外置登入伺服器 (如 Blessing Skin, LittleSkin 等自建 Yggdrasil 站) 進行 Session 查詢與驗證，
 * 並解析玩家的皮膚與披風屬性 (Textures)。
 */
public class YggdrasilService {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-Yggdrasil");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Yggdrasil 驗證結果結構。
     */
    public record YggdrasilAuthResult(boolean success, String id, String name, String texturesValue, String texturesSignature) {
        public static YggdrasilAuthResult failure() {
            return new YggdrasilAuthResult(false, null, null, null, null);
        }
    }

    /**
     * 向指定的 Yggdrasil 驗證伺服器確認玩家是否已加入伺服器。
     *
     * @param yggdrasilUrl 自訂 Yggdrasil API 根網址 (例如: https://mc8.yuaner.tw/api/yggdrasil)
     * @param username     玩家名稱
     * @param serverId     Sha1 雜湊字串 (digest)
     * @param ip           客戶端 IP 位址 (可為空)
     * @return true 若外置驗證伺服器確認 Session 有效
     */
    public static boolean hasJoined(String yggdrasilUrl, String username, String serverId, String ip) {
        return verifyJoined(yggdrasilUrl, username, serverId, ip).success();
    }

    /**
     * 向指定的 Yggdrasil 驗證伺服器驗證玩家 Session，並提取包含 Textures 在內的完整資料。
     *
     * @param yggdrasilUrl 自訂 Yggdrasil API 根網址
     * @param username     玩家名稱
     * @param serverId     Sha1 雜湊字串
     * @param ip           客戶端 IP 位址 (可為空)
     * @return 驗證結果 {@link YggdrasilAuthResult}
     */
    public static YggdrasilAuthResult verifyJoined(String yggdrasilUrl, String username, String serverId, String ip) {
        if (yggdrasilUrl == null || yggdrasilUrl.isBlank() || username == null || serverId == null) {
            return YggdrasilAuthResult.failure();
        }

        try {
            String baseUrl = yggdrasilUrl.trim();
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(baseUrl);
            if (!baseUrl.contains("/sessionserver")) {
                urlBuilder.append("/sessionserver");
            }
            if (!baseUrl.endsWith("/hasJoined")) {
                urlBuilder.append("/session/minecraft/hasJoined");
            }
            urlBuilder.append("?username=").append(URLEncoder.encode(username, StandardCharsets.UTF_8));
            urlBuilder.append("&serverId=").append(URLEncoder.encode(serverId, StandardCharsets.UTF_8));
            if (ip != null && !ip.isBlank()) {
                urlBuilder.append("&ip=").append(URLEncoder.encode(ip, StandardCharsets.UTF_8));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "NeoAuth-MultiAuth/1.0.0")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    String id = root.has("id") && !root.get("id").isJsonNull() ? root.get("id").getAsString() : null;
                    String name = root.has("name") && !root.get("name").isJsonNull() ? root.get("name").getAsString() : username;
                    String texturesValue = null;
                    String texturesSignature = null;

                    if (root.has("properties") && root.get("properties").isJsonArray()) {
                        JsonArray props = root.getAsJsonArray("properties");
                        for (JsonElement elem : props) {
                            if (elem.isJsonObject()) {
                                JsonObject propObj = elem.getAsJsonObject();
                                if (propObj.has("name") && "textures".equals(propObj.get("name").getAsString())) {
                                    if (propObj.has("value") && !propObj.get("value").isJsonNull()) {
                                        texturesValue = propObj.get("value").getAsString();
                                    }
                                    if (propObj.has("signature") && !propObj.get("signature").isJsonNull()) {
                                        texturesSignature = propObj.get("signature").getAsString();
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    return new YggdrasilAuthResult(true, id, name, texturesValue, texturesSignature);
                }
            } else if (response.statusCode() != 204 && response.statusCode() != 404) {
                LOGGER.debug("Yggdrasil 驗證伺服器回應狀態碼: {}", response.statusCode());
            }
        } catch (Exception e) {
            LOGGER.warn("連線至自訂 Yggdrasil 驗證伺服器失敗 ({}): {}", yggdrasilUrl, e.getMessage());
        }
        return YggdrasilAuthResult.failure();
    }
}
