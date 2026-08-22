package tw.yuaner.neoauth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * 支援完整註解保留之 YAML 範本合併工具。
 * <p>
 * 能在將磁碟上既有的設定值與內建預設範本合併時，100% 保留所有註解、格式與預設值說明。
 */
public class YamlCommentPreserver {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeoAuth-YamlMerger");

    /**
     * 以內建範本文字為基準，將使用者在 diskMap 中自訂的數值替換進去，
     * 並補齊範本中所有缺失的區塊與鍵值（包含所有註解說明）。
     *
     * @param templateText 內建完整範本原始文字 (含完整註解)
     * @param diskMap      使用者磁碟上的設定 Map
     * @return 合併後保留註解的 YAML 文字字串
     */
    public static String mergePreservingComments(String templateText, Map<String, Object> diskMap) {
        if (templateText == null || templateText.isEmpty()) {
            return "";
        }
        if (diskMap == null || diskMap.isEmpty()) {
            return templateText;
        }

        Map<String, Object> flatDiskMap = new LinkedHashMap<>();
        flattenMap("", diskMap, flatDiskMap);

        String[] lines = templateText.split("\r?\n", -1);
        StringBuilder result = new StringBuilder();

        // 堆疊結構：記錄當前縮排與區塊名稱
        List<SectionEntry> sectionStack = new ArrayList<>();
        Set<String> processedKeys = new HashSet<>();

        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            // 註解行或空白行，原樣保留
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                result.append(line).append("\n");
                i++;
                continue;
            }

            int indent = line.length() - line.stripLeading().length();

            // 若縮排回退，彈出較深層的 section
            while (!sectionStack.isEmpty() && sectionStack.get(sectionStack.size() - 1).indent >= indent) {
                sectionStack.remove(sectionStack.size() - 1);
            }

            // 檢查是否為 key-value 行 (例如 "mySQLHost: '127.0.0.1'" 或 "settings:")
            int colonIdx = line.indexOf(':');
            if (colonIdx != -1) {
                String keyPart = line.substring(indent, colonIdx).trim();
                // 排除列表項中的冒號 (例如 "  - http://...")
                if (!keyPart.startsWith("-")) {
                    String fullPath = getPath(sectionStack, keyPart);
                    String rest = line.substring(colonIdx + 1).trim();

                    // 若此行是新的父級區塊 (例如 "DataSource:" 或 "security:")
                    if (rest.isEmpty() || rest.startsWith("#")) {
                        sectionStack.add(new SectionEntry(keyPart, indent));
                        result.append(line).append("\n");
                        i++;
                        continue;
                    }

                    // 檢查 diskMap 是否有此鍵的值
                    if (flatDiskMap.containsKey(fullPath)) {
                        Object val = flatDiskMap.get(fullPath);
                        processedKeys.add(fullPath);

                        // 檢查是否有行尾註解
                        String inlineComment = "";
                        int hashIdx = line.indexOf('#', colonIdx);
                        if (hashIdx != -1) {
                            inlineComment = " " + line.substring(hashIdx).trim();
                        }

                        // 判斷是否為列表
                        if (val instanceof List<?> list) {
                            result.append(" ".repeat(indent)).append(keyPart).append(":");
                            if (inlineComment.isEmpty()) {
                                result.append("\n");
                            } else {
                                result.append(inlineComment).append("\n");
                            }
                            // 替換後續的列表項目行
                            i++;
                            while (i < lines.length && isListItem(lines[i], indent)) {
                                i++; // 跳過範本原本的 list 行
                            }
                            // 寫入 diskMap 的 list 行
                            for (Object item : list) {
                                result.append(" ".repeat(indent + 2)).append("- ").append(formatScalar(item)).append("\n");
                            }
                            continue;
                        } else {
                            // 純量值 (String, Number, Boolean)
                            result.append(" ".repeat(indent)).append(keyPart).append(": ").append(formatScalar(val));
                            if (!inlineComment.isEmpty()) {
                                result.append(inlineComment);
                            }
                            result.append("\n");
                            i++;
                            continue;
                        }
                    }
                }
            }

            result.append(line).append("\n");
            i++;
        }

        // 檢查是否有使用者自訂但範本沒有的額外鍵值
        Map<String, Object> extraKeys = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : diskMap.entrySet()) {
            if (!templateText.contains(entry.getKey() + ":")) {
                extraKeys.put(entry.getKey(), entry.getValue());
            }
        }
        if (!extraKeys.isEmpty()) {
            result.append("\n# ==============================================================================\n");
            result.append("# 自訂延伸設定項目\n");
            result.append("# ==============================================================================\n");
            Yaml yaml = new Yaml();
            result.append(yaml.dump(extraKeys));
        }

        return result.toString();
    }

    private static boolean isListItem(String line, int parentIndent) {
        String trimmed = line.trim();
        if (trimmed.startsWith("-")) {
            int indent = line.length() - line.stripLeading().length();
            return indent > parentIndent;
        }
        return false;
    }

    private static String getPath(List<SectionEntry> stack, String key) {
        if (stack.isEmpty()) return key;
        StringBuilder sb = new StringBuilder();
        for (SectionEntry entry : stack) {
            sb.append(entry.name).append(".");
        }
        sb.append(key);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void flattenMap(String prefix, Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> subMap) {
                flattenMap(path, (Map<String, Object>) subMap, target);
            } else {
                target.put(path, entry.getValue());
            }
        }
    }

    private static String formatScalar(Object val) {
        if (val == null) return "\"\"";
        if (val instanceof Boolean || val instanceof Number) {
            return String.valueOf(val);
        }
        String str = String.valueOf(val);
        if (str.isEmpty()) return "\"\"";
        if (str.contains(":") || str.contains("#") || str.contains("'") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\\\"") + "\"";
        }
        return "\"" + str + "\"";
    }

    private static class SectionEntry {
        final String name;
        final int indent;

        SectionEntry(String name, int indent) {
            this.name = name;
            this.indent = indent;
        }
    }
}
