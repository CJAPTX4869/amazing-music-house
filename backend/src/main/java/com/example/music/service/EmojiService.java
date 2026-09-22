package com.example.music.service;

import com.example.music.vo.EmojiVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 表情包服务（服务端统一管理：管理员上传，用户端只消费）
 */
public interface EmojiService {

    /**
     * 全量表情清单（按 sort 升序，评论表情面板数据源）
     */
    List<EmojiVO> listAll();

    /**
     * 管理员上传表情：静态图统一缩放到 96×96（GIF 跳过缩放以保留动画）；
     * name 即评论占位符 [name]，重复名拦截
     */
    EmojiVO upload(String name, MultipartFile file);

    /**
     * 读取表情图片资源（表情不存在或文件缺失返回 null）
     */
    Resource getImageResource(Long id);
}
