package tw.yuaner.neoauth.config;

import java.util.Map;

/**
 * 重生點與登入傳送點設定 (對應 config/neoauth/spawn.yml)。
 */
public class SpawnConfig {

    public static class Location {
        private String world = "minecraft:overworld";
        private double x = 0.0;
        private double y = 64.0;
        private double z = 0.0;
        private float yaw = 0.0f;
        private float pitch = 0.0f;
        private boolean enabled = false;

        public static Location fromMap(Map<?, ?> map) {
            Location loc = new Location();
            if (map == null) return loc;

            if (map.get("world") != null) loc.world = String.valueOf(map.get("world"));
            if (map.get("x") instanceof Number n) loc.x = n.doubleValue();
            if (map.get("y") instanceof Number n) loc.y = n.doubleValue();
            if (map.get("z") instanceof Number n) loc.z = n.doubleValue();
            if (map.get("yaw") instanceof Number n) loc.yaw = n.floatValue();
            if (map.get("pitch") instanceof Number n) loc.pitch = n.floatValue();
            if (map.get("enabled") instanceof Boolean b) loc.enabled = b;

            return loc;
        }

        public String getWorld() { return world; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public boolean isEnabled() { return enabled; }
    }

    private Location spawn = new Location();
    private Location firstSpawn = new Location();

    public static SpawnConfig fromMap(Map<String, Object> map) {
        SpawnConfig config = new SpawnConfig();
        if (map == null) return config;

        Object spawnObj = map.get("spawn");
        if (spawnObj instanceof Map<?, ?> sm) {
            config.spawn = Location.fromMap(sm);
        }

        Object firstSpawnObj = map.get("firstSpawn");
        if (firstSpawnObj instanceof Map<?, ?> fsm) {
            config.firstSpawn = Location.fromMap(fsm);
        }

        return config;
    }

    public Location getSpawn() {
        return spawn;
    }

    public Location getFirstSpawn() {
        return firstSpawn;
    }
}
