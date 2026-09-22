package com.example.music.utils;

import com.example.music.vo.PlaylistVO;
import com.example.music.vo.SongVO;
import com.example.music.vo.UserVO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 旧前端兼容层数据转换工具
 * <p>
 * 背景：旧前端（vue3-cloud-music-master）按**网易云 API 的数据结构**消费数据，
 * 本工具负责把自有 VO 转换为旧前端期望的嵌套格式（字段映射见 需求分析.md 13.5）。
 * <p>
 * ⚠️ 三个必须遵守的约定（都是实测踩过的坑）：
 * <ol>
 *   <li><b>歌名字段名必须是 {@code name}</b>（旧前端读 {@code item.name}，不是 {@code title}，
 *       且 {@code mapSongs()} 不会做该字段的兜底映射）；</li>
 *   <li><b>{@code fee} 必须非 0</b>：旧前端 {@code filter(item => item.fee !== 0)} 会静默丢弃
 *       {@code fee === 0} 的歌曲，导致播放列表为空；</li>
 *   <li><b>播放地址必须不带 query</b>：旧前端会硬拼 {@code url + '?id=' + id}，
 *       若原 URL 有参数会得到畸形地址。</li>
 * </ol>
 *
 * @see <a href="需求分析.md">需求分析.md 第 13 章「旧前端融合方案」</a>
 */
public final class LegacyConvertUtils {

    /**
     * 可播放标识：必须同时满足旧前端的两个判断
     * <ol>
     *   <li>{@code filter(item => item.fee !== 0)}：为 0 会被播放列表静默丢弃 → 不能是 0</li>
     *   <li>{@code useValidateVipSong} 中 {@code fee === 1} 被判定为 VIP 歌曲，会拦截播放
     *       → 不能是 1</li>
     * </ol>
     * 取网易云语义的 {@code 8}（非会员可免费播放低音质），既过滤不掉也不触发 VIP 拦截。
     */
    private static final int FEE_PLAYABLE = 8;

    /** 多歌手分隔符（历史数据中以顿号/斜杠分隔） */
    private static final String ARTIST_SEPARATOR = "[、/,，]";

    /** 后端 context-path，用于把相对资源路径拼成绝对地址 */
    private static final String API_PREFIX = "/api";

    private LegacyConvertUtils() {
    }

    /* ==================== 歌曲 ==================== */

    /**
     * 单曲：SongVO → 旧前端（网易云）格式
     */
    public static Map<String, Object> song(SongVO vo) {
        return song(vo, null);
    }

