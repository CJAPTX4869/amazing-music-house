package com.example.music;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 音乐文件管理系统 - 后端启动类
 *
 * @author music-server
 */
@SpringBootApplication
@MapperScan("com.example.music.mapper")
// @EnableAsync 已移至 AsyncConfig（含线程池配置）
@EnableScheduling   // 孤儿文件清理、磁盘监控定时任务
public class MusicApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicApplication.class, args);
    }
}
