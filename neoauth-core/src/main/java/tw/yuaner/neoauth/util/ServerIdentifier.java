package tw.yuaner.neoauth.util;

import java.net.InetAddress;
import java.nio.file.Paths;

/**
 * 伺服器識別名稱解析工具。
 * <p>
 * 支援從設定檔、環境變數、JVM 參數、或當前執行目錄與連接埠自動推導伺服器名稱。
 */
public class ServerIdentifier {

    /**
     * 解析當前伺服器的識別名稱。
     *
     * @param configuredName 設定檔中設定的 server_name (可能為 AUTO, 空白或自訂名稱)
     * @param port           伺服器監聽連接埠 (若無法取得可傳入 0 或負數)
     * @return 最終解析出的伺服器識別名稱
     */
    public static String resolve(String configuredName, int port) {
        String dirName = getDirectoryName();
        String hostName = getHostName();
        String portStr = port > 0 ? String.valueOf(port) : "";

        // 1. 若為 null、空白或設定為 AUTO (不區分大小寫)，啟用智慧自動推導
        if (configuredName == null || configuredName.isBlank() || "AUTO".equalsIgnoreCase(configuredName.trim())) {
            // A. 優先檢查 JVM 參數 (-Dneoauth.server.name=xxx)
            String sysProp = System.getProperty("neoauth.server.name");
            if (sysProp != null && !sysProp.isBlank()) {
                return sysProp.trim();
            }

            // B. 檢查環境變數 (適合 Docker / Pterodactyl 容器化部署)
            String envName = System.getenv("NEOAUTH_SERVER_NAME");
            if (envName == null || envName.isBlank()) {
                envName = System.getenv("SERVER_NAME");
            }
            if (envName != null && !envName.isBlank()) {
                return envName.trim();
            }

            // C. 依據目錄名與連接埠自動組合 (例如: "lobby:25565" 或 "survival:25566")
            if (!portStr.isEmpty()) {
                return dirName + ":" + portStr;
            }
            return dirName;
        }

        // 2. 若設定檔中包含動態佔位符，則進行替換
        String result = configuredName;
        if (result.contains("{dir}")) {
            result = result.replace("{dir}", dirName);
        }
        if (result.contains("{port}")) {
            result = result.replace("{port}", portStr);
        }
        if (result.contains("{host}")) {
            result = result.replace("{host}", hostName);
        }

        return result;
    }

    private static String getDirectoryName() {
        try {
            // 1. 若處於 PrismLauncher / MultiMC 等啟動器環境，優先讀取 INST_NAME 環境變數
            String instName = System.getenv("INST_NAME");
            if (instName != null && !instName.isBlank()) {
                return instName.trim();
            }

            java.nio.file.Path current = Paths.get("").toAbsolutePath();
            String dirName = current.getFileName() != null ? current.getFileName().toString() : "minecraft";

            // 2. 若當前資料夾名稱為通用名稱 (如 "minecraft" 或 ".minecraft")，嘗試取上一層目錄名稱 (例如 PrismLauncher 實例名稱)
            if ("minecraft".equalsIgnoreCase(dirName) || ".minecraft".equalsIgnoreCase(dirName)) {
                java.nio.file.Path parent = current.getParent();
                if (parent != null && parent.getFileName() != null) {
                    String parentName = parent.getFileName().toString();
                    if (!parentName.isBlank() && !"instances".equalsIgnoreCase(parentName)) {
                        return parentName;
                    }
                }
            }

            return dirName;
        } catch (Exception e) {
            return "minecraft";
        }
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
