package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 本地导入请求（文件绝对路径列表）
 */
@Data
public class LocalImportDTO {

    @NotEmpty(message = "请选择要导入的文件")
    @Size(max = 2000, message = "单次导入不能超过 2000 个文件")
    private List<String> paths;
}
