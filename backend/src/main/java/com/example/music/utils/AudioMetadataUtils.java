package com.example.music.utils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 音频元数据（ID3 标签）解析工具
 * <p>
 * 依赖 jaudiotagger。解析失败时降级返回空对象，绝不阻断上传流程。
 */
@Slf4j
public final class AudioMetadataUtils {

    static {
        // 关闭 jaudiotagger 内部的 java.util.logging 噪音日志
        try {
            Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
        } catch (Exception ignored) {
            // 忽略
        }
    }

    private AudioMetadataUtils() {
    }

    /**
     * 解析音频元数据
     *
     * @param path 音频文件的绝对路径
     * @return 解析结果（失败时各字段为 null，duration 为 0）
     */
    public static AudioMetadata parse(Path path) {
        AudioMetadata metadata = new AudioMetadata();
        if (path == null || !path.toFile().exists()) {
            return metadata;
        }
        try {
            AudioFile audioFile = AudioFileIO.read(path.toFile());

            if (audioFile.getAudioHeader() != null) {
                metadata.setDuration(audioFile.getAudioHeader().getTrackLength());
            }

            Tag tag = audioFile.getTag();
            if (tag != null) {
                metadata.setTitle(trimToNull(tag.getFirst(FieldKey.TITLE)));
                metadata.setArtist(trimToNull(tag.getFirst(FieldKey.ARTIST)));
                metadata.setAlbum(trimToNull(tag.getFirst(FieldKey.ALBUM)));
                metadata.setGenre(trimToNull(tag.getFirst(FieldKey.GENRE)));
            }
        } catch (Exception e) {
            // 部分格式（如 wma / 无标签文件）解析失败属正常情况，降级处理
            log.debug("音频元数据解析失败（已降级为手动填写）：{} - {}", path.getFileName(), e.getMessage());
        }
        return metadata;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 音频元数据
     */
    @Data
    public static class AudioMetadata {
        private String title;
        private String artist;
        private String album;
        private String genre;
        /** 时长（秒），解析失败为 0 */
        private Integer duration = 0;
    }
}
