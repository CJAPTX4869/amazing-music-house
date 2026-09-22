package com.example.music.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一分页响应结构
 *
 * @param <T> 记录类型
 */
@Data
public class PageVO<T> {

    private List<T> records;
    private Long total;
    private Long page;
    private Long size;

    /**
     * 由 MyBatis-Plus 分页对象直接转换（元素类型一致）
     */
    public static <T> PageVO<T> of(IPage<T> page) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(page.getRecords());
        vo.setTotal(page.getTotal());
        vo.setPage(page.getCurrent());
        vo.setSize(page.getSize());
        return vo;
    }

    /**
     * 由分页对象转换，并将实体逐条映射为 VO
     *
     * @param page      分页结果
     * @param converter 实体 -> VO 的转换函数
     */
    public static <E, T> PageVO<T> of(IPage<E> page, Function<E, T> converter) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(page.getRecords() == null
                ? Collections.emptyList()
                : page.getRecords().stream().map(converter).collect(Collectors.toList()));
        vo.setTotal(page.getTotal());
        vo.setPage(page.getCurrent());
        vo.setSize(page.getSize());
        return vo;
    }
}
