package com.example.music.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * 文件存储服务（本地磁盘实现）
 * <p>
 * 抽象此接口便于后续替换存储介质，同时对上层屏蔽磁盘细节。
 */
public interface FileStorageService {

    /**
     * 校验文件（非空 / 扩展名 / MIME / 大小 / 磁盘余量）
     *
     * @throws com.example.music.common.exception.BusinessException 校验不通过
     */
    void validate(MultipartFile file);

    /**
     * 保存文件：UUID 重命名 + 日期分片目录，写入过程同步计算 MD5
     *
     * @param file   上传文件
     * @param subDir 子目录（audio / cover）
     * @return 存储结果（文件名、相对路径、MD5、大小）
     */
    StoredFile store(MultipartFile file, String subDir);

    /**
     * 保存歌词文件（LRC）
     * <p>
     * 与音频分开的原因：音频校验使用扩展名/MIME 白名单（仅音频格式），
     * 歌词为文本文件，若复用 {@link #store} 会被判为 415 不支持的类型。
     *
     * @param file 歌词文件（.lrc / .txt，≤ 1MB）
     * @return 存储结果（相对路径形如 lyric/2026/09/15/xxx.lrc）
     */
    StoredFile storeLyric(MultipartFile file);

    /**
     * 保存封面图片
     * <p>
     * 与音频分开的原因同 {@link #storeLyric}：封面是图片，需独立的扩展名白名单
     * （jpg / jpeg / png / webp / gif），复用 {@link #store} 会被判为 415。
     *
     * @param file 图片文件（上述格式，≤ 5MB；前端已压缩为正方形，后端不重复处理）
     * @return 存储结果（相对路径形如 cover/2026/09/15/xxx.jpg）
     */
    StoredFile storeCover(MultipartFile file);

    /**
     * 保存用户头像图片
     * <p>
     * 独立目录（avatar）而非复用封面目录：头像与歌曲生命周期无关，
     * 混放会导致清理歌曲封面时误删用户头像。
     *
     * @param file 图片文件（jpg / jpeg / png / webp / gif，≤ 2MB；前端已裁剪为正方形）
     * @return 存储结果（相对路径形如 avatar/2026/09/16/xxx.jpg）
     */
    StoredFile storeAvatar(MultipartFile file);

    /**
     * 保存评论配图
     * <p>
     * 独立目录（comment）避免与歌曲封面/头像混放；支持 GIF 动图原图上传
     * （GIF 不做像素压缩以保留动画，仅限大小）。
     *
     * @param file 图片文件（jpg / jpeg / png / webp / gif，≤ 5MB）
     * @return 存储结果（相对路径形如 comment/2026/09/18/xxx.png）
     */
    StoredFile storeCommentImage(MultipartFile file);

    /**
     * 保存表情包图片（管理员上传，服务端统一管理；用户端只消费）
     *
     * @param file 图片文件（jpg / jpeg / png / webp / gif，≤ 1MB）
     * @return 存储结果（相对路径形如 emoji/2026/09/18/xxx.png）
     */
    StoredFile storeEmoji(MultipartFile file);

    /**
     * 按相对路径加载文件资源（文件不存在抛 1005）
     */
    Resource loadAsResource(String relativePath);

    /**
     * 删除文件（不存在时忽略；异常仅记录日志）
     */
    void delete(String relativePath);

    /**
     * 将相对路径解析为绝对路径，并校验是否越出存储根目录（防路径穿越）
     */
    Path resolveAbsolute(String relativePath);

    /**
     * 提取小写扩展名（不含点）；无法识别时返回空串
     */
    String extractExtension(String originalFilename);

    /**
     * 获取存储目录所在磁盘的可用空间（字节）
     */
    long getUsableSpace();

    /**
     * 获取存储目录所在磁盘的总空间（字节）
     */
    long getTotalSpace();

    /**
     * 文件落盘结果
     */
    @Data
    @AllArgsConstructor
    class StoredFile {
        /** 落盘文件名（UUID.扩展名） */
        private String fileName;
        /** 相对存储路径，如 audio/2026/09/15/xxx.mp3 */
        private String relativePath;
        /** 文件 MD5 */
        private String md5;
        /** 文件大小（字节） */
        private Long size;
    }
}
