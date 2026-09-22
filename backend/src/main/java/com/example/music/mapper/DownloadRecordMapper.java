package com.example.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.music.entity.DownloadRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 下载记录 Mapper
 */
@Mapper
public interface DownloadRecordMapper extends BaseMapper<DownloadRecord> {
}
