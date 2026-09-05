package tw.yuaner.neoauth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import tw.yuaner.neoauth.DatabaseManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NeoAuth 全域設定檔與資源管理器。
 * <p>
 * 負責在伺服器目錄建立 config/neoauth/ 目錄架構、釋出預設範本、解析各項 YAML 設定檔、
 * 自動合併補齊缺失的設定鍵值並 100% 保留原始註解 (Comment-Preserving Deep Merge) 及支援熱重載。
 */
public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-Config");
    private static final ConfigManager INSTANCE = new ConfigManager();

    private Path configDir = Path.of("config", "neoauth");
    private Path messagesDir = configDir.resolve("messages");

    private NeoAuthConfig config = new NeoAuthConfig();
    private CommandsConfig commandsConfig = new CommandsConfig();
    private MessagesManager messagesManager = new MessagesManager();
    private String welcomeMessage = "";

    private ConfigManager() {}

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    public void setConfigDir(Path dir) {
        this.configDir = dir != null ? dir : Path.of("config", "neoauth");
        this.messagesDir = this.configDir.resolve("messages");
    }

    /**
     * 初始化設定檔管理器：確保檔案存在並完成初次載入與合併。
     */
    public static void init() {
        INSTANCE.load();
    }

    /**
     * 載入（或重載）所有設定檔並自動合併補齊缺失的鍵值（保留完整註解）。
     */
    public synchronized void load() {
        try {
            ensureDirectories();

            // 1. 載入並合併 config.yml
            Map<String, Object> configData = loadAndMergeYaml("config.yml", configDir.resolve("config.yml"));
            this.config = NeoAuthConfig.fromMap(configData);

            // 2. 載入並合併 commands.yml
            Map<String, Object> commandsData = loadAndMergeYaml("commands.yml", configDir.resolve("commands.yml"));
            this.commandsConfig = CommandsConfig.fromMap(commandsData);

            // 3. 載入 welcome.txt
            Path welcomePath = configDir.resolve("welcome.txt");
            if (!Files.exists(welcomePath)) {
                copyDefaultResource("welcome.txt", welcomePath);
            }
            if (Files.exists(welcomePath)) {
                this.welcomeMessage = Files.readString(welcomePath, StandardCharsets.UTF_8);
            } else {
                this.welcomeMessage = "";
            }

            // 5. 確保語言檔範本存在並自動合併
            loadAndMergeYaml("messages/messages_zhtw.yml", messagesDir.resolve("messages_zhtw.yml"));
            loadAndMergeYaml("messages/messages_en.yml", messagesDir.resolve("messages_en.yml"));
            loadAndMergeYaml("messages/help_zhtw.yml", messagesDir.resolve("help_zhtw.yml"));
            loadAndMergeYaml("messages/help_en.yml", messagesDir.resolve("help_en.yml"));

            // 6. 載入當前語言訊息檔
            String lang = config.getMessagesLanguage() != null ? config.getMessagesLanguage().toLowerCase() : "zhtw";
            File msgFile = messagesDir.resolve("messages_" + lang + ".yml").toFile();
            if (!msgFile.exists()) {
                msgFile = messagesDir.resolve("messages_en.yml").toFile();
            }

            MessagesManager newMsgMgr = new MessagesManager();
            if (msgFile.exists()) {
                Yaml yaml = new Yaml();
                try (InputStream in = new FileInputStream(msgFile)) {
                    Map<String, Object> data = yaml.load(in);
                    newMsgMgr.loadMessages(data);
                }
            }
            this.messagesManager = newMsgMgr;

            LOGGER.info("NeoAuth: 成功載入設定檔 (語言模式: {})", lang);
        } catch (Exception e) {
            LOGGER.error("NeoAuth: 載入設定檔時發生錯誤！", e);
        }
    }

    /**
     * 熱重載設定檔與資料庫連線。
     */
    public synchronized boolean reload() {
        try {
            load();
            DatabaseManager.init();
            return true;
        } catch (Exception e) {
            LOGGER.error("NeoAuth: 熱重載失敗", e);
            return false;
        }
    }

    /**
     * 確保 config/neoauth/ 與 messages/ 目錄存在。
     */
    private void ensureDirectories() {
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            if (!Files.exists(messagesDir)) {
                Files.createDirectories(messagesDir);
            }
        } catch (Exception e) {
            LOGGER.error("NeoAuth: 建立設定檔目錄失敗！", e);
        }
    }

    /**
     * 讀取磁碟上的 YAML 檔案，若檔案不存在則釋出內建完整註解預設範本；
     * 若檔案已存在且缺少部分鍵值，則以內建範本為基礎進行帶註解之深度合併 (Comment-Preserving Deep Merge)。
     *
     * @param resourcePath 內建資源路徑 (例如: "config.yml")
     * @param diskPath     磁碟目標路徑
     * @return 合併後的完整 Map 資料
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadAndMergeYaml(String resourcePath, Path diskPath) {
        Yaml yaml = new Yaml();
        String templateText = "";
        Map<String, Object> defaultMap = new LinkedHashMap<>();

        // 讀取內建預設範本文字與 Map
        try (InputStream in = getClass().getResourceAsStream("/defaults/" + resourcePath)) {
            if (in != null) {
                templateText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> loaded = yaml.load(templateText);
                if (loaded != null) {
                    defaultMap = loaded;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("NeoAuth: 讀取內建預設範本 /defaults/{} 失敗: {}", resourcePath, e.getMessage());
        }

        // 若目標檔案不存在，直接釋出完整範本檔案 (含所有註解)
        if (!Files.exists(diskPath)) {
            try {
                if (diskPath.getParent() != null && !Files.exists(diskPath.getParent())) {
                    Files.createDirectories(diskPath.getParent());
                }
                Files.writeString(diskPath, templateText, StandardCharsets.UTF_8);
                LOGGER.info("NeoAuth: 已釋出預設範本檔案至 {}", diskPath);
            } catch (Exception e) {
                LOGGER.error("NeoAuth: 釋出範本檔案 {} 失敗: {}", resourcePath, e.getMessage());
            }
            return defaultMap;
        }

        // 若目標檔案已存在，讀取既有設定
        Map<String, Object> diskMap = new LinkedHashMap<>();
        try (InputStream in = new FileInputStream(diskPath.toFile())) {
            Map<String, Object> loaded = yaml.load(in);
            if (loaded != null) {
                diskMap = loaded;
            }
        } catch (Exception e) {
            LOGGER.error("NeoAuth: 讀取磁碟設定檔 {} 失敗，將使用預設設定: {}", diskPath, e.getMessage());
            return defaultMap;
        }

        // 檢查是否缺失任何鍵值，若缺失則以保留註解方式合併
        if (isMissingAnyKey(defaultMap, diskMap)) {
            String mergedText = YamlCommentPreserver.mergePreservingComments(templateText, diskMap);
            try {
                Files.writeString(diskPath, mergedText, StandardCharsets.UTF_8);
                LOGGER.info("NeoAuth: 已自動合併並補齊設定檔缺失項目與註解至 {}", diskPath);
                Map<String, Object> reloaded = yaml.load(mergedText);
                if (reloaded != null) {
                    diskMap = reloaded;
                }
            } catch (Exception e) {
                LOGGER.error("NeoAuth: 儲存合併設定檔 {} 失敗 (請檢查檔案與目錄之寫入權限): {}", diskPath, e.getMessage());
            }
        }

        return diskMap;
    }

    /**
     * 遞迴檢查 target 是否缺少 defaults 中的任何鍵值。
     */
    @SuppressWarnings("unchecked")
    private boolean isMissingAnyKey(Map<String, Object> defaults, Map<String, Object> target) {
        if (defaults == null || target == null) return false;
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            if (!target.containsKey(key)) {
                return true;
            }
            if (entry.getValue() instanceof Map<?, ?> defSub && target.get(key) instanceof Map<?, ?> tgtSub) {
                if (isMissingAnyKey((Map<String, Object>) defSub, (Map<String, Object>) tgtSub)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void copyDefaultResource(String resourcePath, Path targetPath) {
        try (InputStream in = getClass().getResourceAsStream("/defaults/" + resourcePath)) {
            if (in != null) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("NeoAuth: 已釋出預設範本檔案至 {}", targetPath);
            } else {
                LOGGER.warn("NeoAuth: 找不到內建預設範本: /defaults/{}", resourcePath);
            }
        } catch (Exception e) {
            LOGGER.error("NeoAuth: 釋出範本檔案 {} 失敗: {}", resourcePath, e.getMessage());
        }
    }

    public NeoAuthConfig getConfig() {
        return config;
    }

    public void setConfig(NeoAuthConfig config) {
        this.config = config != null ? config : new NeoAuthConfig();
    }

    public CommandsConfig getCommandsConfig() {
        return commandsConfig;
    }

    public MessagesManager getMessagesManager() {
        return messagesManager;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    /**
     * 取得替換玩家名稱並轉換色彩代碼後的歡迎公告。
     */
    public String getFormattedWelcomeMessage(String playerName) {
        if (welcomeMessage == null || welcomeMessage.isEmpty()) return "";
        String text = welcomeMessage.replace("{PLAYER}", playerName != null ? playerName : "Player");
        return MessagesManager.colorize(text);
    }

    /**
     * 動態設定是否開放遊戲內註冊，並將變更回寫至 config/neoauth/config.yml (保留所有註解)。
     *
     * @param enabled 是否開放註冊
     * @return true 若更新並儲存成功，false 若發生錯誤
     */
    @SuppressWarnings("unchecked")
    public synchronized boolean setEnableRegister(boolean enabled) {
        this.config.setRegistrationEnabled(enabled);

        Path configPath = configDir.resolve("config.yml");
        try {
            ensureDirectories();
            Yaml yaml = new Yaml();
            Map<String, Object> diskMap = new LinkedHashMap<>();
            if (Files.exists(configPath)) {
                try (InputStream in = new FileInputStream(configPath.toFile())) {
                    Map<String, Object> loaded = yaml.load(in);
                    if (loaded != null) {
                        diskMap = loaded;
                    }
                }
            }

            Map<String, Object> settingsMap = (Map<String, Object>) diskMap.computeIfAbsent("settings", k -> new LinkedHashMap<String, Object>());
            Map<String, Object> regMap = (Map<String, Object>) settingsMap.computeIfAbsent("registration", k -> new LinkedHashMap<String, Object>());
            regMap.put("enabled", enabled);

            String templateText = "";
            try (InputStream in = getClass().getResourceAsStream("/defaults/config.yml")) {
                if (in != null) {
                    templateText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            if (templateText.isEmpty() && Files.exists(configPath)) {
                templateText = Files.readString(configPath, StandardCharsets.UTF_8);
            }

            String mergedText = YamlCommentPreserver.mergePreservingComments(templateText, diskMap);
            Files.writeString(configPath, mergedText, StandardCharsets.UTF_8);
            LOGGER.info("NeoAuth: 已成功將 settings.registration.enabled 設定為 {} 並回寫至 {}", enabled, configPath);
            return true;
        } catch (Exception e) {
            LOGGER.error("NeoAuth: 回寫 config.yml 時發生錯誤！", e);
            return false;
        }
    }
}
