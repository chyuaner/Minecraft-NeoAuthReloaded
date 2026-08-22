package tw.yuaner.neoauth.platform;

import java.util.ServiceLoader;

/**
 * 服務載入器工具類別。
 * <p>
 * 利用 Java 標準 SPI (Service Provider Interface) 機制，
 * 在執行時期自動尋找並載入對應平台 (Forge 或 NeoForge) 的 {@link IPlatformHelper} 實作。
 */
public class Services {

    /**
     * 當前平台的服務實例。
     */
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    /**
     * 載入指定介面的第一個可用服務實作。
     *
     * @param <T>   服務介面型別
     * @param clazz 服務介面類別
     * @return 服務實作實例
     * @throws IllegalStateException 若找不到任何實作時拋出
     */
    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("找不到 " + clazz.getName() + " 的平台服務實作！請確認 META-INF/services 設定。"));
        return loadedService;
    }
}
