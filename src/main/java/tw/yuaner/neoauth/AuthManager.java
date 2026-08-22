package tw.yuaner.neoauth;

import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {
    
    // Store UUIDs of logged-in players
    private static final Set<java.util.UUID> LOGGED_IN_PLAYERS = ConcurrentHashMap.newKeySet();

    public static boolean isLoggedIn(ServerPlayer player) {
        return LOGGED_IN_PLAYERS.contains(player.getUUID());
    }

    public static void setLoggedIn(ServerPlayer player) {
        LOGGED_IN_PLAYERS.add(player.getUUID());
    }

    public static void setLoggedOut(ServerPlayer player) {
        LOGGED_IN_PLAYERS.remove(player.getUUID());
    }
}
