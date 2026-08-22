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
        public void setWorld(String world) { this.world = world; }
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public double getZ() { return z; }
        public void setZ(double z) { this.z = z; }
        public float getYaw() { return yaw; }
        public void setYaw(float yaw) { this.yaw = yaw; }
        public float getPitch() { return pitch; }
        public void setPitch(float pitch) { this.pitch = pitch; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("world", world);
            map.put("x", x);
            map.put("y", y);
            map.put("z", z);
            map.put("yaw", yaw);
            map.put("pitch", pitch);
            map.put("enabled", enabled);
            return map;
        }
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

    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("spawn", spawn.toMap());
        map.put("firstSpawn", firstSpawn.toMap());
        return map;
    }

    public Location getSpawn() {
        return spawn;
    }

    public Location getFirstSpawn() {
        return firstSpawn;
    }
}
