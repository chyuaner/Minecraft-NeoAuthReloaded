package tw.yuaner.neoauth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.yuaner.neoauth.config.NeoAuthConfig;
import tw.yuaner.neoauth.util.BlueMapIntegration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BlueMapIntegrationTest {

    @Test
    public void testFormatAvatarUrl() {
        UUID uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        String username = "Notch";

        String officialTemplate = "https://mc-heads.net/avatar/{username}/{size}";
        String formattedOfficial = BlueMapIntegration.formatAvatarUrl(officialTemplate, username, uuid);
        assertEquals("https://mc-heads.net/avatar/Notch/64", formattedOfficial);

        String customTemplate = "https://mc8.yuaner.tw/avatar/player/{username}?size={size}&uuid={uuid}";
        String formattedCustom = BlueMapIntegration.formatAvatarUrl(customTemplate, username, uuid);
        assertEquals("https://mc8.yuaner.tw/avatar/player/Notch?size=64&uuid=069a79f4-44e9-4726-a5be-fca90e38aaf5", formattedCustom);
    }

    @Test
    public void testDeriveAvatarTemplateFromBaseUrl() {
        assertEquals("https://mc8.yuaner.tw/avatar/player/{username}?size=64",
                BlueMapIntegration.deriveAvatarTemplateFromBaseUrl("https://mc8.yuaner.tw/api/yggdrasil"));

        assertEquals("https://mc8.yuaner.tw/avatar/player/{username}?size=64",
                BlueMapIntegration.deriveAvatarTemplateFromBaseUrl("https://mc8.yuaner.tw/api/yggdrasil/"));

        assertEquals("https://skin.example.com/avatar/player/{username}?size=64",
                BlueMapIntegration.deriveAvatarTemplateFromBaseUrl("https://skin.example.com"));
    }

    @Test
    public void testIsValidPngOrImage() {
        // Valid PNG header (89 50 4E 47 0D 0A 1A 0A)
        byte[] validPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertTrue(BlueMapIntegration.isValidPngOrImage(validPng));

        // Invalid / empty
        assertFalse(BlueMapIntegration.isValidPngOrImage(null));
        assertFalse(BlueMapIntegration.isValidPngOrImage(new byte[]{0, 1, 2}));

        // General image buffer > 32 bytes
        byte[] genericImage = new byte[40];
        assertTrue(BlueMapIntegration.isValidPngOrImage(genericImage));
    }

    @Test
    public void testHasBlueMapDirectory(@TempDir Path tempDir) throws IOException {
        assertFalse(BlueMapIntegration.hasBlueMapDirectory(tempDir));

        Path bluemapDir = tempDir.resolve("bluemap");
        Files.createDirectories(bluemapDir);
        assertTrue(BlueMapIntegration.hasBlueMapDirectory(tempDir));
    }

    @Test
    public void testWriteHeadToAllMaps(@TempDir Path tempDir) throws IOException {
        // 模擬 BlueMap 目錄架構
        Path webMaps = tempDir.resolve("bluemap/web/maps");
        Path worldMap = webMaps.resolve("world");
        Path netherMap = webMaps.resolve("world_nether");
        Path endMap = webMaps.resolve("world_the_end");
        Files.createDirectories(worldMap);
        Files.createDirectories(netherMap);
        Files.createDirectories(endMap);

        Path webAssets = tempDir.resolve("bluemap/web/assets");
        Files.createDirectories(webAssets);

        UUID offlineUuid = UUID.randomUUID();
        byte[] mockPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

        int count = BlueMapIntegration.writeHeadToAllMaps(tempDir, offlineUuid, mockPng);
        assertEquals(4, count, "Should write to 3 map folders and 1 global asset folder");

        Path worldHead = worldMap.resolve("assets/playerheads/" + offlineUuid + ".png");
        assertTrue(Files.exists(worldHead));
        assertArrayEquals(mockPng, Files.readAllBytes(worldHead));

        Path netherHead = netherMap.resolve("assets/playerheads/" + offlineUuid + ".png");
        assertTrue(Files.exists(netherHead));
        assertArrayEquals(mockPng, Files.readAllBytes(netherHead));

        Path globalHead = tempDir.resolve("bluemap/web/assets/playerheads/" + offlineUuid + ".png");
        assertTrue(Files.exists(globalHead));
        assertArrayEquals(mockPng, Files.readAllBytes(globalHead));
    }

    @Test
    public void testSkinRestorerJoinAutoFetchProvidersParsing(@TempDir Path tempDir) throws IOException {
        Path srDir = tempDir.resolve("config/skinrestorer");
        Files.createDirectories(srDir);

        // 與線上測試伺服器 config/skinrestorer/config.json 100% 相同結構
        String json = """
                {
                  "language": "zh_tw",
                  "join": {
                    "refreshSkin": true,
                    "autoFetch": {
                      "enabled": true,
                      "overrideExisting": false,
                      "providers": [
                        "mojang",
                        "myblessingskin"
                      ]
                    }
                  },
                  "providers": {
                    "mojang": {
                      "enabled": true,
                      "name": "mojang"
                    },
                    "ely_by": {
                      "enabled": true,
                      "name": "ely.by"
                    },
                    "custom": [
                      {
                        "baseUrl": "https://mc8.yuaner.tw/api/yggdrasil",
                        "type": "authlib-injector",
                        "useProviderSignature": false,
                        "enabled": true,
                        "name": "myblessingskin",
                        "cache": {
                          "enabled": true,
                          "duration": 300
                        }
                      }
                    ]
                  },
                  "version": 3
                }
                """;
        Files.writeString(srDir.resolve("config.json"), json);

        BlueMapIntegration.SkinRestorerSettings settings = BlueMapIntegration.parseSkinRestorerConfig(tempDir);
        assertNotNull(settings);
        assertTrue(settings.hasCustomSources());
        assertEquals("OFFICIAL_FIRST", settings.priority());
        assertEquals("https://mc8.yuaner.tw/avatar/player/{username}?size=64", settings.customProviders().get("myblessingskin"));

        // 驗證候選網址順序：官方在前、myblessingskin 在後
        List<String> candidates = settings.buildCandidateUrls("https://mc-heads.net/avatar/{username}/64");
        assertEquals(2, candidates.size());
        assertEquals("https://mc-heads.net/avatar/{username}/64", candidates.get(0));
        assertEquals("https://mc8.yuaner.tw/avatar/player/{username}?size=64", candidates.get(1));
    }

    @Test
    public void testSkinRestorerCustomFirstJoinAutoFetch(@TempDir Path tempDir) throws IOException {
        Path srDir = tempDir.resolve("config/skinsrestorer");
        Files.createDirectories(srDir);

        String json = """
                {
                  "join": {
                    "autoFetch": {
                      "enabled": true,
                      "providers": [
                        "myblessingskin",
                        "mojang"
                      ]
                    }
                  },
                  "custom": [
                    {
                      "baseUrl": "https://mc8.yuaner.tw/api/yggdrasil",
                      "type": "authlib-injector",
                      "enabled": true,
                      "name": "myblessingskin"
                    }
                  ]
                }
                """;
        Files.writeString(srDir.resolve("config.json"), json);

        BlueMapIntegration.SkinRestorerSettings settings = BlueMapIntegration.parseSkinRestorerConfig(tempDir);
        assertNotNull(settings);
        assertEquals("CUSTOM_FIRST", settings.priority());

        // 驗證候選網址順序：myblessingskin 在前、官方在後
        List<String> candidates = settings.buildCandidateUrls("https://mc-heads.net/avatar/{username}/64");
        assertEquals(2, candidates.size());
        assertEquals("https://mc8.yuaner.tw/avatar/player/{username}?size=64", candidates.get(0));
        assertEquals("https://mc-heads.net/avatar/{username}/64", candidates.get(1));
    }

    @Test
    public void testNeoAuthConfigBlueMapParsing() {
        Map<String, Object> map = Map.of(
                "bluemap", Map.of(
                        "enabled", true,
                        "useSkinRestorerConfig", true,
                        "priority", "OFFICIAL_FIRST",
                        "avatarUrl", "https://custom-avatar.com/head/{username}",
                        "customAvatarUrl", "https://mc8.yuaner.tw/avatar/player/{username}?size=64"
                )
        );

        NeoAuthConfig config = NeoAuthConfig.fromMap(map);
        assertTrue(config.isBlueMapIntegrationEnabled());
        assertTrue(config.isUseSkinRestorerConfig());
        assertEquals("OFFICIAL_FIRST", config.getBlueMapPriority());
        assertEquals("https://custom-avatar.com/head/{username}", config.getBlueMapAvatarUrl());
        assertEquals("https://mc8.yuaner.tw/avatar/player/{username}?size=64", config.getBlueMapCustomAvatarUrl());
    }

    @Test
    public void testNeoAuthConfigBlueMapCompatibilityKeys() {
        Map<String, Object> map = Map.of(
                "bluemap", Map.of(
                        "enable-bluemap-integration", false,
                        "use-skinrestorer", false,
                        "source-priority", "CUSTOM_FIRST",
                        "avatar-url", "https://custom-avatar.com/{username}",
                        "custom-avatar-url", "https://skin.yuaner.tw/{username}"
                )
        );

        NeoAuthConfig config = NeoAuthConfig.fromMap(map);
        assertFalse(config.isBlueMapIntegrationEnabled());
        assertFalse(config.isUseSkinRestorerConfig());
        assertEquals("CUSTOM_FIRST", config.getBlueMapPriority());
        assertEquals("https://custom-avatar.com/{username}", config.getBlueMapAvatarUrl());
        assertEquals("https://skin.yuaner.tw/{username}", config.getBlueMapCustomAvatarUrl());
    }

    @Test
    public void testCachedAvatarTextureHashAndTtl() {
        byte[] sampleData = new byte[]{1, 2, 3, 4};
        String hash1 = BlueMapIntegration.computeTextureHash("sample_texture_base64_v1");
        String hash2 = BlueMapIntegration.computeTextureHash("sample_texture_base64_v2");
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotEquals(hash1, hash2);

        // 建立快取 (時間為當前時間)
        BlueMapIntegration.CachedAvatar avatar = new BlueMapIntegration.CachedAvatar(sampleData, hash1, System.currentTimeMillis());
        assertArrayEquals(sampleData, avatar.getData());
        assertEquals(hash1, avatar.getTextureHash());

        // 1. 同樣的 Texture Hash 且未過期 -> 有效
        assertTrue(avatar.isValid(hash1, 120));

        // 2. 玩家更換了皮膚 (Texture Hash 改變) -> 立即無效！
        assertFalse(avatar.isValid(hash2, 120));

        // 3. 測試 TTL 逾時
        long oldTime = System.currentTimeMillis() - (121 * 60 * 1000L); // 121 分鐘前
        BlueMapIntegration.CachedAvatar expiredAvatar = new BlueMapIntegration.CachedAvatar(sampleData, hash1, oldTime);
        assertFalse(expiredAvatar.isValid(hash1, 120)); // 超過 120 分鐘 -> 失效
        assertTrue(expiredAvatar.isValid(hash1, 0));    // 0 為不檢查時間 -> 有效
    }

    @Test
    public void testBlueMapIntegrationClearCache() {
        BlueMapIntegration.clearCache();
        assertNull(BlueMapIntegration.getCachedAvatar("Steve"));

        // 手動模擬寫入快取
        byte[] data = new byte[]{10, 20, 30};
        String hash = BlueMapIntegration.computeTextureHash("texture_data");
        BlueMapIntegration.CachedAvatar cached = new BlueMapIntegration.CachedAvatar(data, hash, System.currentTimeMillis());

        // 驗證清空特定玩家
        assertFalse(BlueMapIntegration.clearCacheForPlayer("Steve"));

        // 驗證全域清空
        BlueMapIntegration.clearCache();
        assertNull(BlueMapIntegration.getCachedAvatar("Steve"));
    }
}

