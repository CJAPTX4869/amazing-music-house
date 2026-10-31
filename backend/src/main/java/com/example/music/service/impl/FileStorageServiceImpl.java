package com.example.music.service.impl;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.IdUtil;
import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.ResultCode;
import com.example.music.config.StorageProperties;
import com.example.music.constant.StorageConstants;
import com.example.music.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 本地磁盘文件存储实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String DATE_SEPARATOR = "/";
    private static final String DOT = ".";
    private static final int BUFFER_SIZE = 8192;
    private static final String OCTET_STREAM = "application/octet-stream";

    /** 歌词文件大小上限（1MB，歌词纯文本远小于此，防止异常大文件） */
    private static final long MAX_LYRIC_SIZE = 1024 * 1024;

    /** 允许的歌词文件扩展名 */
    private static final Set<String> ALLOWED_LYRIC_EXTENSIONS =
            new HashSet<>(Arrays.asList("lrc", "txt"));

    /** 封面图片大小上限（5MB；前端 canvas 压缩后通常仅 100–300KB，此处留足余量） */
    private static final long MAX_COVER_SIZE = 5L * 1024 * 1024;

    /** 允许的封面图片扩展名 */
    private static final Set<String> ALLOWED_COVER_EXTENSIONS =
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "webp", "gif"));

    /** 头像图片大小上限（2MB；前端已裁剪为 512×512 JPEG，通常仅几十 KB） */
    private static final long MAX_AVATAR_SIZE = 2L * 1024 * 1024;

    /** 允许的头像图片扩展名 */
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS =
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "webp", "gif"));

    /** 评论配图大小上限（5MB，支持 GIF 动图原图上传） */
    private static final long MAX_COMMENT_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 允许的评论配图扩展名（含 GIF 动图） */
    private static final Set<String> ALLOWED_COMMENT_IMAGE_EXTENSIONS =
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "webp", "gif"));

    /** 表情图大小上限（1MB，面板表情体积应尽量小） */
    private static final long MAX_EMOJI_SIZE = 1024 * 1024;

    /** 允许的表情图扩展名 */
    private static final Set<String> ALLOWED_EMOJI_EXTENSIONS =
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "webp", "gif"));

    private final StorageProperties storageProperties;

    /** 按文件头魔数判断是否真实音频：MP3(ID3/0xFFEx)/WAV(RIFF..WAVE)/OGG/FLAC/MP4-M4A(ftyp)/WMA(D0CF11E0) */
    private boolean looksLikeAudio(MultipartFile file) {
        byte[] head = new byte[12];
        try (java.io.InputStream in = file.getInputStream()) {
            int n = in.read(head);
            if (n < 4) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return startsWith(head, new byte[]{'I', 'D', '3'})                       // MP3 (ID3v2)
                || (head[0] == (byte) 0xFF && (head[1] & 0xE0) == 0xE0)         // MP3 (MPEG 帧同步)
                || (startsWith(head, new byte[]{'R', 'I', 'F', 'F'})
                    && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E') // WAV
                || startsWith(head, new byte[]{'O', 'g', 'g', 'S'})              // OGG
                || startsWith(head, new byte[]{'f', 'L', 'a', 'C'})              // FLAC
                || startsWith(head, new byte[]{'f', 't', 'y', 'p'})              // MP4/M4A
                || startsWith(head, new byte[]{(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0}); // WMA/ASF
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (i >= data.length || data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY);
        }
        if (file.getSize() > storageProperties.getMaxFileSize()) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE,
                    "文件大小超过限制（最大 " + (storageProperties.getMaxFileSize() / 1024 / 1024) + "MB）");
        }

        String ext = extractExtension(file.getOriginalFilename());
        if (ext.isEmpty() || !storageProperties.getAllowedExtensions().contains(ext)) {
            throw new BusinessException(ResultCode.UNSUPPORTED_TYPE,
                    "不支持的音频格式" + (ext.isEmpty() ? "" : "：" + ext)
                            + "，仅支持 " + String.join(" / ", storageProperties.getAllowedExtensions()));
        }

        String contentType = file.getContentType();
        // MIME 宽松校验：浏览器对部分格式会给出 application/octet-stream，这种情况放行（仍受扩展名白名单约束）
        if (contentType != null && !contentType.trim().isEmpty()
                && !OCTET_STREAM.equalsIgnoreCase(contentType)
                && !storageProperties.getAllowedMimeTypes().contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.UNSUPPORTED_TYPE, "文件类型不匹配：" + contentType);
        }

        // 魔数校验：扩展名与 MIME 都可伪造，必须读文件头确认真实音频内容，
        // 防止把任意字节改后缀传上来占存储/绕过内容属性
        if (!looksLikeAudio(file)) {
            throw new BusinessException(ResultCode.UNSUPPORTED_TYPE,
                    "文件内容不是有效的音频（已按文件头校验，改扩展名无效）");
        }

        // 磁盘余量校验，避免写满磁盘导致服务不可用
        if (getUsableSpace() < storageProperties.getMinFreeSpace()) {
            throw new BusinessException(ResultCode.DISK_FULL);
        }
    }

    @Override
    public StoredFile store(MultipartFile file, String subDir) {
        validate(file);
        return writeToDisk(file, subDir, extractExtension(file.getOriginalFilename()));
    }

    @Override
    public StoredFile storeLyric(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY, "歌词文件为空");
        }
        if (file.getSize() > MAX_LYRIC_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE, "歌词文件不能超过 1MB");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_LYRIC_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.UNSUPPORTED_TYPE, "歌词文件仅支持 .lrc / .txt 格式");
        }
        // 歌词同样需要磁盘余量校验（虽体积小，但保持一致性）
        if (getUsableSpace() < storageProperties.getMinFreeSpace()) {
            throw new BusinessException(ResultCode.DISK_FULL);
        }
        return writeToDisk(file, StorageConstants.LYRIC_DIR, ext);
    }

    @Override
    public StoredFile storeCover(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY, "封面图片为空");
        }
        if (file.getSize() > MAX_COVER_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE,
                    "封面图片不能超过 " + (MAX_COVER_SIZE / 1024 / 1024) + "MB");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_COVER_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.UNSUPPORTED_TYPE,
                    "封面仅支持 jpg / jpeg / png / webp / gif 格式");
        }
        // 磁盘余量校验，与音频/歌词保持一致
        if (getUsableSpace() < storageProperties.getMinFreeSpace()) {
            throw new BusinessException(ResultCode.DISK_FULL);
        }
        return writeToDisk(file, StorageConstants.COVER_DIR, ext);
    }

    @Override
    public StoredFile storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY, "头像图片为空");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE,
                    "头像图片不能超过 " + (MAX_AVATAR_SIZE / 1024 / 1024) + "MB");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_AVATAR_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.UNSUPPORTED_TYPE,
                    "头像仅支持 jpg / jpeg / png / webp / gif 格式");
        }
        if (getUsableSpace() < storageProperties.getMinFreeSpace()) {
            throw new BusinessException(ResultCode.DISK_FULL);
        }
        return writeToDisk(file, StorageConstants.AVATAR_DIR, ext);
    }

    @Override
    public StoredFile storeCommentImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY, "评论配图为空");
        }
        if (file.getSize() > MAX_COMMENT_IMAGE_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE,
                    "评论配图不能超过 " + (MAX_COMMENT_IMAGE_SIZE / 1024 / 1024) + "MB");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_COMMENT_IMAGE_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.UNSUPPORTED_TYPE,
                    "评论配图仅支持 jpg / jpeg / png / webp / gif 格式");
        }
        if (getUsableSpace() < storageProperties.getMinFreeSpace()) {
            throw new BusinessException(ResultCode.DISK_FULL);
        }
        return writeToDisk(file, StorageConstants.COMMENT_DIR, ext);
    }

    @Override
    public StoredFile storeEmoji(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY, "表情图片为空");
        }
        if (file.getSize() > MAX_EMOJI_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE, "表情图片不能超过 1MB");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EMOJI_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.UNSUPPORTED_TYPE,
                    "表情仅支持 jpg / jpeg / png / webp / gif 格式");
        }
        if (getUsableSpace() < storageProperties.getMinFreeSpace()) {
            throw new BusinessException(ResultCode.DISK_FULL);
        }
        return writeToDisk(file, StorageConstants.EMOJI_DIR, ext);
    }

    /**
     * 落盘公共逻辑：UUID 重命名 → 日期分片 → 边写盘边计算 MD5
     *
     * @param file   上传文件
     * @param subDir 子目录（audio / lyric / cover）
     * @param ext    小写扩展名（可为空）
     */
    private StoredFile writeToDisk(MultipartFile file, String subDir, String ext) {
        String fileName = IdUtil.fastSimpleUUID() + (ext.isEmpty() ? "" : DOT + ext);
        String datePath = LocalDate.now().format(DATE_DIR_FORMATTER);
        String relativePath = subDir + DATE_SEPARATOR + datePath + DATE_SEPARATOR + fileName;

        Path target = resolveAbsolute(relativePath);
        try {
            Files.createDirectories(target.getParent());

            MessageDigest digest = MessageDigest.getInstance("MD5");
            // 边写盘边计算 MD5：仅遍历一次字节流
            try (InputStream in = file.getInputStream();
                 DigestInputStream digestIn = new DigestInputStream(in, digest);
                 OutputStream out = new BufferedOutputStream(
                         Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = digestIn.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }

            String md5 = HexUtil.encodeHexStr(digest.digest());
            log.debug("文件落盘成功：{} (md5={}, size={})", relativePath, md5, file.getSize());
            return new StoredFile(fileName, relativePath, md5, file.getSize());

        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 算法不可用", e);
            throw new BusinessException(ResultCode.FILE_WRITE_FAILED, "文件校验失败");
        } catch (IOException e) {
            log.error("文件落盘失败：{}", relativePath, e);
            // 清理写了一半的残留文件
            delete(relativePath);
            throw new BusinessException(ResultCode.FILE_WRITE_FAILED);
        }
    }

    @Override
    public Resource loadAsResource(String relativePath) {
        Path path = resolveAbsolute(relativePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            log.warn("文件不存在：{}", relativePath);
            throw new BusinessException(ResultCode.FILE_MISSING);
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(resolveAbsolute(relativePath));
            if (deleted) {
                log.debug("已删除文件：{}", relativePath);
            }
        } catch (Exception e) {
            // 删除失败不影响业务结果，仅记录日志（避免文件异常导致删除事务失败）
            log.warn("删除文件失败（已忽略）：{} - {}", relativePath, e.getMessage());
        }
    }

    @Override
    public Path resolveAbsolute(String relativePath) {
        Path root = rootPath();
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new BusinessException(ResultCode.FILE_MISSING, "文件路径为空");
        }
        // 拒绝绝对路径与上跳路径，防止路径穿越
        String cleaned = relativePath.replace("\\", "/");
        if (cleaned.startsWith("/") || cleaned.contains("..")) {
            log.warn("检测到非法路径访问尝试：{}", relativePath);
            throw new BusinessException(ResultCode.FILE_MISSING, "非法的文件路径");
        }
        Path target = root.resolve(cleaned).normalize();
        if (!target.startsWith(root)) {
            log.warn("检测到路径穿越尝试：{}", relativePath);
            throw new BusinessException(ResultCode.FILE_MISSING, "非法的文件路径");
        }
        return target;
    }

    @Override
    public String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int index = originalFilename.lastIndexOf('.');
        if (index < 0 || index == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    @Override
    public long getUsableSpace() {
        try {
            Path root = rootPath();
            Files.createDirectories(root);
            return Files.getFileStore(root).getUsableSpace();
        } catch (IOException e) {
            // 无法获取时不阻断上传
            log.warn("获取磁盘可用空间失败：{}", e.getMessage());
            return Long.MAX_VALUE;
        }
    }

    @Override
    public long getTotalSpace() {
        try {
            Path root = rootPath();
            Files.createDirectories(root);
            return Files.getFileStore(root).getTotalSpace();
        } catch (IOException e) {
            log.warn("获取磁盘总空间失败：{}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 存储根目录（绝对、规范化）
     * <p>
     * 配置为绝对路径时直接使用；配置为相对路径时，以「编译产物所在的项目目录」为基准解析，
     * 避免因启动方式不同（IDEA / 命令行）导致工作目录不同而找不到文件：
     * - IDEA 与 Maven 运行时类文件位于 {@code <项目>/target/classes}
     * - 打包运行时类文件位于 {@code <项目>/target/xxx.jar}
     * 两种情况都取上两级目录即可得到项目目录（music-server）。
     */
    private Path rootPath() {
        Path configured = Paths.get(storageProperties.getRootPath());
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        // 候选一：项目目录（不依赖启动时的工作目录）
        Path fromProject = projectDir().resolve(configured).normalize();
        if (Files.exists(fromProject)) {
            return fromProject;
        }
        // 候选二：当前工作目录（兼容直接在 music-server 下启动的场景）
        Path fromWorkDir = Paths.get(System.getProperty("user.dir")).resolve(configured).normalize();
        if (Files.exists(fromWorkDir)) {
            return fromWorkDir;
        }
        // 都不存在（首次运行）：用项目目录，后续会自动创建
        return fromProject;
    }

    /**
     * 解析项目目录；失败时回退到当前工作目录
     * <p>
     * 类文件位置有两种形态，取「上两级」都正好是项目目录：
     * - IDE / Maven 运行：{@code <项目>/target/classes} → {@code <项目>}
     * - 打包运行：{@code <项目>/target/xxx.jar} → {@code <项目>}
     */
    private Path projectDir() {
        try {
            URL location = FileStorageServiceImpl.class.getProtectionDomain().getCodeSource().getLocation();
            // 关键修复：Spring Boot 打包运行（fat jar）时 location 形如
            //   jar:file:/D:/.../music-server/target/music-server-1.0.0.jar!/BOOT-INF/classes!/
            // 这种嵌套 URL 不能直接 Paths.get(uri)（会抛异常 → 旧代码因此回退到"当前工作目录"，
            // 导致项目换位置后 storage 找不到）。这里先剥掉 jar: / file: / "!/..." 只留真实文件路径。
            String raw = location.toString();
            int bang = raw.indexOf("!/");
            if (bang >= 0) {
                raw = raw.substring(0, bang);
            }
            if (raw.startsWith("jar:")) {
                raw = raw.substring("jar:".length());
            }
            if (raw.startsWith("file:")) {
                raw = raw.substring("file:".length());
            }
            // Windows 下形如 /D:/xxx → 去掉开头的斜杠
            if (raw.length() > 2 && raw.charAt(0) == '/' && raw.charAt(2) == ':') {
                raw = raw.substring(1);
            }
            Path locationPath = Paths.get(raw);
            Path dir = Files.isDirectory(locationPath)
                    ? locationPath                                   // …/target/classes 或 …/target
                    : locationPath.getParent();                      // …/target/xxx.jar
            // 自下而上找"项目目录"标记：pom.xml 或 storage 目录
            // → 项目整体搬到任何盘符/目录都成立，不依赖启动时的工作目录
            for (Path cursor = dir; cursor != null; cursor = cursor.getParent()) {
                if (Files.exists(cursor.resolve("pom.xml")) || Files.exists(cursor.resolve("storage"))) {
                    return cursor;
                }
            }
            // 兜底：沿用旧的"上两级"约定
            Path targetDir = dir;
            if (targetDir != null && targetDir.getParent() != null) {
                return targetDir.getParent();
            }
        } catch (Exception e) {
            log.warn("解析项目目录失败，回退到当前工作目录：{}", e.getMessage());
        }
        return Paths.get(System.getProperty("user.dir"));
    }
}
