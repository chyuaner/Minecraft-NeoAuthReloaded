package tw.yuaner.neoauth.forge;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import tw.yuaner.neoauth.config.IAuthConfig;

/**
 * Minecraft 1.20.1 Forge 平台的設定檔實作。
 * <p>
 * 使用 Forge 的 {@link ForgeConfigSpec} 來定義伺服器設定項目，
 * 並實作 {@link IAuthConfig} 供共用核心邏輯呼叫。
 */
public class ForgeConfig implements IAuthConfig {

    public static final ForgeConfig INSTANCE;
    public static final ForgeConfigSpec SERVER_SPEC;

    private final ForgeConfigSpec.ConfigValue<String> dbHost;
    private final ForgeConfigSpec.ConfigValue<String> dbPort;
    private final ForgeConfigSpec.ConfigValue<String> dbName;
    private final ForgeConfigSpec.ConfigValue<String> dbUsername;
    private final ForgeConfigSpec.ConfigValue<String> dbPassword;
    private final ForgeConfigSpec.ConfigValue<String> dbTable;
    private final ForgeConfigSpec.ConfigValue<Boolean> allowOfflinePlayers;

    static {
        final Pair<ForgeConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ForgeConfig::new);
        SERVER_SPEC = specPair.getRight();
        INSTANCE = specPair.getLeft();
    }

    private ForgeConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("NeoAuth 伺服器驗證設定檔 (Forge 1.20.1)");

        builder.push("Database");
        dbHost = builder
                .comment("MariaDB 資料庫主機位址 (預設: 127.0.0.1)")
                .define("host", "127.0.0.1");
        dbPort = builder
                .comment("MariaDB 資料庫連接埠 (預設: 3306)")
                .define("port", "3306");
        dbName = builder
                .comment("MariaDB 資料庫名稱 (預設: neoauth)")
                .define("database", "neoauth");
        dbUsername = builder
                .comment("MariaDB 資料庫使用者名稱 (預設: root)")
                .define("username", "root");
        dbPassword = builder
                .comment("MariaDB 資料庫密碼")
                .define("password", "");
        dbTable = builder
                .comment("NeoAuth 資料表名稱 (預設: neoauth)")
                .define("table", "neoauth");
        builder.pop();

        builder.push("Authentication");
        allowOfflinePlayers = builder
                .comment("是否允許離線（盜版）玩家在伺服器 online-mode=true 時進入伺服器")
                .define("allowOfflinePlayers", true);
        builder.pop();
    }

    @Override
    public String getDbHost() {
        return dbHost.get();
    }

    @Override
    public String getDbPort() {
        return dbPort.get();
    }

    @Override
    public String getDbName() {
        return dbName.get();
    }

    @Override
    public String getDbUsername() {
        return dbUsername.get();
    }

    @Override
    public String getDbPassword() {
        return dbPassword.get();
    }

    @Override
    public String getDbTable() {
        return dbTable.get();
    }

    @Override
    public boolean isAllowOfflinePlayers() {
        return allowOfflinePlayers.get();
    }
}
