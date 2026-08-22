package tw.yuaner.neoauth;

import net.minecraft.server.level.ServerPlayer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {
    
    // Store UUIDs of logged-in players
    private static final Set<UUID> LOGGED_IN_PLAYERS = ConcurrentHashMap.newKeySet();

    // Store UUIDs of players verified through Mojang Online-Mode authentication
    private static final Set<UUID> PREMIUM_VERIFIED = ConcurrentHashMap.newKeySet();

    public static boolean isLoggedIn(ServerPlayer player) {
        return LOGGED_IN_PLAYERS.contains(player.getUUID());
    }

    public static void setLoggedIn(ServerPlayer player) {
        LOGGED_IN_PLAYERS.add(player.getUUID());
    }

    public static void setLoggedOut(ServerPlayer player) {
        LOGGED_IN_PLAYERS.remove(player.getUUID());
    }

    public static void markPremiumVerified(UUID uuid) {
        if (uuid != null) {
            PREMIUM_VERIFIED.add(uuid);
        }
    }

    public static boolean isPremiumVerified(UUID uuid) {
        return uuid != null && PREMIUM_VERIFIED.contains(uuid);
    }

    public static void clearPremiumVerified(UUID uuid) {
        if (uuid != null) {
            PREMIUM_VERIFIED.remove(uuid);
        }
    }

    public static boolean isOfflineUuid(String username, UUID uuid) {
        if (username == null || uuid == null) return false;
        UUID expectedOfflineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        return expectedOfflineUuid.equals(uuid);
    }
}
