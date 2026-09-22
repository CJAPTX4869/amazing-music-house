package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 批量删除请求
 * <p>
 * 使用 POST 提交（部分代理/客户端会丢弃 DELETE 的请求体）。
 */
@Data
public class BatchDeleteDTO {

    @NotEmpty(message = "请至少选择一条记录")
    @Size(max = 100, message = "单次最多删除 100 条")
    private List<Long> ids;
}