    /**
     * 单曲转换（带请求基础地址）
     *
     * @param baseUrl 形如 {@code http://localhost:8080}（不含 context-path）；
     *                非空时把封面相对路径拼成绝对地址，旧前端 {@code <img src>} 可直接加载
     */
    public static Map<String, Object> song(SongVO vo, String baseUrl) {
        if (vo == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", vo.getId());
        // 坑 2：字段名必须是 name
        m.put("name", vo.getTitle());
        m.put("ar", artists(vo.getArtist()));
        // 别名：旧前端模板直接访问 item.alias[0]，字段缺失会抛渲染异常，必须显式给空数组
        m.put("alias", Collections.emptyList());
        m.put("al", album(vo.getAlbum(), absoluteCover(vo, baseUrl)));
        // 坑 3：时长单位是毫秒
        m.put("dt", vo.getDuration() == null ? 0 : vo.getDuration() * 1000);
        // 坑 1：fee 必须非 0 且非 1（详见 FEE_PLAYABLE 注释）
        m.put("fee", FEE_PLAYABLE);
        // 喜欢数/评论数（V3.1 列表角标：红心/评论气泡 + 数字，前端 formatCount 格式化为 999+/1w+/100w+）
        m.put("likeCount", vo.getLikeCount() == null ? 0 : vo.getLikeCount());
        m.put("commentCount", vo.getCommentCount() == null ? 0 : vo.getCommentCount());
        // 无 MV：必须显式输出 0，旧前端用 `mv !== 0` 判断是否渲染 MV 标签，
        // 字段缺失时 undefined !== 0 为真，会给每首歌都挂上 MV 标签
        m.put("mv", 0);
        m.put("playCount", vo.getPlayCount() == null ? 0 : vo.getPlayCount());
        m.put("pop", 100);
        m.put("no", 1);
        return m;
    }

    /**
     * 歌曲列表批量转换
     */
    public static List<Map<String, Object>> songs(List<SongVO> list) {
        return songs(list, null);
    }

    /**
     * 歌曲列表批量转换（带请求基础地址）
     */
    public static List<Map<String, Object>> songs(List<SongVO> list, String baseUrl) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(vo -> song(vo, baseUrl))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 封面绝对地址：相对路径 → {@code baseUrl + /api + /songs/{id}/cover}
     * <p>
     * 旧前端运行在独立端口（如 5175），相对路径会指向自身而 404，故必须拼绝对地址。
     *
     * @return 无封面时返回空串（旧前端 LoadImg 会自动回退占位图）
     */
    private static String absoluteCover(SongVO vo, String baseUrl) {
        String coverUrl = vo.getCoverUrl();
        if (!StringUtils.hasText(coverUrl)) {
            return "";
        }
        return StringUtils.hasText(baseUrl) ? baseUrl + API_PREFIX + coverUrl : coverUrl;
    }

    /**
     * 歌手数组：字符串 → [{id, name}]
     */
    private static List<Map<String, Object>> artists(String artist) {
        if (!StringUtils.hasText(artist)) {
            return Collections.singletonList(artistNode("未知歌手"));
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (String part : artist.split(ARTIST_SEPARATOR)) {
            String name = part.trim();
            if (!name.isEmpty()) {
                list.add(artistNode(name));
            }
        }
        return list.isEmpty() ? Collections.singletonList(artistNode(artist)) : list;
    }

    private static Map<String, Object> artistNode(String name) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", 0);
        a.put("name", name);
        return a;
    }

    /**
     * 专辑对象：{@code {id, name, picUrl}}（RotateCd 取 al.picUrl 作封面）
     */
    private static Map<String, Object> album(String albumName, String coverUrl) {
        Map<String, Object> al = new LinkedHashMap<>();
        al.put("id", 0);
        al.put("name", StringUtils.hasText(albumName) ? albumName : "未知专辑");
        al.put("picUrl", coverUrl == null ? "" : coverUrl);
        return al;
    }

    /* ==================== 歌单 ==================== */

    /**
     * 歌单：PlaylistVO → 旧前端格式
     *
     * @param vo      歌单
     * @param songs   歌单内歌曲（为 null 时不输出 tracks，仅输出 trackIds）
     */
    public static Map<String, Object> playlist(PlaylistVO vo, List<SongVO> songs) {
        return playlist(vo, songs, null);
    }

    /**
     * 歌单转换（带请求基础地址，使歌单内歌曲封面为绝对地址）
     */
    public static Map<String, Object> playlist(PlaylistVO vo, List<SongVO> songs, String baseUrl) {
        if (vo == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", vo.getId());
        m.put("name", vo.getName());
        m.put("description", vo.getDescription());
        m.put("coverImgUrl", absolutePlaylistCover(vo, baseUrl));
        m.put("userId", vo.getUserId());
        m.put("nickname", vo.getUserName());
        m.put("trackCount", vo.getSongCount() == null ? 0 : vo.getSongCount());
        m.put("playCount", 0);
        m.put("subscribed", false);
        m.put("creator", creator(vo, baseUrl));
        if (songs == null) {
            m.put("trackIds", Collections.emptyList());
        } else {
            m.put("trackIds", songs.stream()
                    .map(s -> Collections.singletonMap("id", s.getId()))
                    .collect(Collectors.toList()));
            m.put("tracks", songs(songs, baseUrl));
        }
        return m;
    }

    private static Map<String, Object> creator(PlaylistVO vo, String baseUrl) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("userId", vo.getUserId());
        c.put("nickname", vo.getUserName() == null ? "" : vo.getUserName());
        // 头像统一走用户头像接口：该用户无头像时返回 404，前端 n-avatar 自动降级为默认占位
        c.put("avatarUrl", vo.getUserId() == null ? "" : userAvatarApi(vo.getUserId(), baseUrl));
        return c;
    }

    /* ==================== 用户 ==================== */

    /**
     * 用户：UserVO → 旧前端 profile 格式（头像拼绝对地址）
     */
    public static Map<String, Object> profile(UserVO vo, String baseUrl) {
        if (vo == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", vo.getId());
        m.put("id", vo.getId());
        m.put("nickname", vo.getNickname());
        m.put("avatarUrl", absoluteAvatar(vo, baseUrl));
        m.put("vipType", 0);
        m.put("signature", vo.getSignature() == null ? "" : vo.getSignature());
        return m;
    }

    /**
     * 头像绝对地址
     * <p>
     * 数据库存的是存储相对路径，对外统一暴露为 {@code /users/{id}/avatar} 接口；
     * 旧前端运行在独立端口（5175），相对路径会 404，因此必须携带 baseUrl 前缀。
     */
    private static String absoluteAvatar(UserVO vo, String baseUrl) {
        if (vo.getAvatar() == null || vo.getAvatar().trim().isEmpty()) {
            return "";
        }
        return userAvatarApi(vo.getId(), baseUrl);
    }

    /**
     * 用户头像接口地址（含 baseUrl 前缀）
     * <p>
     * 头像不直接暴露存储路径，统一由 {@code GET /users/{id}/avatar} 直出。
     */
    private static String userAvatarApi(Long userId, String baseUrl) {
        String path = "/api/users/" + userId + "/avatar";
        return (baseUrl == null || baseUrl.isEmpty()) ? path : baseUrl + path;
    }

    /**
     * 歌单封面绝对地址：相对 API 路径 → {@code baseUrl + /api + /playlists/{id}/cover}
     * <p>
     * 旧前端运行在独立端口（如 5175），相对路径会指向自身而 404，故必须拼绝对地址。
     *
     * @return 无封面时返回空串（旧前端 LoadImg 自动回退占位图）
     */
    private static String absolutePlaylistCover(PlaylistVO vo, String baseUrl) {
        String coverUrl = vo.getCoverUrl();
        if (!StringUtils.hasText(coverUrl)) {
            return "";
        }
        return StringUtils.hasText(baseUrl) ? baseUrl + API_PREFIX + coverUrl : coverUrl;
    }
}
