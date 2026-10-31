package com.example.music.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 歌曲上传请求（multipart/form-data）
 * <p>
 * 元数据字段均为选填：留空时优先取 ID3 标签，其次取文件名。
 */
@Data
public class SongUploadDTO {

    @NotNull(message = "请选择要上传的文件")
    private MultipartFile file;

    @Size(max = 200, message = "歌曲名长度不能超过 200 个字符")
    private String title;

    @Size(max = 100, message = "歌手长度不能超过 100 个字符")
    private String artist;

    @Size(max = 100, message = "专辑长度不能超过 100 个字符")
    private String album;

    @Size(max = 50, message = "流派长度不能超过 50 个字符")
    /** 标签（逗号分隔，最多 3 个） */
    private String tags;

    /** 前端预计算的 MD5（用于秒传判断，可选） */
    private String md5;
}
