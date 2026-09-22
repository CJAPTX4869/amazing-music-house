package com.example.music.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件存储配置（前缀 app.storage）
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** 存储根目录（相对项目运行目录或绝对路径） */
    private String rootPath = "./storage";

    /** 单文件大小上限（字节），默认 100MB */
    private long maxFileSize = 100L * 1024 * 1024;

    /** 剩余磁盘空间低于该值拒绝上传（字节），默认 1GB */
    private long minFreeSpace = 1024L * 1024 * 1024;

    /** 允许的扩展名（小写，不含点） */
    private List<String> allowedExtensions = new ArrayList<>();

    /** 允许的 MIME 类型 */
    private List<String> allowedMimeTypes = new ArrayList<>();
}
