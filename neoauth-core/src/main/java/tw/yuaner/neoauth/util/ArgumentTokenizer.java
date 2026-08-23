package tw.yuaner.neoauth.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 指令參數解析工具類別。
 * 支援未引號特殊字元（如 @, #, $, !）以及帶引號的字串，解決 Brigadier 指令對特殊符號的預設限制。
 */
public final class ArgumentTokenizer {

    private ArgumentTokenizer() {
    }

    /**
     * 將輸入字串依據空格與雙引號/單引號解析為參數列表。
     * 支援未引號特殊字元（如 @, #, $, ! 等）以及帶引號的字串。
     *
     * @param input 原始輸入字串
     * @return 參數列表
     */
    public static List<String> tokenize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = '"';

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                } else if (c == '\\' && i + 1 < input.length() && input.charAt(i + 1) == quoteChar) {
                    current.append(quoteChar);
                    i++; // 跳過轉義字元
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuotes = true;
                    quoteChar = c;
                } else if (Character.isWhitespace(c)) {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(c);
                }
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    /**
     * 清理單一參數。若帶有外層引號則去除，若為空則回傳空字串。
     *
     * @param raw 原始參數字串
     * @return 清理後的字串
     */
    public static String cleanArgument(String raw) {
        if (raw == null) {
            return "";
        }
        List<String> tokens = tokenize(raw);
        if (tokens.isEmpty()) {
            return raw.trim();
        }
        if (tokens.size() == 1) {
            return tokens.get(0);
        }
        // 若包含空格且未加引號（例如 "my secret pass" 未加引號），保留原字串但去除前後空白
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            return tokens.get(0);
        }
        return raw.trim();
    }
}
