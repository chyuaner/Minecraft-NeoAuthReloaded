package tw.yuaner.neoauth;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {

    public static final ServerConfig SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    static {
        final Pair<ServerConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public static class ServerConfig {
        public final ModConfigSpec.ConfigValue<String> dbHost;
        public final ModConfigSpec.ConfigValue<String> dbPort;
        public final ModConfigSpec.ConfigValue<String> dbName;
        public final ModConfigSpec.ConfigValue<String> dbUsername;
        public final ModConfigSpec.ConfigValue<String> dbPassword;
        public final ModConfigSpec.ConfigValue<String> dbTable;

        public ServerConfig(ModConfigSpec.Builder builder) {
            builder.push("Database");
            dbHost = builder
                    .comment("MariaDB Host")
                    .define("host", "127.0.0.1");
            dbPort = builder
                    .comment("MariaDB Port")
                    .define("port", "3306");
            dbName = builder
                    .comment("Database Name")
                    .define("database", "authme");
            dbUsername = builder
                    .comment("Database Username")
                    .define("username", "root");
            dbPassword = builder
                    .comment("Database Password")
                    .define("password", "");
            dbTable = builder
                    .comment("Table Name")
                    .define("table", "authme");
            builder.pop();

            builder.push("Authentication");
            allowOfflinePlayers = builder
                    .comment("Allow offline-mode / cracked players to join even when server online-mode is true")
                    .define("allowOfflinePlayers", true);
            builder.pop();
        }

        public final ModConfigSpec.ConfigValue<Boolean> allowOfflinePlayers;
    }
}
