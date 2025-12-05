package other.coloplast.impl.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class TimestampConverter {
    
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    private static final List<String> COMMON_PATTERNS = Arrays.asList(
        "yyyy-MM-dd HH:mm:ss.S",
        "yyyy-MM-dd HH:mm:ss.SS",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy/MM/dd HH:mm:ss.S",
        "yyyy/MM/dd HH:mm:ss.SS",
        "yyyy/MM/dd HH:mm:ss.SSS",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm",
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "yyyyMMddHHmmss",
        "yyyyMMdd",
        "yyyy年MM月dd日 HH时mm分ss秒",
        "yyyy年MM月dd日 HH时mm分",
        "yyyy年MM月dd日"
    );
    
    /**
     * 统一方法：自动识别日期格式并转换为时间戳
     * @param dateTimeStr 日期时间字符串
     * @return 时间戳（毫秒），解析失败返回 -1
     */
    public static long toTimestamp(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty() || dateTimeStr.equals("0000-00-00")) {
            System.out.println("格式不对返回："+dateTimeStr);
            return -1;
        }
        
        String input = dateTimeStr.trim();
        
        // 尝试常见格式
        for (String pattern : COMMON_PATTERNS) {
            try {
                return parseWithPattern(input, pattern);
            } catch (DateTimeParseException e) {
                // 继续尝试下一个格式
                continue;
            }
        }
        
        // 尝试纯数字格式（时间戳或数字日期）
        try {
            return parseNumericInput(input);
        } catch (Exception e) {
            // 继续尝试其他方法
        }
        
        // 尝试智能解析
        try {
            return smartParse(input);
        } catch (Exception e) {
            return -1; // 所有方法都失败
        }
    }
    
    /**
     * 使用指定格式解析
     */
    private static long parseWithPattern(String input, String pattern) {
        if (pattern.contains("HH") || pattern.contains("mm") || pattern.contains("ss")) {
            // 包含时间信息
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            LocalDateTime dateTime = LocalDateTime.parse(input, formatter);
            return dateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
        } else {
            // 仅日期信息
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            LocalDate date = LocalDate.parse(input, formatter);
            return date.atStartOfDay(DEFAULT_ZONE).toInstant().toEpochMilli();
        }
    }
    
    /**
     * 解析数字输入（可能是时间戳或数字日期）
     */
    private static long parseNumericInput(String input) {
        // 如果是纯数字
        if (Pattern.matches("\\d+", input)) {
            long number = Long.parseLong(input);
            
            // 判断是否是时间戳（根据长度）
            if (input.length() == 13) { // 毫秒时间戳
                return number;
            } else if (input.length() == 10) { // 秒时间戳
                return number * 1000;
            } else if (input.length() == 8) { // yyyyMMdd 格式
                return parseWithPattern(input, "yyyyMMdd");
            } else if (input.length() == 14) { // yyyyMMddHHmmss 格式
                return parseWithPattern(input, "yyyyMMddHHmmss");
            }
        }
        throw new DateTimeParseException("Not a valid numeric date format", input, 0);
    }
    
    /**
     * 智能解析：根据字符串特征自动识别格式
     */
    private static long smartParse(String input) {
        // 判断分隔符类型
        if (input.contains("-")) {
            if (input.contains(":")) {
                // 检查是否包含毫秒部分
                if (input.contains(".")) {
                    // 根据小数点后的位数确定毫秒格式
                    String[] parts = input.split("\\.");
                    if (parts.length == 2) {
                        String millisPart = parts[1];
                        if (millisPart.length() == 1) {
                            return parseWithPattern(input, "yyyy-MM-dd HH:mm:ss.S");
                        } else if (millisPart.length() == 2) {
                            return parseWithPattern(input, "yyyy-MM-dd HH:mm:ss.SS");
                        } else if (millisPart.length() == 3) {
                            return parseWithPattern(input, "yyyy-MM-dd HH:mm:ss.SSS");
                        }
                    }
                }
                return parseWithPattern(input, "yyyy-MM-dd HH:mm:ss");
            } else {
                return parseWithPattern(input, "yyyy-MM-dd");
            }
        } else if (input.contains("/")) {
            if (input.contains(":")) {
                // 检查是否包含毫秒部分
                if (input.contains(".")) {
                    // 根据小数点后的位数确定毫秒格式
                    String[] parts = input.split("\\.");
                    if (parts.length == 2) {
                        String millisPart = parts[1];
                        if (millisPart.length() == 1) {
                            return parseWithPattern(input, "yyyy/MM/dd HH:mm:ss.S");
                        } else if (millisPart.length() == 2) {
                            return parseWithPattern(input, "yyyy/MM/dd HH:mm:ss.SS");
                        } else if (millisPart.length() == 3) {
                            return parseWithPattern(input, "yyyy/MM/dd HH:mm:ss.SSS");
                        }
                    }
                }
                return parseWithPattern(input, "yyyy/MM/dd HH:mm:ss");
            } else {
                return parseWithPattern(input, "yyyy/MM/dd");
            }
        } else if (input.contains("年")) {
            if (input.contains("时")) {
                return parseWithPattern(input, "yyyy年MM月dd日 HH时mm分ss秒");
            } else {
                return parseWithPattern(input, "yyyy年MM月dd日");
            }
        } else if (input.contains(":")) {
            // 只有时间，没有明确日期分隔符，尝试添加当前日期
            String currentDate = LocalDate.now().toString();
            return parseWithPattern(currentDate + " " + input, "yyyy-MM-dd HH:mm:ss");
        }
        
        throw new DateTimeParseException("Cannot determine format", input, 0);
    }
    
    /**
     * 重载方法：支持数字参数输入
     */
    public static long toTimestamp(int year, int month, int day) {
        return LocalDate.of(year, month, day)
                       .atStartOfDay(DEFAULT_ZONE)
                       .toInstant()
                       .toEpochMilli();
    }
    
    /**
     * 重载方法：支持数字参数输入（带时间）
     */
    public static long toTimestamp(int year, int month, int day, 
                                  int hour, int minute, int second) {
        return LocalDateTime.of(year, month, day, hour, minute, second)
                           .atZone(DEFAULT_ZONE)
                           .toInstant()
                           .toEpochMilli();
    }
    
    /**
     * 转换为秒级时间戳
     */
    public static long toSecondTimestamp(long millisTimestamp) {
        return millisTimestamp / 1000;
    }
    
    /**
     * 转换为秒级时间戳（从字符串）
     */
    public static long toSecondTimestamp(String dateTimeStr) {
        long millis = toTimestamp(dateTimeStr);
        return millis != -1 ? millis / 1000 : -1;
    }

    public static void main(String[] args) {
        // 测试各种时间格式
        String[] testDates = {
            "2025-09-15 16:46:05.0",
            "2025-09-15 16:46:05.12",
            "2025-09-15 16:46:05.123",
            "2025-09-15 16:46:05",
            "2025-09-15",
            "0000-00-00"
        };
        
        for (String dateStr : testDates) {
            long timestamp = TimestampConverter.toTimestamp(dateStr);
            System.out.printf("输入: %-25s -> 时间戳: %d%n", dateStr, timestamp);
        }
    }
}