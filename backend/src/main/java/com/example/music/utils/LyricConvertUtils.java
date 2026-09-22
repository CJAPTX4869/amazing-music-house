package com.example.music.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌词格式规范化（本地导入用）
 * <p>
 * 网易云客户端下载的 .lrc 常见是「逐字歌词 JSON」：每行一个 JSON 对象
 * {@code {"t":1234,"c":[{"tx":"作词: "},{"tx":"梶浦由記","li":"http://..."}]}}
 * 其中 t 为毫秒时间戳，c[].tx 为文本片段（li/or 是艺人或链接的附加信息，不应作为歌词文本）。
 * 播放器只认标准 LRC（{@code [mm:ss.xx]文本}），因此导入前必须转换。
 * <p>
 * 已是标准 LRC 的内容原样返回，避免二次转换损坏格式。
 */
@Slf4j
public final class LyricConvertUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LyricConvertUtils() {
    }

    /**
     * 规范化为 LRC 文本（逐行处理）
     * <p>
     * 注意：实测网易云下载的 .lrc 可能是<b>混合格式</b>——前几行是逐字歌词 JSON（带 li/or 附加信息），
     * 后面是标准 LRC 行。因此必须逐行判断，不能整体判定：整体判定会被后面的 {@code [mm:ss]} 命中
     * 而直接原样返回，导致 JSON 行残留（播放器无法解析）。
     * <ul>
     *   <li>逐字 JSON 行 → 转为 {@code [mm:ss.xx]文本}</li>
     *   <li>标准 LRC 行 → 原样保留</li>
     *   <li>空行 / 无法识别 → 跳过或原样保留（不破坏已有内容）</li>
     * </ul>
     */
    public static String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String[] rows = raw.split("\\R");
        List<String> out = new ArrayList<>();
        for (String row : rows) {
            String line = row.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (isJsonLyricLine(line)) {
                String converted = convertOne(line);
                if (converted != null) {
                    out.add(converted);
                }
                continue;
            }
            // 标准 LRC 行或其它内容（如 [ti:标题] 元信息）原样保留
            out.add(line);
        }
        return String.join("\n", out);
    }

    /** 逐字歌词行特征：JSON 对象且含时间/文本字段 */
    private static boolean isJsonLyricLine(String line) {
        return line.startsWith("{") && line.contains("\"t\"") && line.contains("\"c\"");
    }

    /** 转换单行逐字歌词；解析失败返回 null（该行丢弃，避免脏数据进入歌词文件） */
    private static String convertOne(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            long time = node.path("t").asLong();
            StringBuilder sb = new StringBuilder();
            JsonNode content = node.path("c");
            if (content.isArray()) {
                for (JsonNode seg : content) {
                    // tx 为歌词文本；li/or 是链接与内部协议地址，不能混入歌词
                    sb.append(seg.path("tx").asText(""));
                }
            }
            String lyric = sb.toString().trim();
            return lyric.isEmpty() ? null : formatTime(time) + lyric;
        } catch (Exception e) {
            log.debug("逐字歌词行解析失败已丢弃：{}", line.length() > 60 ? line.substring(0, 60) : line);
            return null;
        }
    }

    /** 毫秒 → [mm:ss.xx] */
    private static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long centis = (millis % 1000) / 10;
        return String.format("[%02d:%02d.%02d]", minutes, seconds, centis);
    }
}
