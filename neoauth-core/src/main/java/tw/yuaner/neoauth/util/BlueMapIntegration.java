package tw.yuaner.neoauth.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.yuaner.neoauth.config.ConfigManager;
import tw.yuaner.neoauth.config.IAuthConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * BlueMap 網頁地圖玩家頭像自動同步工具類。
 * <p>
 * 當伺服器運行於離線模式 (online-mode: false) 時，玩家在伺服器端分配的是離線 UUID (v3)。
 * BlueMap 在網頁端渲染地圖上的玩家時，會向伺服器請求對應 offlineUuid 的頭像 PNG 圖檔。
 * <p>
 * 本類別在玩家通過 Mojang 官方或自架第三方皮膚站 (如 Blessing Skin / authlib-injector) 驗證時，
 * 非同步抓取其正版頭像並透過 BlueMap API 的 AssetStorage (支援 SQL / MariaDB / File 等任意儲存庫)
 * 以及本機地圖檔案目錄寫入頭像，完美解決網頁地圖顯示為史蒂夫預設頭像之問題。
 * <p>
 * 支援原生讀取與連動 SkinRestorer (SkinsRestorer v15+) 模組之 join.autoFetch.providers 與 custom 自架皮膚站設定。
 */
public class BlueMapIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-BlueMap");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<UUID, byte[]> PENDING_HEADS = new ConcurrentHashMap<>();
    private static volatile Object activeBlueMapApi = null;

    static {
        registerBlueMapListener();
    }

    /**
     * SkinRestorer 設定解析結果封裝。
     */
    public record SkinRestorerSettings(
            Map<String, String> customProviders,
            List<String> orderedProviders,
            List<String> customAvatarUrlTemplates,
            String priority
    ) {
        public boolean hasCustomSources() {
            return customAvatarUrlTemplates != null && !customAvatarUrlTemplates.isEmpty();
        }

        /**
         * 依據 SkinRestorer 的 join.autoFetch.providers 順序建立下載候選網址清單。
         *
         * @param officialUrlTemplate 官方正版頭像網址範本
         * @return 排序後的頭像下載網址範本清單
         */
        public List<String> buildCandidateUrls(String officialUrlTemplate) {
            List<String> candidates = new ArrayList<>();
            if (orderedProviders != null && !orderedProviders.isEmpty()) {
                for (String provider : orderedProviders) {
                    if (provider == null || provider.isBlank()) continue;
                    String lower = provider.trim().toLowerCase();
                    if ("mojang".equals(lower) || "official".equals(lower)) {
                        if (officialUrlTemplate != null && !officialUrlTemplate.isBlank() && !candidates.contains(officialUrlTemplate)) {
                            candidates.add(officialUrlTemplate);
                        }
                    } else if (customProviders.containsKey(lower)) {
                        String url = customProviders.get(lower);
                        if (url != null && !url.isBlank() && !candidates.contains(url)) {
                            candidates.add(url);
                        }
                    } else if ("custom".equals(lower)) {
                        for (String url : customAvatarUrlTemplates) {
                            if (url != null && !url.isBlank() && !candidates.contains(url)) {
                                candidates.add(url);
                            }
                        }
                    }
                }
            }

            // 若尚有未涵蓋的 custom 或 official 來源，依優先順序補齊作為末尾備援
            if ("CUSTOM_FIRST".equalsIgnoreCase(priority)) {
                for (String url : customAvatarUrlTemplates) {
                    if (url != null && !url.isBlank() && !candidates.contains(url)) {
                        candidates.add(url);
                    }
                }
                if (officialUrlTemplate != null && !officialUrlTemplate.isBlank() && !candidates.contains(officialUrlTemplate)) {
                    candidates.add(officialUrlTemplate);
                }
            } else {
                if (officialUrlTemplate != null && !officialUrlTemplate.isBlank() && !candidates.contains(officialUrlTemplate)) {
                    candidates.add(officialUrlTemplate);
                }
                for (String url : customAvatarUrlTemplates) {
                    if (url != null && !url.isBlank() && !candidates.contains(url)) {
                        candidates.add(url);
                    }
                }
            }

            return candidates;
        }
    }

    /**
     * 非同步執行 BlueMap 正版頭像下載與同步。
     *
     * @param username    玩家名稱
     * @param offlineUuid 玩家在遊戲中使用的離線 UUID
     * @return 包含同步成功與否的 CompletableFuture
     */
    public static CompletableFuture<Boolean> syncBlueMapPlayerHead(String username, UUID offlineUuid) {
        return syncBlueMapPlayerHead(username, offlineUuid, false);
    }

    /**
     * 非同步執行 BlueMap 正版或外置站頭像下載與同步。
     *
     * @param username     玩家名稱
     * @param offlineUuid  玩家在遊戲中使用的離線 UUID
     * @param isCustomAuth 是否為自訂外置驗證站登入
     * @return 包含同步成功與否的 CompletableFuture
     */
    public static CompletableFuture<Boolean> syncBlueMapPlayerHead(String username, UUID offlineUuid, boolean isCustomAuth) {
        return CompletableFuture.supplyAsync(() -> {
            if (username == null || username.isBlank() || offlineUuid == null) {
                return false;
            }

            IAuthConfig config = ConfigManager.getInstance().getConfig();
            if (config != null && !config.isBlueMapIntegrationEnabled()) {
                return false;
            }

            // 檢查伺服器是否安裝了 BlueMap（若無任何 BlueMap 資料夾且 API 未載入則直接略過，零網路開銷）
            if (!hasBlueMapDirectory(Path.of(".")) && !isBlueMapApiLoaded()) {
                return false;
            }

            try {
                byte[] headBytes = fetchPlayerHeadBytes(config, username, offlineUuid, isCustomAuth);
                if (headBytes == null || headBytes.length == 0) {
                    LOGGER.warn("NeoAuth: 無法取得玩家 {} ({}) 的頭像圖檔數據。", username, offlineUuid);
                    return false;
                }

                // 1. 優先透過 BlueMap API 的 AssetStorage 寫入（支援 SQL/MariaDB/PostgreSQL/File 任意儲存後端）
                int apiCount = writeHeadToBlueMapApi(offlineUuid, headBytes);

                // 2. 同步寫入本機檔案系統 BlueMap 目錄作為備援與靜態檔案支援
                int fileCount = writeHeadToAllMaps(Path.of("."), offlineUuid, headBytes);

                int totalCount = Math.max(apiCount, fileCount);
                if (totalCount > 0) {
                    LOGGER.info("NeoAuth: 已成功為玩家 {} ({}) 同步 BlueMap 網頁地圖頭像 (API儲存寫入: {} 個地圖, 檔案目錄寫入: {} 個)。",
                            username, offlineUuid, apiCount, fileCount);
                    return true;
                } else {
                    LOGGER.debug("NeoAuth: 未找到任何 BlueMap 地圖或儲存後端可寫入頭像 ({})。", username);
                    return false;
                }
            } catch (Exception e) {
                LOGGER.warn("NeoAuth: 同步 BlueMap 頭像時發生錯誤 ({}): {}", username, e.getMessage());
                return false;
            }
        });
    }

    /**
     * 檢查 BlueMap API 類別是否已在 ClassLoader 中載入。
     */
    public static boolean isBlueMapApiLoaded() {
        try {
            Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 檢查指定根目錄下是否存在 BlueMap 相關目錄結構。
     */
    public static boolean hasBlueMapDirectory(Path rootDir) {
        return Files.exists(rootDir.resolve("bluemap"))
                || Files.exists(rootDir.resolve("bluemap/web/maps"))
                || Files.exists(rootDir.resolve("bluemap/maps"))
                || Files.exists(rootDir.resolve("bluemap/web/assets"));
    }

    /**
     * 依設定優先順序下載玩家頭像圖檔位元組陣列。
     */
    public static byte[] fetchPlayerHeadBytes(IAuthConfig config, String username, UUID offlineUuid, boolean isCustomAuth) {
        String officialUrlTemplate = "https://mc-heads.net/avatar/{username}/64";
        List<String> customUrlTemplates = new ArrayList<>();
        String priority = "OFFICIAL_FIRST";

        if (config != null) {
            if (config.getBlueMapAvatarUrl() != null && !config.getBlueMapAvatarUrl().isBlank()) {
                officialUrlTemplate = config.getBlueMapAvatarUrl().trim();
            }
            if (config.getBlueMapPriority() != null && !config.getBlueMapPriority().isBlank()) {
                priority = config.getBlueMapPriority().trim().toUpperCase();
            }
            if (config.getBlueMapCustomAvatarUrl() != null && !config.getBlueMapCustomAvatarUrl().isBlank()) {
                customUrlTemplates.add(config.getBlueMapCustomAvatarUrl().trim());
            }
        }

        // 1. 若啟用 SkinRestorer 連動設定，嘗試讀取 SkinRestorer 設定檔 (join.autoFetch.providers 與 custom)
        boolean useSkinRestorer = (config == null || config.isUseSkinRestorerConfig());
        List<String> candidates = null;
        if (useSkinRestorer) {
            SkinRestorerSettings srSettings = parseSkinRestorerConfig(Path.of("."));
            if (srSettings != null) {
                LOGGER.debug("NeoAuth: 成功連動讀取 SkinRestorer 設定 (providers: {}, 自架站數: {})",
                        srSettings.orderedProviders(), srSettings.customAvatarUrlTemplates().size());
                candidates = srSettings.buildCandidateUrls(officialUrlTemplate);
            }
        }

        // 2. 若未連動或未找到 SkinRestorer，使用獨立設定建立候選下載清單
        if (candidates == null || candidates.isEmpty()) {
            candidates = new ArrayList<>();
            boolean isOfficialFirst = !"CUSTOM_FIRST".equalsIgnoreCase(priority);
            if (isOfficialFirst) {
                if (!officialUrlTemplate.isBlank()) {
                    candidates.add(officialUrlTemplate);
                }
                for (String custom : customUrlTemplates) {
                    if (!custom.isBlank() && !candidates.contains(custom)) {
                        candidates.add(custom);
                    }
                }
            } else {
                for (String custom : customUrlTemplates) {
                    if (!custom.isBlank() && !candidates.contains(custom)) {
                        candidates.add(custom);
                    }
                }
                if (!officialUrlTemplate.isBlank() && !candidates.contains(officialUrlTemplate)) {
                    candidates.add(officialUrlTemplate);
                }
            }
        }

        // 3. 若此玩家明確是通過自訂外置驗證站登入，自訂皮膚站來源優先
        if (isCustomAuth && candidates != null && !candidates.isEmpty()) {
            List<String> reordered = new ArrayList<>();
            for (String c : candidates) {
                if (!c.equals(officialUrlTemplate)) {
                    reordered.add(c);
                }
            }
            if (!officialUrlTemplate.isBlank() && !reordered.contains(officialUrlTemplate)) {
                reordered.add(officialUrlTemplate);
            }
            candidates = reordered;
        }

        // 4. 逐一嘗試下載頭像圖檔，取得第一個成功的結果
        for (String template : candidates) {
            String url = formatAvatarUrl(template, username, offlineUuid);
            byte[] bytes = downloadImage(url);
            if (bytes != null && bytes.length > 0) {
                return bytes;
            }
        }

        return null;
    }

    /**
     * 掃描並解析指定根目錄下 SkinRestorer (SkinsRestorer) 的設定檔。
     * <p>
     * 支援解析：
     * <ul>
     *   <li>{@code custom} 自架站清單（提取 name 與 baseUrl）。</li>
     *   <li>{@code join.autoFetch.providers} 服務優先順序清單（例如 {@code ["mojang", "myblessingskin"]}）。</li>
     * </ul>
     *
     * @param rootDir 伺服器根目錄
     * @return {@link SkinRestorerSettings} 若成功讀取，否則為 null
     */
    public static SkinRestorerSettings parseSkinRestorerConfig(Path rootDir) {
        if (rootDir == null) return null;

        String[] candidatePaths = new String[]{
                "config/skinrestorer/config.json",
                "config/skinsrestorer/config.json",
                "plugins/SkinsRestorer/config.json",
                "plugins/SkinRestorer/config.json"
        };

        for (String relPath : candidatePaths) {
            Path path = rootDir.resolve(relPath);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonElement jsonElem = JsonParser.parseReader(reader);
                    if (jsonElem.isJsonObject()) {
                        JsonObject root = jsonElem.getAsJsonObject();
                        Map<String, String> customProviders = new LinkedHashMap<>();
                        List<String> customUrls = new ArrayList<>();

                        // 解析 custom 區塊 (支援 SkinsRestorer v15 的 providers.custom 與舊版 root.custom)
                        JsonArray customArr = null;
                        if (root.has("providers") && root.get("providers").isJsonObject()) {
                            JsonObject provObj = root.getAsJsonObject("providers");
                            if (provObj.has("custom") && provObj.get("custom").isJsonArray()) {
                                customArr = provObj.getAsJsonArray("custom");
                            }
                            if (provObj.has("ely_by") && provObj.get("ely_by").isJsonObject()) {
                                customProviders.put("ely_by", "http://skinsystem.ely.by/avatars/{username}");
                                customProviders.put("ely.by", "http://skinsystem.ely.by/avatars/{username}");
                            }
                        }
                        if (customArr == null && root.has("custom") && root.get("custom").isJsonArray()) {
                            customArr = root.getAsJsonArray("custom");
                        }

                        if (customArr != null) {
                            for (JsonElement item : customArr) {
                                if (item.isJsonObject()) {
                                    JsonObject customObj = item.getAsJsonObject();
                                    if (customObj.has("enabled") && !customObj.get("enabled").getAsBoolean()) {
                                        continue;
                                    }
                                    if (customObj.has("baseUrl") && !customObj.get("baseUrl").isJsonNull()) {
                                        String baseUrl = customObj.get("baseUrl").getAsString();
                                        String avatarTemplate = deriveAvatarTemplateFromBaseUrl(baseUrl);
                                        if (!avatarTemplate.isBlank()) {
                                            if (customObj.has("name") && !customObj.get("name").isJsonNull()) {
                                                String name = customObj.get("name").getAsString().trim().toLowerCase();
                                                customProviders.put(name, avatarTemplate);
                                            }
                                            customProviders.putIfAbsent("custom", avatarTemplate);
                                            if (!customUrls.contains(avatarTemplate)) {
                                                customUrls.add(avatarTemplate);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 解析服務順序：優先讀取 join.autoFetch.providers
                        List<String> orderedProviders = new ArrayList<>();
                        String priority = "OFFICIAL_FIRST";

                        JsonArray providersArr = null;
                        if (root.has("join") && root.get("join").isJsonObject()) {
                            JsonObject joinObj = root.getAsJsonObject("join");
                            if (joinObj.has("autoFetch") && joinObj.get("autoFetch").isJsonObject()) {
                                JsonObject autoFetchObj = joinObj.getAsJsonObject("autoFetch");
                                if (autoFetchObj.has("providers") && autoFetchObj.get("providers").isJsonArray()) {
                                    providersArr = autoFetchObj.getAsJsonArray("providers");
                                }
                            }
                        }

                        // 相容 fallback 鍵：order / service_order / services
                        if (providersArr == null) {
                            if (root.has("order") && root.get("order").isJsonArray()) {
                                providersArr = root.getAsJsonArray("order");
                            } else if (root.has("service_order") && root.get("service_order").isJsonArray()) {
                                providersArr = root.getAsJsonArray("service_order");
                            } else if (root.has("services") && root.get("services").isJsonArray()) {
                                providersArr = root.getAsJsonArray("services");
                            }
                        }

                        if (providersArr != null && !providersArr.isEmpty()) {
                            for (JsonElement p : providersArr) {
                                if (p != null && !p.isJsonNull()) {
                                    orderedProviders.add(p.getAsString().trim().toLowerCase());
                                }
                            }
                            if (!orderedProviders.isEmpty()) {
                                String first = orderedProviders.get(0);
                                if ("mojang".equals(first) || "official".equals(first)) {
                                    priority = "OFFICIAL_FIRST";
                                } else {
                                    priority = "CUSTOM_FIRST";
                                }
                            }
                        }

                        return new SkinRestorerSettings(
                                Collections.unmodifiableMap(customProviders),
                                Collections.unmodifiableList(orderedProviders),
                                Collections.unmodifiableList(customUrls),
                                priority
                        );
                    }
                } catch (Exception e) {
                    LOGGER.debug("NeoAuth: 解析 SkinRestorer 設定檔失敗 ({}): {}", relPath, e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * 由 SkinRestorer 的 baseUrl (例如 "https://mc8.yuaner.tw/api/yggdrasil") 推導 Blessing Skin 頭像網址範本。
     */
    public static String deriveAvatarTemplateFromBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return "";
        String clean = baseUrl.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.endsWith("/api/yggdrasil")) {
            clean = clean.substring(0, clean.length() - "/api/yggdrasil".length());
        } else if (clean.endsWith("/sessionserver")) {
            clean = clean.substring(0, clean.length() - "/sessionserver".length());
        }
        return clean + "/avatar/player/{username}?size=64";
    }

    /**
     * 格式化頭像網址範本，替換 {username}、{uuid} 與 {size} 佔位符。
     */
    public static String formatAvatarUrl(String template, String username, UUID offlineUuid) {
        if (template == null) return "";
        String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String uuidStr = offlineUuid != null ? offlineUuid.toString() : "";
        return template
                .replace("{username}", encodedName)
                .replace("{player}", encodedName)
                .replace("{uuid}", uuidStr)
                .replace("{size}", "64");
    }

    /**
     * 從指定 URL 下載圖片位元組陣列。
     */
    public static byte[] downloadImage(String urlString) {
        if (urlString == null || urlString.isBlank()) return null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "NeoAuth-BlueMap/1.0.0")
                    .header("Accept", "image/png,image/webp,image/*;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                // 檢查 mc-heads.net 等官方 API 的 X-Account-Valid 標頭
                // 若玩家不存在於 Mojang 官方 (x-account-valid: false)，mc-heads.net 會回傳預設史蒂夫並標記 false，
                // 此時必須視為該來源無此頭像 (回傳 null)，以便無縫回退至自架第三方皮膚站 (Blessing Skin)！
                Optional<String> accountValidHeader = response.headers().firstValue("X-Account-Valid");
                if (accountValidHeader.isPresent() && "false".equalsIgnoreCase(accountValidHeader.get().trim())) {
                    LOGGER.debug("NeoAuth: 頭像來源回傳 X-Account-Valid: false，玩家不存在於該官方來源，繼續回退下一來源: {}", urlString);
                    return null;
                }

                byte[] body = response.body();
                if (isValidPngOrImage(body)) {
                    return body;
                }
            } else {
                LOGGER.debug("NeoAuth: 下載頭像 HTTP 狀態碼異常 ({}): {}", response.statusCode(), urlString);
            }
        } catch (Exception e) {
            LOGGER.debug("NeoAuth: 下載頭像連線失敗 ({}): {}", urlString, e.getMessage());
        }
        return null;
    }

    /**
     * 簡單驗證是否為非空的圖檔資料 (包含 PNG 魔術字節檢查)。
     */
    public static boolean isValidPngOrImage(byte[] data) {
        if (data == null || data.length < 8) return false;
        // PNG magic bytes: 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return true;
        }
        // 若伺服器回傳 WebP 或 JPEG，只要大於 32 bytes 亦視為有效圖檔
        return data.length >= 32;
    }

    /**
     * 註冊 BlueMap 生命週期監聽器，確保 BlueMap 異步載入完成時能即時補寫已快取的玩家頭像。
     */
    public static void registerBlueMapListener() {
        try {
            Class<?> apiClass = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            Method onEnableMethod = apiClass.getMethod("onEnable", Consumer.class);
            onEnableMethod.invoke(null, (Consumer<Object>) api -> {
                LOGGER.info("NeoAuth: 偵測到 BlueMap API 已就緒，正在自動同步已快取的玩家頭像至 BlueMap 儲存庫...");
                activeBlueMapApi = api;
                CompletableFuture.runAsync(() -> flushPendingHeadsToApi(api));
            });
            Method onDisableMethod = apiClass.getMethod("onDisable", Consumer.class);
            onDisableMethod.invoke(null, (Consumer<Object>) api -> {
                activeBlueMapApi = null;
            });
        } catch (ClassNotFoundException ignored) {
            // 伺服器未安裝 BlueMap 模組
        } catch (Throwable e) {
            LOGGER.debug("NeoAuth: 註冊 BlueMap 生命週期監聽時發生異常: {}", e.getMessage());
        }
    }

    /**
     * 透過 BlueMap API 將頭像寫入所有地圖的 AssetStorage（支援 SQL / MariaDB / File 等任意儲存後端）。
     *
     * @param offlineUuid 離線 UUID
     * @param headBytes   頭像 PNG 資料
     * @return 成功寫入的地圖數量
     */
    public static int writeHeadToBlueMapApi(UUID offlineUuid, byte[] headBytes) {
        if (offlineUuid == null || headBytes == null || headBytes.length == 0) {
            return 0;
        }

        // 1. 先加入待同步快取佇列，確保若 BlueMap 尚未加載完畢，能在加載完成時自動補寫
        PENDING_HEADS.put(offlineUuid, headBytes);

        try {
            Class<?> apiClass = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            Method getInstanceMethod = apiClass.getMethod("getInstance");
            @SuppressWarnings("unchecked")
            Optional<Object> optApi = (Optional<Object>) getInstanceMethod.invoke(null);

            Object api = (optApi != null && optApi.isPresent()) ? optApi.get() : activeBlueMapApi;
            if (api != null) {
                Method getMapsMethod = api.getClass().getMethod("getMaps");
                @SuppressWarnings("unchecked")
                Collection<Object> maps = (Collection<Object>) getMapsMethod.invoke(api);

                if (maps == null || maps.isEmpty()) {
                    return 0;
                }

                int count = 0;
                String assetPath = "playerheads/" + offlineUuid + ".png";

                for (Object map : maps) {
                    try {
                        Method getAssetStorageMethod = map.getClass().getMethod("getAssetStorage");
                        Object assetStorage = getAssetStorageMethod.invoke(map);
                        if (assetStorage != null) {
                            Method writeAssetMethod = assetStorage.getClass().getMethod("writeAsset", String.class);
                            try (OutputStream out = (OutputStream) writeAssetMethod.invoke(assetStorage, assetPath)) {
                                if (out != null) {
                                    out.write(headBytes);
                                    out.flush();
                                    count++;
                                }
                            }
                        }
                    } catch (Exception mapEx) {
                        LOGGER.debug("NeoAuth: 透過 BlueMap API 寫入地圖頭像失敗: {}", mapEx.getMessage());
                    }
                }
                return count;
            }
        } catch (ClassNotFoundException ignored) {
            // BlueMap API 未載入
        } catch (Exception e) {
            LOGGER.debug("NeoAuth: 調用 BlueMap API 寫入頭像時發生異常: {}", e.getMessage());
        }

        return 0;
    }

    /**
     * 將佇列中的所有玩家頭像寫入指定的 BlueMap API 實例。
     */
    private static void flushPendingHeadsToApi(Object api) {
        if (api == null || PENDING_HEADS.isEmpty()) return;
        try {
            Method getMapsMethod = api.getClass().getMethod("getMaps");
            @SuppressWarnings("unchecked")
            Collection<Object> maps = (Collection<Object>) getMapsMethod.invoke(api);
            if (maps == null || maps.isEmpty()) return;

            int totalUpdated = 0;
            for (Map.Entry<UUID, byte[]> entry : PENDING_HEADS.entrySet()) {
                UUID uuid = entry.getKey();
                byte[] bytes = entry.getValue();
                String assetPath = "playerheads/" + uuid + ".png";

                int written = 0;
                for (Object map : maps) {
                    try {
                        Method getAssetStorageMethod = map.getClass().getMethod("getAssetStorage");
                        Object assetStorage = getAssetStorageMethod.invoke(map);
                        if (assetStorage != null) {
                            Method writeAssetMethod = assetStorage.getClass().getMethod("writeAsset", String.class);
                            try (OutputStream out = (OutputStream) writeAssetMethod.invoke(assetStorage, assetPath)) {
                                if (out != null) {
                                    out.write(bytes);
                                    out.flush();
                                    written++;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (written > 0) {
                    totalUpdated++;
                }
            }
            if (totalUpdated > 0) {
                LOGGER.info("NeoAuth: 已在 BlueMap 就緒後自動補寫 {} 位玩家的頭像至 BlueMap 儲存庫。", totalUpdated);
            }
        } catch (Exception e) {
            LOGGER.debug("NeoAuth: flushPendingHeadsToApi 異常: {}", e.getMessage());
        }
    }

    /**
     * 將頭像圖檔寫入 rootDir 下所有 BlueMap 地圖的 assets/playerheads/ 目錄中。
     *
     * @param rootDir     伺服器根路徑 (通常為 Path.of("."))
     * @param offlineUuid 離線 UUID
     * @param headBytes   頭像 PNG 資料
     * @return 成功寫入的地圖目錄數量
     */
    public static int writeHeadToAllMaps(Path rootDir, UUID offlineUuid, byte[] headBytes) {
        if (offlineUuid == null || headBytes == null || headBytes.length == 0) {
            return 0;
        }

        int count = 0;
        String fileName = offlineUuid.toString() + ".png";

        // 1. 寫入 bluemap/web/maps/<mapId>/assets/playerheads/
        Path bluemapWebMaps = rootDir.resolve("bluemap/web/maps");
        if (Files.exists(bluemapWebMaps) && Files.isDirectory(bluemapWebMaps)) {
            try (Stream<Path> stream = Files.list(bluemapWebMaps)) {
                for (Path mapDir : stream.filter(Files::isDirectory).toList()) {
                    Path playerHeadsDir = mapDir.resolve("assets/playerheads");
                    if (writeBytesToFile(playerHeadsDir, fileName, headBytes)) {
                        count++;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        // 2. 寫入 bluemap/maps/<mapId>/assets/playerheads/ (若存在)
        Path bluemapMaps = rootDir.resolve("bluemap/maps");
        if (Files.exists(bluemapMaps) && Files.isDirectory(bluemapMaps)) {
            try (Stream<Path> stream = Files.list(bluemapMaps)) {
                for (Path mapDir : stream.filter(Files::isDirectory).toList()) {
                    Path playerHeadsDir = mapDir.resolve("assets/playerheads");
                    if (writeBytesToFile(playerHeadsDir, fileName, headBytes)) {
                        count++;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        // 3. 寫入全域 bluemap/web/assets/playerheads/ (若存在)
        Path bluemapWebAssets = rootDir.resolve("bluemap/web/assets/playerheads");
        if (Files.exists(rootDir.resolve("bluemap/web/assets"))) {
            if (writeBytesToFile(bluemapWebAssets, fileName, headBytes)) {
                count++;
            }
        }

        return count;
    }

    private static boolean writeBytesToFile(Path dir, String fileName, byte[] data) {
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path targetFile = dir.resolve(fileName);
            Files.write(targetFile, data);
            return true;
        } catch (Exception e) {
            LOGGER.debug("NeoAuth: 寫入頭像檔案失敗至 {}: {}", dir, e.getMessage());
            return false;
        }
    }
}
