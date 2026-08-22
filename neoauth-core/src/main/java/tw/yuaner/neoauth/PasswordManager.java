package tw.yuaner.neoauth;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 密碼加密與雜湊比對管理員。
 * <p>
 * 完全相容於主流 Bukkit / Paper 登入外掛 <b>AuthMeReloaded</b> 的密碼格式：
 * <ul>
 *   <li><b>預設格式</b>：AuthMe 標準加鹽雙重 SHA-256 (格式為: {@code $SHA$salt$hash})</li>
 *   <li><b>BCrypt</b>：支援 {@code $2a$}, {@code $2b$}, {@code $2y$} 前綴的強效雜湊</li>
 *   <li><b>歷史格式相容</b>：支援舊版無鹽 MD5 / SHA-256 比對</li>
 * </ul>
 */
public class PasswordManager {

    /**
     * 安全隨機數產生器，用於生成隨機鹽值 (Salt)。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 將純文字密碼加密為 AuthMe 預設的加鹽 SHA-256 格式。
     *
     * @param password 玩家輸入的純文字密碼
     * @return 格式為 {@code $SHA$salt$hash} 的加密字串
     */
    public static String hashPassword(String password) {
        String salt = generateSalt(16);
        return computeSha256(password, salt);
    }

    /**
     * 驗證玩家輸入的純文字密碼是否與資料庫中的雜湊值相符。
     *
     * @param password 玩家輸入的純文字密碼
     * @param hash     資料庫中儲存的加密密碼字串
     * @return true 若密碼正確，否則為 false
     */
    public static boolean checkPassword(String password, String hash) {
        if (hash == null || password == null) return false;

        // 1. AuthMe 標準 $SHA$salt$hash 格式
        if (hash.startsWith("$SHA$")) {
            String[] parts = hash.split("\\$");
            if (parts.length == 4) {
                String salt = parts[2];
                String computed = computeSha256(password, salt);
                return computed.equals(hash);
            }
        }
        // 2. BCrypt 格式
        else if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
            return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
        }
        // 3. 舊版無鹽 MD5 或 SHA-256 格式
        else {
            String md5 = md5Hex(password);
            String sha256 = sha256Hex(password);
            return hash.equalsIgnoreCase(md5) || hash.equalsIgnoreCase(sha256);
        }

        return false;
    }

    /**
     * 計算 AuthMe 標準的雙重加鹽 SHA-256 雜湊。
     * <p>
     * 演算法步驟：{@code SHA256(SHA256(password) + salt)}。
     *
     * @param password 純文字密碼
     * @param salt     隨機鹽值
     * @return 組合後的雜湊格式字串
     */
    private static String computeSha256(String password, String salt) {
        String hash1 = sha256Hex(password);
        String hash2 = sha256Hex(hash1 + salt);
        return "$SHA$" + salt + "$" + hash2;
    }

    /**
     * 計算資料的單次 SHA-256 16進位字串。
     *
     * @param data 輸入字串
     * @return 64位長度之 16 進位字串
     */
    private static String sha256Hex(String data) {
        return hashHex("SHA-256", data);
    }

    /**
     * 計算資料的單次 MD5 16進位字串。
     *
     * @param data 輸入字串
     * @return 32位長度之 16 進位字串
     */
    private static String md5Hex(String data) {
        return hashHex("MD5", data);
    }

    /**
     * 通用訊息摘要演算法計算方法。
     *
     * @param algorithm 演算法名稱 (例如: "SHA-256", "MD5")
     * @param data      輸入字串
     * @return 16 進位雜湊字串
     */
    private static String hashHex(String algorithm, String data) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("系統不支援此加密演算法: " + algorithm, e);
        }
    }

    /**
     * 生成指定長度的十六進位隨機鹽值字串。
     *
     * @param length 鹽值字串長度
     * @return 隨機十六進位字串
     */
    private static String generateSalt(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }
}
