package com.example.music.controller;

import com.example.music.common.response.Result;
import com.example.music.security.PublicApi;
import com.example.music.security.RequireAdmin;
import com.example.music.security.RequireLogin;
import com.example.music.service.EmojiService;
import com.example.music.vo.EmojiVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 表情包接口（服务端统一管理：管理员上传，用户端只消费）
 * <p>
 * 清单与图片为匿名可访问（评论输入框对未登录用户同样展示表情面板；
 * 发表评论时才要求登录），图片直出带长缓存（表情内容不变，删除即换新 id）。
 */
@Slf4j
@RestController
@RequestMapping("/emojis")
@RequiredArgsConstructor
@RequireLogin
public class EmojiController {

    /** 表情缓存策略：图片不可变（内容永不覆盖），长缓存 */
    private static final String EMOJI_CACHE_CONTROL = "public, max-age=604800";

    private final EmojiService emojiService;

    /**
     * 全量表情清单（评论表情面板数据源）
     */
    @PublicApi
    @GetMapping
    public Result<List<EmojiVO>> list() {
        return Result.success(emojiService.listAll());
    }

    /**
     * 读取表情图片（公开；不存在返回 404）
     */
    @PublicApi
    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        Resource resource = emojiService.getImageResource(id);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(resolveImageMediaType(resource))
                .header(HttpHeaders.CACHE_CONTROL, EMOJI_CACHE_CONTROL)
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    /**
     * 管理员上传表情（name 为评论占位符 [name]；静态图统一缩放 96×96）
     */
    @RequireAdmin
    @PostMapping
    public Result<EmojiVO> upload(@RequestParam("name") String name,
                                  @RequestParam("file") MultipartFile file) {
        return Result.success("表情上传成功", emojiService.upload(name, file));
    }

    private MediaType resolveImageMediaType(Resource resource) {
        String filename = resource.getFilename();
        if (StringUtils.hasText(filename)) {
            return MediaTypeFactory.getMediaType(filename)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
