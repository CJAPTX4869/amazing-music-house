package com.example.music.controller.legacy;

import com.example.music.common.exception.BusinessException;
import com.example.music.security.CaptchaTicketManager;
import com.example.music.security.OnlineClientTracker;
import com.example.music.common.response.ResultCode;
import com.example.music.dto.LoginDTO;
import com.example.music.dto.PlaylistSaveDTO;
import com.example.music.dto.SongQueryDTO;
import com.example.music.entity.Song;
import com.example.music.security.RequireLogin;
import com.example.music.security.UserContext;
import com.example.music.service.PlaylistService;
import com.example.music.service.SongService;
import com.example.music.service.UserService;
import com.example.music.utils.LegacyConvertUtils;
import com.example.music.vo.LoginVO;
import com.example.music.vo.PageVO;
import com.example.music.vo.PlaylistVO;
import com.example.music.vo.SongVO;
import com.example.music.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 旧前端兼容接口（翻译层）
 * <p>
 * <b>设计说明（重要）</b>：
 * <ol>
 *   <li>本类承接旧前端 {@code vue3-cloud-music-master} 的原有调用路径，
 *       <b>路径与响应结构沿用其对接网易云 API 时的约定</b>，使旧前端代码无需改动即可切换数据源；</li>
 *   <li>本类是**唯一允许不返回统一响应体 {@code Result<T>} 的例外**（旧前端按网易云结构解析，
 *       强行统一会导致其 45 个组件全部需要修改）；</li>
 *   <li>内部**统一转调既有 Service**，业务逻辑不在此重复实现；</li>
 *   <li>权限与规范接口保持一致：需要登录的方法标注 {@link RequireLogin}，
 *       同样经过 JWT 拦截器校验，不因"兼容"而放松。</li>
 * </ol>
 *
 * @see com.example.music.utils.LegacyConvertUtils 字段映射（含三个必踩的坑）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LegacyController {

    private static final int CODE_OK = 200;

    /** 搜索建议返回条数上限 */
    private static final int SUGGEST_LIMIT = 10;

    private final SongService songService;
    private final PlaylistService playlistService;
    private final UserService userService;
    private final CaptchaTicketManager captchaTickets;
    private final com.example.music.mapper.PlaylistMapper playlistMapper;
    private final OnlineClientTracker onlineTracker;

    /* ==================== 播放核心链路 ==================== */

    /**
     * 播放可用性检查
     * <p>
     * 旧前端逻辑：{@code if (!checkRes.musicSuccess && !checkRes?.data?.success) 跳过该曲}，
     * 故只要响应体含 {@code success: true} 即通过。
     */
    @GetMapping("/check/music")
    public Map<String, Object> checkMusic(@RequestParam("id") Long id) {
        try {
            songService.getPlayableSong(id);
            return map("success", true, "message", "ok");
        } catch (BusinessException e) {
            return map("success", false, "message", e.getMessage());
        }
    }

    /**
     * 获取播放地址
     * <p>
     * ⚠️ 返回的 url **不含任何 query 参数**：旧前端会执行
     * {@code res.data.data[0].url + '?id=' + id}，带参数会拼出畸形地址。
     */
    @GetMapping("/song/url/v1")
    public Map<String, Object> songUrl(@RequestParam("id") Long id, HttpServletRequest request) {
        try {
            Song song = songService.getPlayableSong(id);
            // V3.1：data[0] 升级为完整歌曲映射（name/ar/al/likeCount/commentCount…），
            // 供评论页信息条等场景复用；url 字段保持本方法语义（绝对地址、不含 query）
            Map<String, Object> item = LegacyConvertUtils.song(SongVO.of(song), baseUrl(request));
            item.put("id", id);
            item.put("url", baseUrl(request) + "/api/songs/" + id + "/stream");
            item.put("br", 320000);
            item.put("size", song.getFileSize());
            return map("code", CODE_OK, "data", Collections.singletonList(item));
        } catch (BusinessException e) {
            // 旧前端会判断 data[0].url 是否存在，返回 url=null 即可安全降级（自动跳过该曲）
            log.debug("播放地址不可用：songId={}, msg={}", id, e.getMessage());
            return map("code", CODE_OK, "data",
                    Collections.singletonList(map("id", id, "url", null)));
        }
    }

    /**
     * 歌词（旧前端格式）
     * <p>
     * 无歌词时返回空串：旧前端据此置 {@code isNotLyric = true} 并显示"暂无歌词"。
     * {@code yrc}/{@code tlyric} 恒为 null（逐字歌词依赖网易云专有格式，本项目不支持）。
     */
    @GetMapping("/lyric/new")
    public Map<String, Object> lyricNew(@RequestParam("id") Long id) {
        String lyric = "";
        try {
            lyric = songService.getLyricContent(id);
        } catch (BusinessException e) {
            log.debug("歌词读取失败（按空歌词返回）：songId={}, msg={}", id, e.getMessage());
        }
        Map<String, Object> lrc = new LinkedHashMap<>();
        lrc.put("lyric", lyric);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("code", CODE_OK);
        res.put("lrc", lrc);
        res.put("tlyric", null);
        res.put("yrc", null);
        return res;
    }

    /* ==================== 歌曲列表 / 搜索 ==================== */

    /**
     * 新歌速递（映射为自有歌曲列表，按创建时间倒序）
     * <p>
     * 属于「翻库」入口（不带任何条件就能列出整库），按「未登录只能搜索+单曲播放」的口径要求登录。
     */
    @RequireLogin
    /** 播放量最高 30 首（发现页"推荐歌曲"实时数据源） */
    @GetMapping("/top/plays")
    public Map<String, Object> topPlays(@RequestParam(defaultValue = "30") Integer limit,
                                        HttpServletRequest request) {
        SongQueryDTO query = new SongQueryDTO();
        query.setPage(1);
        query.setSize(Math.min(Math.max(limit, 1), 50));
        query.setSort("play_count,desc,comment_count,desc,id,asc");
        PageVO<SongVO> page = songService.page(query, false);
        return map("code", CODE_OK, "data",
                LegacyConvertUtils.songs(page.getRecords(), baseUrl(request)));
    }

    /** 评论数最高 20 首（发现页"推荐歌曲"实时数据源） */
    @GetMapping("/top/comments")
    public Map<String, Object> topComments(@RequestParam(defaultValue = "20") Integer limit,
                                           HttpServletRequest request) {
        SongQueryDTO query = new SongQueryDTO();
        query.setPage(1);
        query.setSize(Math.min(Math.max(limit, 1), 50));
        query.setSort("comment_count,desc,play_count,desc,id,asc");
        PageVO<SongVO> page = songService.page(query, false);
        return map("code", CODE_OK, "data",
                LegacyConvertUtils.songs(page.getRecords(), baseUrl(request)));
    }

    @GetMapping("/top/song")
    public Map<String, Object> topSong(@RequestParam(value = "type", required = false) Integer type,
                                       @RequestParam(value = "limit", defaultValue = "20") Integer limit,
                                       @RequestParam(value = "genre", required = false) String genre,
                                       @RequestParam(value = "category", required = false) String category,
                                       HttpServletRequest request) {
        SongQueryDTO query = new SongQueryDTO();
        query.setPage(1);
        // category=latest100（或 全部 tab）：取最新 100 首；其他 tab 按类型标签筛选
        boolean latest100 = "latest100".equals(category);
        query.setSize(latest100 ? 100 : Math.min(Math.max(limit, 1), 50));
        query.setSort("create_time,desc");
        query.setTag(genre);
        PageVO<SongVO> page = songService.page(query, false);
        return map("code", CODE_OK, "data",
                LegacyConvertUtils.songs(page.getRecords(), baseUrl(request)));
    }

    /**
     * 搜索（V3.1：type=1000 歌单搜索按名模糊 + offset 翻页；其余按单曲搜索）
     */
    @GetMapping("/cloudsearch")
    public Map<String, Object> cloudSearch(@RequestParam("keywords") String keywords,
                                           @RequestParam(value = "type", defaultValue = "1") Integer type,
                                           @RequestParam(value = "limit", defaultValue = "30") Integer limit,
                                           @RequestParam(value = "offset", defaultValue = "0") Integer offset,
                                           HttpServletRequest request) {
        int size = Math.min(Math.max(limit, 1), 100);
        int pageNo = Math.max(offset, 0) / size + 1;

        // 空关键词必须返回空态：底层 page 查询在无关键词时是「不带条件」的，
        // 会把整库当成搜索结果吐出去（实测 keywords= 空或一个空格 → songCount=208 全库），
        // 这等于给「未登录不能翻库」开了个后门。与 /search/suggest 的处理保持一致。
        if (!StringUtils.hasText(keywords)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("songs", Collections.emptyList());
            empty.put("songCount", 0);
            empty.put("playlists", Collections.emptyList());
            empty.put("playlistCount", 0);
            return map("code", CODE_OK, "result", empty);
        }

        // 歌单搜索（网易云 type=1000）：仅公开歌单，按歌单名模糊
        if (Integer.valueOf(1000).equals(type)) {
            PageVO<PlaylistVO> page = playlistService.searchPublic(keywords, pageNo, size);
            List<Map<String, Object>> playlists = page.getRecords().stream()
                    .map(p -> LegacyConvertUtils.playlist(p, null, baseUrl(request)))
                    .collect(Collectors.toList());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("playlists", playlists);
            result.put("playlistCount", page.getTotal());
            result.put("songs", Collections.emptyList());
            result.put("songCount", 0);
            return map("code", CODE_OK, "result", result);
        }

        // 单曲搜索（type=1 及其余类型）
        SongQueryDTO query = new SongQueryDTO();
        query.setKeyword(keywords);
        query.setPage(pageNo);
        query.setSize(size);
        PageVO<SongVO> page = songService.page(query, false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("songs", LegacyConvertUtils.songs(page.getRecords(), baseUrl(request)));
        result.put("songCount", page.getTotal());
        result.put("playlists", Collections.emptyList());
        return map("code", CODE_OK, "result", result);
    }

    /**
     * 搜索建议（映射为自有歌曲搜索，供搜索框下拉直接点播）
     * <p>
     * 无关键词时返回空态；歌单/歌手建议本期无数据源，恒为空数组（旧前端已做兜底）。
     */
    @GetMapping("/search/suggest")
    public Map<String, Object> searchSuggest(
            @RequestParam(value = "keywords", required = false) String keywords,
            HttpServletRequest request) {
        List<Map<String, Object>> songs = Collections.emptyList();
        if (StringUtils.hasText(keywords)) {
            SongQueryDTO query = new SongQueryDTO();
            query.setKeyword(keywords);
            query.setPage(1);
            query.setSize(SUGGEST_LIMIT);
            songs = LegacyConvertUtils.songs(songService.page(query, false).getRecords(),
                    baseUrl(request));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("songs", songs);
        result.put("playlists", Collections.emptyList());
        result.put("artists", Collections.emptyList());
        return map("code", CODE_OK, "result", result);
    }

    /* ==================== 歌单 ==================== */

    /**
     * 歌单详情（含歌曲；不校验归属，供旧前端展示任意歌单）
     * <p>
     * 必须要求登录：「我喜欢的音乐」这类系统歌单本身是公开属性，
     * 匿名可查等于把每个用户的收藏列表摊开（实测 id=9 能匿名读到 26 首）。
     */
    @RequireLogin
    @GetMapping("/playlist/detail")
    public Map<String, Object> playlistDetail(@RequestParam("id") Long id,
                                              HttpServletRequest request) {
        PlaylistVO vo = playlistService.detailPublic(id);
        return map("code", CODE_OK, "playlist",
                LegacyConvertUtils.playlist(vo, vo.getSongs(), baseUrl(request)));
    }

    /**
     * 歌单全部歌曲（同 {@link #playlistDetail}，登录后可见）
     */
    @RequireLogin
    @GetMapping("/playlist/track/all")
    public Map<String, Object> playlistTrackAll(@RequestParam("id") Long id,
                                                HttpServletRequest request) {
        PlaylistVO vo = playlistService.detailPublic(id);
        return map("code", CODE_OK, "songs",
                LegacyConvertUtils.songs(vo.getSongs(), baseUrl(request)));
    }

    /**
     * 新建歌单
     */
    @GetMapping("/playlist/create")
    @RequireLogin
    public Map<String, Object> playlistCreate(@RequestParam("name") String name) {
        // GET 参数绕过了 PlaylistSaveDTO 的 @Valid，这里手动对齐其约束（非空、≤100 字符）
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌单名不能为空");
        }
        if (name.length() > 100) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌单名不能超过 100 字符");
        }
        PlaylistSaveDTO dto = new PlaylistSaveDTO();
        dto.setName(name);
        PlaylistVO vo = playlistService.create(dto, UserContext.getUserId());
        return map("code", CODE_OK, "playlist", LegacyConvertUtils.playlist(vo, null));
    }

    /**
     * 歌单增删歌曲
     *
     * @param op add-添加 / del-移除
     */
    @GetMapping("/playlist/tracks")
    @RequireLogin
    public Map<String, Object> playlistTracks(@RequestParam("pid") Long playlistId,
                                              @RequestParam("tracks") Long songId,
                                              @RequestParam("op") String op) {
        Long userId = UserContext.getUserId();
        if ("del".equalsIgnoreCase(op)) {
            playlistService.removeSong(playlistId, songId, userId);
        } else {
            playlistService.addSong(playlistId, songId, userId);
        }
        return map("code", CODE_OK);
    }

    /**
     * 我的歌单列表
     */
    @GetMapping("/user/playlist")
    @RequireLogin
    @SuppressWarnings("unchecked")
    public Map<String, Object> userPlaylist(@RequestParam(value = "uid", required = false) Long uid) {
        Long viewerId = UserContext.getUserId();
        // V3.1：uid 为空取当前用户，否则查看目标用户主页；
        // 返回「我喜欢的音乐 + 创建的 + 收藏的」合并列表（收藏项带 subscribed 标记，
        // 前端 classifySongsList 按 creator.userId 归入"创建/收藏"分组）
        Long targetId = uid != null ? uid : viewerId;
        Map<String, Object> groups = playlistService.userPlaylists(targetId, viewerId);
        List<Map<String, Object>> out = new ArrayList<>();
        Object favorite = groups.get("favorite");
        if (favorite != null) {
            out.add(LegacyConvertUtils.playlist((PlaylistVO) favorite, null));
        }
        ((List<PlaylistVO>) groups.get("created"))
                .forEach(p -> out.add(LegacyConvertUtils.playlist(p, null)));
        ((List<PlaylistVO>) groups.get("collected"))
                .forEach(p -> {
                    Map<String, Object> m = LegacyConvertUtils.playlist(p, null);
                    m.put("subscribed", true);
                    out.add(m);
                });
        return map("code", CODE_OK, "playlist", out, "more", false);
    }

    /**
     * 推荐歌单（映射为自有公开歌单）
     * <p>
     * 也是「翻库」入口之一（一次列出全站歌单，含各人的「我喜欢的音乐」），按口径要求登录。
     */
    @RequireLogin
    /** 歌单播放计数（播放全部按钮触发；推荐歌单"高播放"排序数据源） */
    @GetMapping("/playlist/play")
    public Map<String, Object> playlistPlay(@RequestParam(value = "id") Long id) {
        playlistMapper.incrementPlayCount(id);
        return map("code", CODE_OK);
    }

    @GetMapping("/personalized")
    public Map<String, Object> personalized(
            @RequestParam(value = "limit", defaultValue = "15") Integer limit) {
        // V3.3 推荐歌单规则：最新创建 1 个 + 高播放量 2 个（去重后合并，最多 3 个）
        List<PlaylistVO> latest = playlistService.listPublic(1);
        List<PlaylistVO> hot = playlistService.listPublicByPlays(2);
        List<PlaylistVO> merged = new java.util.ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (PlaylistVO p : latest) {
            if (seen.add(p.getId())) merged.add(p);
        }
        for (PlaylistVO p : hot) {
            if (seen.add(p.getId())) merged.add(p);
        }
        List<Map<String, Object>> result = merged.stream()
                .map(p -> LegacyConvertUtils.playlist(p, null))
                .collect(Collectors.toList());
        return map("code", CODE_OK, "result", result, "hasTaste", false);
    }

    /* ==================== 我喜欢的音乐 ==================== */

    /**
     * 我喜欢的音乐 — 歌曲 ID 列表
     */
    @GetMapping("/likelist")
    @RequireLogin
    public Map<String, Object> likeList(@RequestParam(value = "uid", required = false) Long uid) {
        PlaylistVO favorite = playlistService.getFavoriteList(UserContext.getUserId());
        List<Long> ids = favorite.getSongs() == null
                ? Collections.emptyList()
                : favorite.getSongs().stream().map(SongVO::getId).collect(Collectors.toList());
        return map("code", CODE_OK, "ids", ids, "uid", UserContext.getUserId());
    }

    /**
     * 喜欢 / 取消喜欢
     */
    @GetMapping("/like")
    @RequireLogin
    public Map<String, Object> like(@RequestParam("id") Long songId,
                                    @RequestParam(value = "like", defaultValue = "true") Boolean like) {
        Long userId = UserContext.getUserId();
        if (Boolean.TRUE.equals(like)) {
            playlistService.favorite(songId, userId);
        } else {
            playlistService.unfavorite(songId, userId);
        }
        return map("code", CODE_OK);
    }

    /* ==================== 登录 / 账号 ==================== */

    /**
     * 登录（用户名 + 密码）
     * <p>
     * 旧前端登录组件改造后调用本接口，成功后自行保存 {@code token} 并在请求头携带。
     */
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        // 票据选填校验：听歌端登录带票（服务端校验并一次性消费）；Qt/安卓不带票保持兼容。
        // 客户端既然提交了票据，就必须是真票——假票/过期票/重放一律 403，绝不静默放行。
        if (dto.getTicket() != null && !dto.getTicket().isBlank()
                && !captchaTickets.consumeForLogin(dto.getTicket())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "请先完成抓娃娃人机验证");
        }
        LoginVO vo = userService.login(dto);
        UserVO user = vo.getUser();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("code", CODE_OK);
        res.put("token", vo.getToken());
        res.put("profile", LegacyConvertUtils.profile(user, baseUrl(request)));
        res.put("account", map("id", user.getId(), "userName", user.getUsername()));
        return res;
    }

    /**
     * 登出（无服务端状态，前端清除本地 token 即可）
     */
    @GetMapping("/logout")
    public Map<String, Object> logout() {
        // 已登录则立即从"在线客户端"下线（否则要等 60 秒 TTL 自然过期）
        if (UserContext.isLogin()) {
            onlineTracker.removeUser(UserContext.getUserId());
        }
        return map("code", CODE_OK);
    }

    /**
     * 登录状态（匿名可访问；未登录时 data 为空对象）
     */
    @GetMapping("/login/status")
    public Map<String, Object> loginStatus(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (UserContext.isLogin()) {
            try {
                UserVO user = userService.getProfile(UserContext.getUserId());
                data.put("profile", LegacyConvertUtils.profile(user, baseUrl(request)));
                data.put("account", map("id", user.getId(), "userName", user.getUsername()));
            } catch (BusinessException e) {
                log.debug("登录态查询失败：{}", e.getMessage());
            }
        }
        return map("code", CODE_OK, "data", data);
    }

    /**
     * 账号信息
     */
    @GetMapping("/user/account")
    @RequireLogin
    public Map<String, Object> userAccount(HttpServletRequest request) {
        UserVO user = userService.getProfile(UserContext.getUserId());
        return map("code", CODE_OK, "profile", LegacyConvertUtils.profile(user, baseUrl(request)));
    }

    /* ==================== 空态兜底（无数据源功能） ==================== */

    /**
     * 轮播图（无运营数据源，返回空数组，旧前端自动隐藏）
     */
    @GetMapping("/banner")
    public Map<String, Object> banner() {
        return map("code", CODE_OK, "banners", Collections.emptyList());
    }

    /* ==================== 内部工具 ==================== */

    /**
     * 服务基础地址（供拼接音频绝对地址，播放器 <audio> 需要可直接访问的 URL）
     */
    private String baseUrl(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    }

    /**
     * 便捷构造有序 Map（按插入顺序序列化，便于与网易云结构对照排查）
     */
    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
