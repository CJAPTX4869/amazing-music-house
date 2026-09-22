package com.example.music.utils;

/**
 * 展示层格式化工具
 */
public final class FormatUtils {

    private static final String DASH = "-";

    private FormatUtils() {
    }

    /**
     * 字节数 → 可读文本（1 KB = 1024 B）
     */
    public static String fileSize(Long bytes) {
        if (bytes == null || bytes < 0) {
            return DASH;
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.2f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.2f MB", mb);
        }
        return String.format("%.2f GB", mb / 1024.0);
    }

    /**
     * 秒 → mm:ss（超过 1 小时显示 hh:mm:ss）
     */
    public static String duration(Integer seconds) {
        if (seconds == null || seconds <= 0) {
            return "00:00";
        }
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%02d:%02d", minutes, secs);
    }
}
