package tw.yuaner.neoauth.config;

import java.util.Map;

/**
 * 登入日誌設定模型 (login_logs.yml)。
 * <p>
 * 支援啟用開關、伺服器名稱自動辨識、以及自訂資料表與欄位名稱對應 (比照 AuthMe 規範)。
 */
public class LoginLogsConfig {
    private boolean enabled = false;
    private String serverName = "AUTO";

    // 自訂資料表與欄位名稱
    private String tableName = "login_logs";
    private String columnId = "id";
    private String columnName = "username";
    private String columnUuid = "uuid";
    private String columnLoginTime = "login_time";
    private String columnLogoutTime = "logout_time";
    private String columnIp = "ip";
    private String columnServerHost = "server_host";
    private String columnServerIp = "server_ip";
    private String columnServerPort = "server_port";
    private String columnLoginMethod = "login_method";
    private String columnConnectionChannel = "connection_channel";
    private String columnServerName = "server_name";
    private String columnLoginOpLevel = "login_op_level";
    private String columnLogoutOpLevel = "logout_op_level";

    public static LoginLogsConfig fromMap(Map<String, Object> map) {
        LoginLogsConfig config = new LoginLogsConfig();
        if (map == null) {
            return config;
        }

        Object rootObj = map.containsKey("login_logs") ? map.get("login_logs") : map;
        if (rootObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) rootObj;

            if (root.containsKey("enabled")) {
                Object val = root.get("enabled");
                if (val instanceof Boolean) {
                    config.enabled = (Boolean) val;
                }
            }
            if (root.containsKey("server_name")) {
                Object val = root.get("server_name");
                if (val != null) {
                    config.serverName = String.valueOf(val);
                }
            }

            // 資料表名稱
            config.tableName = getString(root, config.tableName, "mySQLTablename", "tableName", "table");

            // 支援 columns 子字典
            Map<String, Object> colMap = null;
            if (root.get("columns") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) root.get("columns");
                colMap = casted;
            }

            // 各自訂欄位解析
            config.columnId = getColumn(root, colMap, config.columnId, "mySQLColumnId", "id");
            config.columnName = getColumn(root, colMap, config.columnName, "mySQLColumnName", "username", "name");
            config.columnUuid = getColumn(root, colMap, config.columnUuid, "mySQLColumnUuid", "mySQLPlayerUUID", "uuid");
            config.columnLoginTime = getColumn(root, colMap, config.columnLoginTime, "mySQLColumnLoginTime", "login_time", "loginTime");
            config.columnLogoutTime = getColumn(root, colMap, config.columnLogoutTime, "mySQLColumnLogoutTime", "logout_time", "logoutTime");
            config.columnIp = getColumn(root, colMap, config.columnIp, "mySQLColumnIp", "ip");
            config.columnServerHost = getColumn(root, colMap, config.columnServerHost, "mySQLColumnServerHost", "server_host", "serverHost", "host");
            config.columnServerIp = getColumn(root, colMap, config.columnServerIp, "mySQLColumnServerIp", "server_ip", "serverIp");
            config.columnServerPort = getColumn(root, colMap, config.columnServerPort, "mySQLColumnServerPort", "server_port", "serverPort", "port");
            config.columnLoginMethod = getColumn(root, colMap, config.columnLoginMethod, "mySQLColumnLoginMethod", "login_method", "loginMethod");
            config.columnConnectionChannel = getColumn(root, colMap, config.columnConnectionChannel, "mySQLColumnConnectionChannel", "connection_channel", "connectionChannel");
            // server_name 欄位在 root 中使用 mySQLColumnServerName，避免與 root 的 server_name (伺服器標識) 設定值衝突
            String srvNameCol = getString(root, null, "mySQLColumnServerName");
            if (srvNameCol == null && colMap != null) {
                srvNameCol = getString(colMap, null, "mySQLColumnServerName", "server_name", "serverName");
            }
            if (srvNameCol != null) {
                config.columnServerName = srvNameCol;
            }
            config.columnLoginOpLevel = getColumn(root, colMap, config.columnLoginOpLevel, "mySQLColumnLoginOpLevel", "login_op_level", "loginOpLevel");
            config.columnLogoutOpLevel = getColumn(root, colMap, config.columnLogoutOpLevel, "mySQLColumnLogoutOpLevel", "logout_op_level", "logoutOpLevel");
        }

        return config;
    }

    private static String getColumn(Map<String, Object> root, Map<String, Object> colMap, String def, String... keys) {
        String val = getString(root, null, keys);
        if (val != null) return val;
        if (colMap != null) {
            val = getString(colMap, null, keys);
            if (val != null) return val;
        }
        return def;
    }

    private static String getString(Map<String, Object> map, String def, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k)) {
                Object v = map.get(k);
                if (v != null && !String.valueOf(v).isBlank()) {
                    return String.valueOf(v).trim();
                }
            }
        }
        return def;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getColumnId() {
        return columnId;
    }

    public void setColumnId(String columnId) {
        this.columnId = columnId;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnUuid() {
        return columnUuid;
    }

    public void setColumnUuid(String columnUuid) {
        this.columnUuid = columnUuid;
    }

    public String getColumnLoginTime() {
        return columnLoginTime;
    }

    public void setColumnLoginTime(String columnLoginTime) {
        this.columnLoginTime = columnLoginTime;
    }

    public String getColumnLogoutTime() {
        return columnLogoutTime;
    }

    public void setColumnLogoutTime(String columnLogoutTime) {
        this.columnLogoutTime = columnLogoutTime;
    }

    public String getColumnIp() {
        return columnIp;
    }

    public void setColumnIp(String columnIp) {
        this.columnIp = columnIp;
    }

    public String getColumnServerHost() {
        return columnServerHost;
    }

    public void setColumnServerHost(String columnServerHost) {
        this.columnServerHost = columnServerHost;
    }

    public String getColumnServerIp() {
        return columnServerIp;
    }

    public void setColumnServerIp(String columnServerIp) {
        this.columnServerIp = columnServerIp;
    }

    public String getColumnServerPort() {
        return columnServerPort;
    }

    public void setColumnServerPort(String columnServerPort) {
        this.columnServerPort = columnServerPort;
    }

    public String getColumnLoginMethod() {
        return columnLoginMethod;
    }

    public void setColumnLoginMethod(String columnLoginMethod) {
        this.columnLoginMethod = columnLoginMethod;
    }

    public String getColumnConnectionChannel() {
        return columnConnectionChannel;
    }

    public void setColumnConnectionChannel(String columnConnectionChannel) {
        this.columnConnectionChannel = columnConnectionChannel;
    }

    public String getColumnServerName() {
        return columnServerName;
    }

    public void setColumnServerName(String columnServerName) {
        this.columnServerName = columnServerName;
    }

    public String getColumnLoginOpLevel() {
        return columnLoginOpLevel;
    }

    public void setColumnLoginOpLevel(String columnLoginOpLevel) {
        this.columnLoginOpLevel = columnLoginOpLevel;
    }

    public String getColumnLogoutOpLevel() {
        return columnLogoutOpLevel;
    }

    public void setColumnLogoutOpLevel(String columnLogoutOpLevel) {
        this.columnLogoutOpLevel = columnLogoutOpLevel;
    }
}
