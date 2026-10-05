package tw.yuaner.neoauth.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 時間範圍與筆數解析器 (Prometheus / Go / Docker 風格)。
 * 格式: <數量><單位> (例如 10s, 30m, 1h, 5d, 3w, 1M, 2mo, 1y) 或純數字筆數 (如 6, 10)。
 *
 * 單位代碼規範:
 * - s: seconds (秒)
 * - m: minutes (分)
 * - h: hours (小時)
 * - d: days (天)
 * - w: weeks (週)
 * - M 或 mo: months (月，以 30 天計；特別注意區分小寫 m 分鐘與大寫 M / mo 月份)
 * - y: years (年，以 365 天計)
 */
public final class TimeSpanParser {

    private static final Pattern COUNT_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\d+)\\s*(mo|MO|Mo|mO|[smhdwMySMHDWY])");

    public static class ParseResult {
        private final boolean valid;
        private final boolean isCount;
        private final int count;
        private final long durationMillis;
        private final String errorMessage;

        private ParseResult(boolean valid, boolean isCount, int count, long durationMillis, String errorMessage) {
            this.valid = valid;
            this.isCount = isCount;
            this.count = count;
            this.durationMillis = durationMillis;
            this.errorMessage = errorMessage;
        }

        public static ParseResult ofCount(int count) {
            return new ParseResult(true, true, count, 0L, null);
        }

        public static ParseResult ofDuration(long durationMillis) {
            return new ParseResult(true, false, 0, durationMillis, null);
        }

        public static ParseResult error(String message) {
            return new ParseResult(false, false, 0, 0L, message);
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isCount() {
            return isCount;
        }

        public int getCount() {
            return count;
        }

        public long getDurationMillis() {
            return durationMillis;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private TimeSpanParser() {
    }

    /**
     * 判斷是否為純整數筆數格式。
     */
    public static boolean isCount(String input) {
        if (input == null) return false;
        return COUNT_PATTERN.matcher(input.trim()).matches();
    }

    /**
     * 解析純整數筆數，若不合法則傳回預設值。
     */
    public static int parseCount(String input, int def, int max) {
        if (!isCount(input)) return def;
        try {
            int val = Integer.parseInt(input.trim());
            if (val <= 0) return def;
            return Math.min(val, max);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 判斷是否為時間長度格式。
     */
    public static boolean isDuration(String input) {
        ParseResult res = parse(input);
        return res.isValid() && !res.isCount();
    }

    /**
     * 解析時間範圍或筆數字串。
     *
     * @param input 輸入字串 (如 "1d", "6", "3w", "3m", "2M", "2mo")
     * @return {@link ParseResult}
     */
    public static ParseResult parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return ParseResult.ofCount(6);
        }
        String trimmed = input.trim();
        if (isCount(trimmed)) {
            try {
                int c = Integer.parseInt(trimmed);
                if (c <= 0) c = 6;
                if (c > 100) c = 100;
                return ParseResult.ofCount(c);
            } catch (NumberFormatException e) {
                return ParseResult.ofCount(6);
            }
        }

        Matcher matcher = TOKEN_PATTERN.matcher(trimmed);
        int lastEnd = 0;
        long totalMillis = 0L;
        boolean matchedAny = false;

        while (matcher.find()) {
            String inBetween = trimmed.substring(lastEnd, matcher.start()).trim();
            if (!inBetween.isEmpty()) {
                return ParseResult.error("含有無法辨識的字元: " + inBetween);
            }
            lastEnd = matcher.end();
            matchedAny = true;

            long qty;
            try {
                qty = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return ParseResult.error("數值過大: " + matcher.group(1));
            }

            String unit = matcher.group(2);
            long unitMillis;
            if (unit.equalsIgnoreCase("mo") || unit.equals("M")) {
                unitMillis = 30L * 24 * 60 * 60 * 1000L; // 30 天
            } else if (unit.equals("m")) {
                unitMillis = 60 * 1000L; // 1 分鐘
            } else if (unit.equalsIgnoreCase("s")) {
                unitMillis = 1000L; // 1 秒
            } else if (unit.equalsIgnoreCase("h")) {
                unitMillis = 60 * 60 * 1000L; // 1 小時
            } else if (unit.equalsIgnoreCase("d")) {
                unitMillis = 24 * 60 * 60 * 1000L; // 1 天
            } else if (unit.equalsIgnoreCase("w")) {
                unitMillis = 7L * 24 * 60 * 60 * 1000L; // 1 週
            } else if (unit.equalsIgnoreCase("y")) {
                unitMillis = 365L * 24 * 60 * 60 * 1000L; // 1 年
            } else {
                return ParseResult.error("未知的時間單位: " + unit);
            }

            if (qty > 0 && Long.MAX_VALUE / qty < unitMillis) {
                return ParseResult.error("時間數值超出上限");
            }
            totalMillis += (qty * unitMillis);
        }

        String remaining = trimmed.substring(lastEnd).trim();
        if (!matchedAny || !remaining.isEmpty()) {
            return ParseResult.error("無法解析的時間格式: " + trimmed);
        }

        if (totalMillis <= 0) {
            return ParseResult.error("時間長度必須大於 0");
        }

        return ParseResult.ofDuration(totalMillis);
    }

    /**
     * 格式化毫秒為易讀之時長字串 (支援中文與英文)。
     *
     * @param millis 毫秒數
     * @param isZh   是否輸出正體中文
     * @return 格式化後的時長字串
     */
    public static String formatDuration(long millis, boolean isZh) {
        if (millis <= 0) {
            return isZh ? "0秒" : "0s";
        }
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60)) % 24;
        long days = millis / (1000 * 60 * 60 * 24);

        if (isZh) {
            if (days > 0) {
                return days + "天 " + hours + "小時 " + minutes + "分";
            }
            if (hours > 0) {
                return hours + "小時 " + minutes + "分 " + seconds + "秒";
            }
            if (minutes > 0) {
                return minutes + "分 " + seconds + "秒";
            }
            return seconds + "秒";
        } else {
            if (days > 0) {
                return days + "d " + hours + "h " + minutes + "m";
            }
            if (hours > 0) {
                return hours + "h " + minutes + "m " + seconds + "s";
            }
            if (minutes > 0) {
                return minutes + "m " + seconds + "s";
            }
            return seconds + "s";
        }
    }
}
