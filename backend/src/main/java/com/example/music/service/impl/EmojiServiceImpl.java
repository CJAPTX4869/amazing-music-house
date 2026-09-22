package com.example.music.service.impl;

import cn.hutool.core.img.Img;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.ResultCode;
import com.example.music.entity.Emoji;
import com.example.music.mapper.EmojiMapper;
import com.example.music.service.EmojiService;
import com.example.music.service.FileStorageService;
import com.example.music.vo.EmojiVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 表情包服务实现（服务端统一管理：管理员上传，用户端只消费）
 * <p>
 * 统一规格策略：静态图上传后缩放到 96×96（GIF 跳过缩放以保留动画，
 * 展示尺寸由前端 CSS 约束）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmojiServiceImpl implements EmojiService {

    /** 表情名长度上限 */
    private static final int MAX_NAME_LENGTH = 20;

    /** 统一缩放尺寸（长宽） */
    private static final int EMOJI_STANDARD_SIZE = 96;

    private final EmojiMapper emojiMapper;
    private final FileStorageService fileStorageService;

    @Override
    public List<EmojiVO> listAll() {
        return emojiMapper.selectList(new LambdaQueryWrapper<Emoji>()
                        .orderByAsc(Emoji::getSort))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public EmojiVO upload(String name, MultipartFile file) {
        String trimmed = name == null ? "" : name.trim()
                .replace("[", "").replace("]", "");
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "表情名需为 1-" + MAX_NAME_LENGTH + " 个字符");
        }
        Long duplicated = emojiMapper.selectCount(new LambdaQueryWrapper<Emoji>()
                .eq(Emoji::getName, trimmed));
        if (duplicated != null && duplicated > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表情名已存在：" + trimmed);
        }

        FileStorageService.StoredFile stored = fileStorageService.storeEmoji(file);
        normalizeSize(stored);

        Emoji emoji = new Emoji();
        emoji.setName(trimmed);
        emoji.setPath(stored.getRelativePath());
        emoji.setSort(nextSort());
        try {
            emojiMapper.insert(emoji);
        } catch (DuplicateKeyException e) {
            // 与前置 selectCount 检查之间有并发窗口：唯一约束兜底，落盘文件回收
            fileStorageService.delete(stored.getRelativePath());
            throw new BusinessException(ResultCode.PARAM_ERROR, "表情名已存在：" + trimmed);
        }

        log.info("表情上传成功：name={}, path={}", trimmed, stored.getRelativePath());
        return toVO(emoji);
    }

    @Override
    public Resource getImageResource(Long id) {
        Emoji emoji = emojiMapper.selectById(id);
        if (emoji == null || !StringUtils.hasText(emoji.getPath())) {
            return null;
        }
        try {
            return fileStorageService.loadAsResource(emoji.getPath());
        } catch (BusinessException e) {
            // 记录存在但文件已丢失：按无表情处理，避免前端收到 500
            log.warn("表情文件缺失：id={}, path={}", id, emoji.getPath());
            return null;
        }
    }

    /**
     * 静态图统一缩放到 96×96；GIF 保留原样（缩放会丢失动画帧）
     */
    private void normalizeSize(FileStorageService.StoredFile stored) {
        String path = stored.getRelativePath();
        String ext = extractExtension(path);
        if ("gif".equals(ext)) {
            return;
        }
        try {
            File target = fileStorageService.resolveAbsolute(path).toFile();
            // 流式 API：读入内存 → 缩放到统一规格 → 写回原文件（格式按扩展名推断）
            Img.from(target).scale(EMOJI_STANDARD_SIZE, EMOJI_STANDARD_SIZE).write(target);
        } catch (Exception e) {
            // 缩放失败不阻断上传（表情仍可用，仅规格不统一），记录日志便于排查
            log.warn("表情规格化失败（保留原图）：path={}", path, e);
        }
    }

    private int nextSort() {
        List<Emoji> all = emojiMapper.selectList(new LambdaQueryWrapper<Emoji>().select(Emoji::getSort));
        return all.stream()
                .map(Emoji::getSort)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(max -> max + 1)
                .orElse(1);
    }

    private String extractExtension(String relativePath) {
        int dot = relativePath.lastIndexOf('.');
        return dot < 0 ? "" : relativePath.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private EmojiVO toVO(Emoji emoji) {
        EmojiVO vo = new EmojiVO();
        vo.setId(emoji.getId());
        vo.setName(emoji.getName());
        vo.setImageUrl("/emojis/" + emoji.getId() + "/image");
        return vo;
    }
}
