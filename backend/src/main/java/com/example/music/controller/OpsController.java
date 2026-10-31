package com.example.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.music.common.response.Result;
import com.example.music.security.OnlineClientTracker;
import com.example.music.security.UserContext;
import com.example.music.service.SongService;
import com.example.music.entity.Song;
import com.example.music.entity.SongPlayDaily;
import com.example.music.mapper.SongPlayDailyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.util.StringUtils;
import com.example.music.security.UserContext;
import com.example.music.entity.User;
import com.example.music.domain.Server;
import com.example.music.mapper.SongCommentMapper;
import com.example.music.mapper.PlaylistMapper;
import com.example.music.mapper.SongMapper;
import com.example.music.mapper.UserMapper;
import com.example.music.security.RequireAdmin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维状态接口（仅管理员）：应用 / JVM / 系统内存 / 磁盘 / 数据库 / 业务统计 的实时快照。
 * <p>
 * 全部来自 JDK 内建 MBean 与一次轻量 SELECT 1，无额外依赖、无写入。
 */
@Slf4j
@RestController
@RequestMapping("/admin/ops")
@RequireAdmin
@RequiredArgsConstructor
public class OpsController {

    private final UserMapper userMapper;
    private final SongMapper songMapper;
    private final SongCommentMapper songCommentMapper;
    private final PlaylistMapper playlistMapper;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final StringRedisTemplate redis;
    private final OnlineClientTracker onlineTracker;
    private final SongService songService;
    private final SongPlayDailyMapper songPlayDailyMapper;
    private final Environment env;

    @Value("${app.storage.root-path:./storage}")
    private String storageRoot;

    /** 恢复码是否已配置（只暴露布尔位，绝不回传码值） */
    @Value("${app.recovery-code:}")
    private String recoveryCode;

    /** zzyl/若依风格的整机监控快照：CPU / 内存 / JVM / 系统 / 磁盘（CPU 采样阻塞约 1 秒） */
    @GetMapping("/server")
    public Result<Server> server() throws Exception {
        Server server = new Server();
        server.copyTo();
        return Result.success(server);
    }

    /** 在线客户端（90 秒内有活动即在线；按 网页端/桌面端/移动端 分组） */
    /** 歌曲每日播放量曲线（最近 N 天；运维页选歌查看） */
    @GetMapping("/song-daily")
    public Result<List<Map<String, Object>>> songDaily(@RequestParam Long songId,
                                                       @RequestParam(defaultValue = "30") int days) {
        return Result.success(songPlayDailyMapper.selectDailyBySong(songId,
                Math.min(Math.max(days, 1), 365)));
    }

    /** 最火歌曲：播放 TOP50 + 评论 TOP50（同量看评论→看播放→先创建的在前） */
    @GetMapping("/hot-songs")
    public Result<Map<String, Object>> hotSongs(@RequestParam(defaultValue = "50") int limit,
                                                HttpServletRequest request) {
        int n = Math.min(Math.max(limit, 1), 50);
        // 排序规则：播放量相同看评论数，评论数相同看播放量，完全相同看先创建的（id ASC）
        // 走网易云格式转换（name/ar/al/playCount/commentCount），5175 发现页可直接消费
        String base = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("byPlays", com.example.music.utils.LegacyConvertUtils.songs(
                songService.convertToVO(songMapper.selectTopByPlays(n)), base));
        out.put("byComments", com.example.music.utils.LegacyConvertUtils.songs(
                songService.convertToVO(songMapper.selectTopByComments(n)), base));
        return Result.success(out);
    }

