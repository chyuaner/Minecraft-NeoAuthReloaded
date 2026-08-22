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
 * 完全相容於主流 Bukkit / Paper 登入外掛 <b>AuthMeReloaded</b> 與外部社群論壇/皮膚站的密碼格式：
 * <ul>
 *   <li><b>SHA256 (預設)</b>：AuthMe 標準加鹽雙重 SHA-256 (格式為: {@code $SHA$salt$hash})</li>
 *   <li><b>SALTED2MD5</b>：相容 Discuz!、Phpwind 與 Blessing Skin 等系統，格式為 {@code MD5(MD5(password) + salt)}</li>
 *   <li><b>SALTEDSHA512</b>：AuthMe 標準加鹽雙重 SHA-512 (格式為: {@code $SHA$salt$hash})</li>
 *   <li><b>BCrypt</b>：相容 Blessing Skin、Flarum 等系統，支援 {@code $2a$}, {@code $2b$}, {@code $2y$} 前綴</li>
 *   <li><b>歷史格式相容</b>：支援舊版無鹽 MD5 / SHA-256 / SHA-512 比對</li>
 * </ul>
 */
public class PasswordManager {

    /**
     * 安全隨機數產生器，用於生成隨機鹽值 (Salt)。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 根據全域設定檔中的雜湊演算法加密密碼。
     *
     * @param password 玩家輸入的純文字密碼
     * @return 加密字串
     */
    public static String hashPassword(String password) {
        String method = "SHA256";
        int saltLen = 6;
        try {
            tw.yuaner.neoauth.config.IAuthConfig config = tw.yuaner.neoauth.config.ConfigManager.getInstance().getConfig();
            if (config != null) {
                if (config.getPasswordHash() != null && !config.getPasswordHash().isBlank()) {
                    method = config.getPasswordHash().toUpperCase();
                }
                saltLen = config.getDoubleMD5SaltLength();
            }
        } catch (Throwable ignored) {}

        if ("BCRYPT".equals(method)) {
            return BCrypt.withDefaults().hashToString(10, password.toCharArray());
        }

        if ("SALTED2MD5".equals(method)) {
            String salt = generateRandomSalt(saltLen > 0 ? saltLen : 6);
            return "$MD5$" + salt + "$" + computeSalted2Md5(password, salt);
        }

        if ("SALTEDSHA512".equals(method) || "SHA512".equals(method)) {
            String salt = generateSalt(16);
            return computeSha512(password, salt);
        }

        // 預設 SHA256 (AuthMe 標準加鹽雙重 SHA-256)
        String salt = generateSalt(16);
        return computeSha256(password, salt);
    }

    /**
     * 使用指定鹽值與指定演算法計算雜湊字串 (例如配合外部資料表 salt 欄位時使用)。
     *
     * @param password  純文字密碼
     * @param salt      鹽值
     * @param algorithm 演算法 (SALTED2MD5, SALTEDSHA512, SHA256, BCRYPT)
     * @return 雜湊結果
     */
    public static String hashPasswordWithSalt(String password, String salt, String algorithm) {
        if ("SALTED2MD5".equalsIgnoreCase(algorithm)) {
            return computeSalted2Md5(password, salt);
        }
        if ("SALTEDSHA512".equalsIgnoreCase(algorithm) || "SHA512".equalsIgnoreCase(algorithm)) {
            return sha512Hex(sha512Hex(password) + salt);
        }
        if ("SHA256".equalsIgnoreCase(algorithm)) {
            return sha256Hex(sha256Hex(password) + salt);
        }
        return hashPassword(password);
    }

    /**
     * 驗證玩家輸入的純文字密碼是否與資料庫中的雜湊值相符 (無獨立 salt 欄位)。
     *
     * @param password 玩家輸入的純文字密碼
     * @param hash     資料庫中儲存的加密密碼字串
     * @return true 若密碼正確，否則為 false
     */
    public static boolean checkPassword(String password, String hash) {
        return checkPassword(password, hash, null);
    }

    /**
     * 驗證玩家輸入的純文字密碼是否與資料庫中的雜湊值及鹽值相符。
     *
     * @param password 玩家輸入的純文字密碼
     * @param hash     資料庫中儲存的加密密碼字串
     * @param salt     資料庫中儲存的獨立鹽值 (可為 null 或空白)
     * @return true 若密碼正確，否則為 false
     */
    public static boolean checkPassword(String password, String hash, String salt) {
        if (hash == null || password == null) return false;

        // 1. AuthMe 標準 $SHA$salt$hash 或 $SHA512$ 格式
        if (hash.startsWith("$SHA$") || hash.startsWith("$SHA512$")) {
            String[] parts = hash.split("\\$");
            if (parts.length == 4) {
                String extractedSalt = parts[2];
                String hashPart = parts[3];
                if (hashPart.length() == 128) {
                    // SHA-512 (128 hex chars)
                    String computed = computeSha512(password, extractedSalt);
                    return computed.equals(hash) || hashPart.equalsIgnoreCase(sha512Hex(sha512Hex(password) + extractedSalt));
                } else {
                    // SHA-256 (64 hex chars)
                    String computed = computeSha256(password, extractedSalt);
                    return computed.equals(hash) || hashPart.equalsIgnoreCase(sha256Hex(sha256Hex(password) + extractedSalt));
                }
            }
        }

        // 2. AuthMe $MD5$salt$hash 格式
        if (hash.startsWith("$MD5$")) {
            String[] parts = hash.split("\\$");
            if (parts.length == 4) {
                String extractedSalt = parts[2];
                String computed = "$MD5$" + extractedSalt + "$" + computeSalted2Md5(password, extractedSalt);
                return computed.equals(hash) || parts[3].equalsIgnoreCase(computeSalted2Md5(password, extractedSalt));
            }
        }

        // 3. BCrypt 格式 ($2a$, $2b$, $2y$)
        if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
            return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
        }

