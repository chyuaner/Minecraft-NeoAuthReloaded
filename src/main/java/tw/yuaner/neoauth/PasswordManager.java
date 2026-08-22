package tw.yuaner.neoauth;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

public class PasswordManager {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String hashPassword(String password) {
        // We use AuthMe's default SHA256 format for new passwords
        String salt = generateSalt(16);
        return computeSha256(password, salt);
    }

    public static boolean checkPassword(String password, String hash) {
        if (hash == null) return false;
        
        if (hash.startsWith("$SHA$")) {
            String[] parts = hash.split("\\$");
            if (parts.length == 4) {
                String salt = parts[2];
                String computed = computeSha256(password, salt);
                return computed.equals(hash);
            }
        } else if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
            // BCrypt
            return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
        } else {
            // Unsalted MD5/SHA256 (fallback for very old AuthMe)
            String md5 = md5Hex(password);
            String sha256 = sha256Hex(password);
            return hash.equalsIgnoreCase(md5) || hash.equalsIgnoreCase(sha256);
        }
        
        return false;
    }

    private static String computeSha256(String password, String salt) {
        String hash1 = sha256Hex(password);
        String hash2 = sha256Hex(hash1 + salt);
        return "$SHA$" + salt + "$" + hash2;
    }

    private static String sha256Hex(String data) {
        return hashHex("SHA-256", data);
    }

    private static String md5Hex(String data) {
        return hashHex("MD5", data);
    }

    private static String hashHex(String algorithm, String data) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not found: " + algorithm, e);
        }
    }

    private static String generateSalt(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }
}
