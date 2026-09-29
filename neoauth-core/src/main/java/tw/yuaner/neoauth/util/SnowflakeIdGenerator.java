package tw.yuaner.neoauth.util;

/**
 * 64-bit Snowflake 唯一識別碼生成器。
 * <p>
 * 用於登入日誌 (login_logs) 之主鍵 ID 生成，確保在多伺服器架構與主庫斷線 (Fallback SQLite) 容災情境下，
 * 客戶端自主生成的 ID 具備全局唯一性與時間先後順序性，徹底避免 MySQL 與本地 SQLite 主鍵碰撞。
 * <p>
 * 結構配置 (共 64 位元，正數 signed long)：
 * <ul>
 *   <li>1 bit: 符號位 (固定為 0，確保 ID 為正數)</li>
 *   <li>41 bits: 毫秒時間戳 (相對於自訂紀元 2026-01-01，可持續運作約 69 年至 2095 年)</li>
 *   <li>10 bits: 伺服器節點識別碼 (0 ~ 1023，依據伺服器識別名稱自動雜湊分配)</li>
 *   <li>12 bits: 毫秒內遞增序列號 (0 ~ 4095，支援每毫秒單節點生成 4096 筆 ID)</li>
 * </ul>
 */
public class SnowflakeIdGenerator {

    /**
     * 自訂紀元基準點: 2026-01-01 00:00:00 UTC (1767225600000L)
     */
    private static final long EPOCH = 1767225600000L;

    private static final long NODE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_NODE_ID = ~(-1L << NODE_ID_BITS);       // 1023
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);     // 4095

    private static final long NODE_ID_SHIFT = SEQUENCE_BITS;              // 12
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + NODE_ID_BITS; // 22

    private final long nodeId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    private static class InstanceHolder {
        private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator(resolveDefaultNodeId());
    }

    public static SnowflakeIdGenerator getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public SnowflakeIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            this.nodeId = Math.abs(nodeId) % (MAX_NODE_ID + 1);
        } else {
            this.nodeId = nodeId;
        }
    }

    /**
     * 自動由當前伺服器標識或工作目錄推導 10-bit Node ID。
     */
    public static long resolveDefaultNodeId() {
        try {
            String serverName = ServerIdentifier.resolve("AUTO", 0);
            if (serverName != null && !serverName.isBlank()) {
                return (long) Math.abs(serverName.hashCode()) % (MAX_NODE_ID + 1);
            }
        } catch (Throwable ignored) {
        }
        return (long) (Math.random() * (MAX_NODE_ID + 1));
    }

    /**
     * 生成下一個 64-bit 唯一 ID。
     *
     * @return 64 位元長整數唯一 ID
     */
    public synchronized long nextId() {
        long currentTimestamp = timeGen();

        // 處理微小時鐘回撥 (5ms 內短暫等待)
        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                try {
                    Thread.sleep(offset << 1);
                    currentTimestamp = timeGen();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (currentTimestamp < lastTimestamp) {
                // 若仍小於上次時間戳，強制追平上次時間戳以避免 ID 重複
                currentTimestamp = lastTimestamp;
            }
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }
}