    /** 歌曲选项（id+标题，运维页选歌下拉用，管理端） */
    @GetMapping("/song-options")
    public Result<List<Map<String, Object>>> songOptions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Song s : songMapper.selectList(new LambdaQueryWrapper<Song>()
                .select(Song::getId, Song::getTitle)
                .orderByDesc(Song::getId))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("title", s.getTitle());
            out.add(m);
        }
        return Result.success(out);
    }

    /**
     * 每日播放统计（数据透视）：最近 N 天 × 每一首歌。
     * 返回 dates（日期数组）+ songs（每首歌一行：标题 + 每日计数数组 + 合计），
     * 没有播放记录的歌曲计 0 —— 覆盖"每一首歌"。
     */
    @GetMapping("/play-daily")
    public Result<Map<String, Object>> playDaily(@RequestParam(defaultValue = "30") int days) {
        int n = Math.min(Math.max(days, 1), 90);
        List<java.time.LocalDate> dates = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            dates.add(java.time.LocalDate.now().minusDays(i));
        }
        List<Song> songs = songMapper.selectList(new LambdaQueryWrapper<Song>()
                .select(Song::getId, Song::getTitle));
        List<SongPlayDaily> rows = songPlayDailyMapper.selectList(new LambdaQueryWrapper<SongPlayDaily>()
                .ge(SongPlayDaily::getStatDate, dates.get(0))
                .orderByAsc(SongPlayDaily::getStatDate));
        Map<String, Integer> byKey = new LinkedHashMap<>();
        for (SongPlayDaily r : rows) {
            byKey.put(r.getSongId() + "|" + r.getStatDate(), r.getPlayCount());
        }
        List<Map<String, Object>> outSongs = new ArrayList<>();
        for (Song s : songs) {
            List<Integer> counts = new ArrayList<>();
            int total = 0;
            for (java.time.LocalDate d : dates) {
                int c = byKey.getOrDefault(s.getId() + "|" + d, 0);
                counts.add(c);
                total += c;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("songId", s.getId());
            m.put("title", s.getTitle());
            m.put("total", total);
            m.put("counts", counts);
            outSongs.add(m);
        }
        List<String> dateStrs = dates.stream().map(java.time.LocalDate::toString).collect(java.util.stream.Collectors.toList());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dates", dateStrs);
        out.put("songs", outSongs);
        return Result.success(out);
    }

    /**
     * 单曲数据页：一首歌的完整实时状态（基本信息 + 每日播放量 + 评论数）。
     * 歌曲状态总览表格 / 歌曲管理列表点进来即看。
     */
    @GetMapping("/song-data")
    public Result<Map<String, Object>> songData(@RequestParam Long songId, HttpServletRequest request) {
        Song s = songMapper.selectById(songId);
        if (s == null) {
            Map<String, Object> missing = new LinkedHashMap<>();
            missing.put("exists", false);
            return Result.success(missing);
        }
        String base = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", s.getId());
        info.put("title", s.getTitle());
        info.put("artist", s.getArtist());
        info.put("album", s.getAlbum());
        info.put("tags", s.getTags() == null ? "未标注" : s.getTags());
        info.put("status", s.getStatus());
        info.put("playCount", s.getPlayCount());
        info.put("commentCount", s.getCommentCount());
        info.put("likeCount", s.getLikeCount());
        info.put("createTime", String.valueOf(s.getCreateTime()));
        info.put("coverUrl", StringUtils.hasText(s.getCoverPath())
                ? base + "/api/songs/" + s.getId() + "/cover"
                : base + "/api/songs/" + s.getId() + "/cover");
        // 30 天全量横轴（补零），播放量与评论数双曲线——页面看的是趋势而不是"有记录的那几天"
        java.time.LocalDate today = java.time.LocalDate.now();
        List<String> dates = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            dates.add(today.minusDays(i).toString());
        }
        Map<String, Integer> playMap = new LinkedHashMap<>();
        for (Map<String, Object> row : songPlayDailyMapper.selectDailyBySong(songId, 30)) {
            playMap.put(String.valueOf(row.get("date")), ((Number) row.get("count")).intValue());
        }
        Map<String, Integer> cmtMap = new LinkedHashMap<>();
        for (Map<String, Object> row : songCommentMapper.selectDailyBySong(songId, today.minusDays(29))) {
            cmtMap.put(String.valueOf(row.get("date")), ((Number) row.get("count")).intValue());
        }
        List<Integer> plays = new ArrayList<>();
        List<Integer> comments = new ArrayList<>();
        for (String d : dates) {
            plays.add(playMap.getOrDefault(d, 0));
            comments.add(cmtMap.getOrDefault(d, 0));
        }
        List<Map<String, Object>> daily = new ArrayList<>();
        for (var row : songPlayDailyMapper.selectDailyBySong(songId, 30)) {
            daily.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exists", true);
        out.put("info", info);
        out.put("daily", daily);
        out.put("dates", dates);
        out.put("plays", plays);
        out.put("comments", comments);
        return Result.success(out);
    }

    @GetMapping("/online")
    public Result<Map<String, Object>> online() {
        var entries = onlineTracker.listOnline();
        // 排除查看者自己：打开本页的动作本身就是心跳，把自己算进去属于自指噪音；
        // 其他管理员不受影响（多管理员场景仍互相可见）。
        Long selfId = UserContext.getUserId();
        if (selfId != null) {
            entries.removeIf(e -> selfId.equals(e.get("userId")));
        }
        // 补昵称
        List<Long> ids = entries.stream().map(e -> (Long) e.get("userId")).distinct().collect(java.util.stream.Collectors.toList());
        Map<Long, User> users = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (User u : userMapper.selectBatchIds(ids)) {
                users.put(u.getId(), u);
            }
        }
        for (var e : entries) {
            User u = users.get((Long) e.get("userId"));
            e.put("nickname", u != null ? u.getNickname() : String.valueOf(e.get("username")));
            e.put("avatar", u != null ? u.getAvatar() : null);   // 头像相对路径（可为空）
        }
        // 平台分组
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("web", "网页端");
        labels.put("admin", "管理后台");
        labels.put("desktop", "桌面端");
        labels.put("mobile", "移动端");
        Map<String, List<Map<String, Object>>> byPlatform = new LinkedHashMap<>();
        for (var e : entries) {
            byPlatform.computeIfAbsent(String.valueOf(e.get("platform")), k -> new ArrayList<>()).add(e);
        }
        List<Map<String, Object>> platforms = new ArrayList<>();
        for (var item : labels.entrySet()) {
            List<Map<String, Object>> platUsers = byPlatform.getOrDefault(item.getKey(), List.of());
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("key", item.getKey());
            p.put("label", item.getValue());
            p.put("online", !platUsers.isEmpty());
            p.put("count", platUsers.size());
            p.put("users", platUsers);
            platforms.add(p);
        }
        // 兜底：出现未知平台也展示
        for (var item : byPlatform.entrySet()) {
            if (!labels.containsKey(item.getKey())) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("key", item.getKey()); p.put("label", item.getKey());
                p.put("online", true); p.put("count", item.getValue().size()); p.put("users", item.getValue());
                platforms.add(p);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", entries.size());
        out.put("platforms", platforms);
        return Result.success(out);
    }

    /** 歌曲状态统计：总量/上下架/有无歌词/有无封面/类型分布/标签分布（管理端可视化用） */
    @GetMapping("/song-stats")
    public Result<Map<String, Object>> songStats() {
        List<Song> rows = songMapper.selectList(new LambdaQueryWrapper<Song>()
                .select(Song::getId, Song::getTitle, Song::getArtist, Song::getTags,
                        Song::getStatus, Song::getLyricPath, Song::getCoverPath));
        int online = 0, offline = 0, withLyric = 0, withCover = 0, noTag = 0;
        Map<String, Integer> typeDist = new LinkedHashMap<>();
        Map<String, Integer> tagDist = new LinkedHashMap<>();
        for (Song s : rows) {
            boolean isOnline = Integer.valueOf(1).equals(s.getStatus());
            if (isOnline) online++; else offline++;
            if (StringUtils.hasText(s.getLyricPath())) withLyric++; 
            if (StringUtils.hasText(s.getCoverPath())) withCover++;
            // 类型 = 第一个标签（类型标签约定为首标签）；没打标签的显示「未知」，与真正归为「其他」的歌区分开
            String[] tagArr = StringUtils.hasText(s.getTags()) ? s.getTags().split(",") : new String[0];
            String type = tagArr.length > 0 ? tagArr[0].trim() : "未知";
            typeDist.merge(type, 1, Integer::sum);
            if (!StringUtils.hasText(s.getTags())) {
                noTag++;
            } else {
                for (String t : s.getTags().split(",")) {
                    String v = t.trim();
                    if (!v.isEmpty()) tagDist.merge(v, 1, Integer::sum);
                }
            }
        }
        // 三类明细（全量返回，前端滚动展示；列表不大——每项只有 id/标题/歌手）
        List<Map<String, Object>> noLyricList = new ArrayList<>();
        List<Map<String, Object>> noCoverList = new ArrayList<>();
        List<Map<String, Object>> offlineList = new ArrayList<>();
        for (Song s : rows) {
            boolean noLyricFlag = !StringUtils.hasText(s.getLyricPath());
            boolean noCoverFlag = !StringUtils.hasText(s.getCoverPath());
            boolean offFlag = !Integer.valueOf(1).equals(s.getStatus());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("title", s.getTitle());
            item.put("artist", s.getArtist());
            if (noLyricFlag) noLyricList.add(item);
            if (noCoverFlag) noCoverList.add(item);
            if (offFlag) offlineList.add(item);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.put("online", online);
        out.put("offline", offline);
        out.put("withLyric", withLyric);
        out.put("withoutLyric", rows.size() - withLyric);
        out.put("withCover", withCover);
        out.put("withoutCover", rows.size() - withCover);
        out.put("typeDist", typeDist);
        out.put("tagDist", tagDist);
        out.put("noTag", noTag);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("noLyric", noLyricList);
        details.put("noCover", noCoverList);
        details.put("offline", offlineList);
        out.put("details", details);
        return Result.success(out);
    }

    /**
     * 一键自动分类：为没有标签的歌曲按"歌名+歌手+专辑+有无歌词"打 1~3 个标签。
     * 幂等：已有标签的歌曲跳过。返回处理统计。
     */
    @PostMapping("/auto-tag")
    public Result<Map<String, Object>> autoTag() {
        List<Song> rows = songMapper.selectList(new LambdaQueryWrapper<Song>()
                .select(Song::getId, Song::getTitle, Song::getArtist, Song::getAlbum,
                        Song::getLyricPath, Song::getTags));
        int tagged = 0, skipped = 0;
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (Song s : rows) {
            if (StringUtils.hasText(s.getTags())) {
                skipped++;
                continue;
            }
            List<String> tags = autoTagsFor(s);
            Song update = new Song();
            update.setId(s.getId());
            update.setTags(String.join(",", tags));
            songMapper.updateById(update);
            for (String t : tags) dist.merge(t, 1, Integer::sum);
            tagged++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tagged", tagged);
        out.put("skipped", skipped);
        out.put("distribution", dist);
        log.info("自动分类完成：tagged={}, skipped={}, dist={}", tagged, skipped, dist);
        return Result.success("自动分类完成", out);
    }

    /**
     * 单曲自动标签规则（按优先级，真正基于内容区分）：
     * 1. 纯音乐：无歌词文件 且 标题/专辑含伴奏/器乐关键词 → 「纯音乐」（不分国籍）
     * 2. 地区：含日文假名→日本；含谚文→韩国；含汉字→华语；全 ASCII→欧美；其余→其他
     * 3. 风格：旧流派值映射为第二标签（动漫/原声/古典/J-POP/同人/游戏）
     */
    private List<String> autoTagsFor(Song s) {
        List<String> tags = new ArrayList<>();
        String hay = (nvl(s.getTitle()) + " " + nvl(s.getArtist()) + " " + nvl(s.getAlbum())).trim();
        String lower = hay.toLowerCase();
        boolean noLyric = !StringUtils.hasText(s.getLyricPath());
        boolean instrumentalHint = lower.contains("inst") || lower.contains("bgm")
                || lower.contains("伴奏") || lower.contains("纯音乐")
                || lower.contains("off vocal") || lower.contains("karaoke")
                || lower.contains(" backing") || lower.contains("instrumental");
        boolean hasKana = hay.matches(".*[ぁ-んァ-ヶ].*");
        boolean hasHangul = hay.matches(".*[가-힣].*");
        boolean hasHan = hay.matches(".*[一-龥].*");
        boolean hasAsciiLetter = hay.matches(".*[a-zA-Z].*");

        if (noLyric && instrumentalHint) {
            tags.add("纯音乐");
        } else if (hasKana) {
            tags.add("日本");
        } else if (hasHangul) {
            tags.add("韩国");
        } else if (hasHan) {
            tags.add("华语");
        } else if (hasAsciiLetter) {
            tags.add("欧美");
        } else {
            tags.add("其他");
        }
        return tags;
    }

    private static String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> root = new LinkedHashMap<>();

        /* ---------------- 应用 / JVM ---------------- */
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        java.lang.management.MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("pid", runtime.getVmVendor() == null ? null : runtime.getPid());
        app.put("javaVersion", System.getProperty("java.version"));
        app.put("javaVendor", System.getProperty("java.vendor"));
        app.put("uptimeSeconds", runtime.getUptime() / 1000);
        app.put("heapUsedMB", memory.getHeapMemoryUsage().getUsed() / 1048576);
        app.put("heapMaxMB", memory.getHeapMemoryUsage().getMax() / 1048576);
        app.put("activeProfiles", env.getActiveProfiles().length == 0 ? "default" : String.join(",", env.getActiveProfiles()));
        app.put("port", env.getProperty("server.port", "8080"));
        app.put("recoveryCodeConfigured", StringUtils.hasText(recoveryCode));
        root.put("app", app);

        /* ---------------- 操作系统 / 系统内存 ---------------- */
        Map<String, Object> sys = new LinkedHashMap<>();
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            sys.put("osName", System.getProperty("os.name"));
            sys.put("osArch", System.getProperty("os.arch"));
            sys.put("processors", os.getAvailableProcessors());
            sys.put("sysTotalMB", os.getTotalPhysicalMemorySize() / 1048576);
            sys.put("sysFreeMB", os.getFreePhysicalMemorySize() / 1048576);
            sys.put("systemLoad", Math.round(os.getSystemLoadAverage() * 100) / 100.0);
        } catch (Exception e) {
            sys.put("error", String.valueOf(e.getMessage()));
        }
        root.put("system", sys);

        /* ---------------- 磁盘（常用根分区） ---------------- */
        List<Map<String, Object>> disks = new ArrayList<>();
        for (File f : File.listRoots()) {
            try {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("root", f.getPath());
                d.put("totalGB", Math.round(f.getTotalSpace() / 1073741824.0 * 10) / 10.0);
                d.put("usableGB", Math.round(f.getUsableSpace() / 1073741824.0 * 10) / 10.0);
                disks.add(d);
            } catch (Exception ignored) {
                // 无介质的光驱等根分区会抛异常，跳过即可
            }
        }
        root.put("disks", disks);

        /* ---------------- 数据库连通性 + 版本 ---------------- */
        Map<String, Object> db = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            db.put("ok", true);
            db.put("product", md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
            db.put("url", md.getURL());
        } catch (Exception e) {
            db.put("ok", false);
            db.put("error", String.valueOf(e.getMessage()));
        }
        root.put("db", db);

        /* ---------------- 业务统计 ---------------- */
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            stats.put("users", userMapper.selectCount(null));
            stats.put("songs", songMapper.selectCount(null));
            stats.put("comments", songCommentMapper.selectCount(null));
            stats.put("playlists", playlistMapper.selectCount(null));
        } catch (Exception e) {
            stats.put("error", String.valueOf(e.getMessage()));
        }
        root.put("stats", stats);

        /* ---------------- 存储目录 ---------------- */
        Map<String, Object> storage = new LinkedHashMap<>();
        Path rootPath = Path.of(storageRoot).toAbsolutePath().normalize();
        storage.put("path", rootPath.toString());
        storage.put("exists", Files.exists(rootPath));
        root.put("storage", storage);

        /* ---------------- Redis（缓存/票据/限流） ---------------- */
        Map<String, Object> redis = new LinkedHashMap<>();
        try (org.springframework.data.redis.connection.RedisConnection conn = redisConnectionFactory.getConnection()) {
            java.util.Properties info = conn.info();
            redis.put("ok", true);
            redis.put("version", info.getProperty("redis_version"));
            redis.put("mode", info.getProperty("redis_mode"));
            redis.put("uptimeDays", info.getProperty("uptime_in_days"));
            redis.put("usedMemoryHuman", info.getProperty("used_memory_human"));
            redis.put("connectedClients", info.getProperty("connected_clients"));
            redis.put("opsPerSec", info.getProperty("instantaneous_ops_per_sec"));
            long hits = Long.parseLong(info.getProperty("keyspace_hits", "0"));
            long misses = Long.parseLong(info.getProperty("keyspace_misses", "0"));
            long total = hits + misses;
            redis.put("hitRate", total == 0 ? "-" : Math.round(hits * 10000.0 / total) / 100.0 + "%");
            redis.put("dbSize", conn.dbSize());
            Map<String, Object> keys = new LinkedHashMap<>();
            keys.put("验证码票据", countKeys("captcha:ticket:*"));
            keys.put("登录限流", countKeys("login:*"));
            keys.put("歌曲分页缓存", countKeys("songs:page:*"));
            redis.put("businessKeys", keys);
        } catch (Exception e) {
            redis.put("ok", false);
            redis.put("error", String.valueOf(e.getMessage()));
        }
        root.put("redis", redis);

        return Result.success(root);
    }

    private long countKeys(String pattern) {
        java.util.Set<String> keys = redis.keys(pattern);
        return keys == null ? 0 : keys.size();
    }
}
