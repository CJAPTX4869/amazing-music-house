package com.example.music.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.music.constant.StorageConstants;
import com.example.music.entity.Song;
import com.example.music.mapper.SongMapper;
import com.example.music.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 孤儿文件清理任务
 * <p>
 * 孤儿文件 = 磁盘上存在但数据库无对应记录的音频文件（通常由上传中断、异常回滚不彻底产生）。
 * <p>
 * 安全设计：默认<b>只扫描并告警，不删除</b>；确认无误后可通过
 * {@code app.storage.orphan-clean-enabled=true} 开启自动删除。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanFileCleanTask {

    /** 是否真正删除孤儿文件（默认关闭，防止误删） */
    @Value("${app.storage.orphan-clean-enabled:false}")
    private boolean cleanEnabled;

    private final SongMapper songMapper;
    private final FileStorageService fileStorageService;

    /**
     * 每天凌晨 3 点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scanOrphanFiles() {
        log.info("开始扫描孤儿文件（自动清理开关：{}）", cleanEnabled);

        // 1. 收集数据库中所有有效文件路径
        Set<String> knownPaths = songMapper
                .selectList(new LambdaQueryWrapper<Song>().select(Song::getFilePath))
                .stream()
                .map(Song::getFilePath)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. 遍历磁盘上的音频文件
        Path audioRoot;
        try {
            audioRoot = fileStorageService.resolveAbsolute(StorageConstants.AUDIO_DIR);
        } catch (Exception e) {
            log.warn("存储目录不可用，跳过孤儿文件扫描：{}", e.getMessage());
            return;
        }
        if (!Files.isDirectory(audioRoot)) {
            log.info("音频目录尚不存在，跳过扫描");
            return;
        }

        Path storageRoot = audioRoot.getParent();
        if (storageRoot == null) {
            // 极端配置下（如 root-path 被设为磁盘根目录）getParent() 可能为 null
            log.warn("无法解析存储根目录，跳过本次孤儿文件扫描：{}", audioRoot);
            return;
        }
        int orphanCount = 0;

        try (Stream<Path> stream = Files.walk(audioRoot)) {
            List<Path> files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path file : files) {
                String relativePath = storageRoot.relativize(file).toString().replace("\\", "/");
                if (!knownPaths.contains(relativePath)) {
                    orphanCount++;
                    if (cleanEnabled) {
                        fileStorageService.delete(relativePath);
                        log.info("已清理孤儿文件：{}", relativePath);
                    } else {
                        log.warn("发现孤儿文件（未删除）：{}", relativePath);
                    }
                }
            }
        } catch (IOException e) {
            log.error("孤儿文件扫描失败", e);
            return;
        }

        log.info("孤儿文件扫描完成：磁盘文件 {} 个，孤儿 {} 个，{}",
                knownPaths.size(), orphanCount, cleanEnabled ? "已清理" : "未清理（仅告警）");
    }
}
