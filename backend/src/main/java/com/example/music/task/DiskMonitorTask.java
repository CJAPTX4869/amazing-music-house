package com.example.music.task;

import com.example.music.service.FileStorageService;
import com.example.music.utils.FormatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 磁盘空间监控任务
 * <p>
 * 本地磁盘存储的最大风险是写满导致服务不可用，需提前告警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiskMonitorTask {

    /** 告警阈值：85% */
    private static final double WARN_RATIO = 0.85;

    /** 严重阈值：95% */
    private static final double ERROR_RATIO = 0.95;

    private final FileStorageService fileStorageService;

    /**
     * 每 30 分钟检查一次
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void checkDiskSpace() {
        long total = fileStorageService.getTotalSpace();
        long usable = fileStorageService.getUsableSpace();
        if (total <= 0) {
            return;
        }

        long used = total - usable;
        double ratio = (double) used / total;
        String detail = "磁盘使用率 " + String.format("%.1f%%", ratio * 100)
                + "（已用 " + FormatUtils.fileSize(used) + " / 总 " + FormatUtils.fileSize(total)
                + "，剩余 " + FormatUtils.fileSize(usable) + "）";

        if (ratio >= ERROR_RATIO) {
            log.error("磁盘空间严重不足，已拒绝新上传：{}", detail);
        } else if (ratio >= WARN_RATIO) {
            log.warn("磁盘空间告警：{}", detail);
        } else {
            log.debug("磁盘空间正常：{}", detail);
        }
    }
}