        // 4. 若有外部/獨立 salt 欄位 (例如 Discuz!, Phpwind, Blessing Skin SALTED2MD5 / SALTEDSHA512)
        if (salt != null && !salt.isBlank()) {
            // SALTED2MD5: MD5(MD5(password) + salt)
            String salted2Md5 = computeSalted2Md5(password, salt);
            if (hash.equalsIgnoreCase(salted2Md5)) {
                return true;
            }
            // MD5(password + salt)
            String saltedMd5 = md5Hex(password + salt);
            if (hash.equalsIgnoreCase(saltedMd5)) {
                return true;
            }
            // SALTEDSHA512: SHA512(SHA512(password) + salt)
            String salted2Sha512 = sha512Hex(sha512Hex(password) + salt);
            if (hash.equalsIgnoreCase(salted2Sha512)) {
                return true;
            }
            // SHA512(password + salt)
            String saltedSha512 = sha512Hex(password + salt);
            if (hash.equalsIgnoreCase(saltedSha512)) {
                return true;
            }
            // SHA256(SHA256(password) + salt)
            String salted2Sha256 = sha256Hex(sha256Hex(password) + salt);
            if (hash.equalsIgnoreCase(salted2Sha256)) {
                return true;
            }
        }

        // 5. 舊版無鹽 MD5 / SHA-256 / SHA-512 比對
        String md5 = md5Hex(password);
        String sha256 = sha256Hex(password);
        String sha512 = sha512Hex(password);
        return hash.equalsIgnoreCase(md5) || hash.equalsIgnoreCase(sha256) || hash.equalsIgnoreCase(sha512);
    }

    /**
     * 計算 Discuz! / Phpwind / Blessing Skin 標準的雙重加鹽 MD5 雜湊。
     * <p>
     * 演算法步驟：{@code MD5(MD5(password) + salt)}。
     *
     * @param password 純文字密碼
     * @param salt     鹽值
     * @return 32 位十六進位小寫雜湊字串
     */
    public static String computeSalted2Md5(String password, String salt) {
        String hash1 = md5Hex(password);
        return md5Hex(hash1 + salt);
    }

    /**
     * 計算 AuthMe 標準的雙重加鹽 SHA-256 雜湊。
     * <p>
     * 演算法步驟：{@code SHA256(SHA256(password) + salt)}。
     *
     * @param password 純文字密碼
     * @param salt     隨機鹽值
     * @return 組合後的雜湊格式字串 (例如: {@code $SHA$salt$hash})
     */
    private static String computeSha256(String password, String salt) {
        String hash1 = sha256Hex(password);
        String hash2 = sha256Hex(hash1 + salt);
        return "$SHA$" + salt + "$" + hash2;
    }

    /**
     * 計算 AuthMe 標準的雙重加鹽 SHA-512 雜湊。
     * <p>
     * 演算法步驟：{@code SHA512(SHA512(password) + salt)}。
     *
     * @param password 純文字密碼
     * @param salt     隨機鹽值
     * @return 組合後的雜湊格式字串 (例如: {@code $SHA$salt$hash})
     */
    private static String computeSha512(String password, String salt) {
        String hash1 = sha512Hex(password);
        String hash2 = sha512Hex(hash1 + salt);
        return "$SHA$" + salt + "$" + hash2;
    }

    /**
     * 計算資料的單次 SHA-512 16進位字串。
     *
     * @param data 輸入字串
     * @return 128位長度之 16 進位字串
     */
    public static String sha512Hex(String data) {
        return hashHex("SHA-512", data);
    }

    /**
     * 計算資料的單次 SHA-256 16進位字串。
     *
     * @param data 輸入字串
     * @return 64位長度之 16 進位字串
     */
    public static String sha256Hex(String data) {
        return hashHex("SHA-256", data);
    }

    /**
     * 計算資料的單次 MD5 16進位字串。
     *
     * @param data 輸入字串
     * @return 32位長度之 16 進位字串
     */
    public static String md5Hex(String data) {
        return hashHex("MD5", data);
    }

    /**
     * 通用訊息摘要演算法計算方法。
     *
     * @param algorithm 演算法名稱 (例如: "SHA-512", "SHA-256", "MD5")
     * @param data      輸入字串
     * @return 16 進位小寫雜湊字串
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
    public static String generateSalt(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }

    /**
     * 生成指定長度的英數字隨機鹽值字串 (用於 Discuz/Phpwind 等外部系統)。
     *
     * @param length 鹽值長度
     * @return 隨機英數字字串
     */
    public static String generateRandomSalt(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
