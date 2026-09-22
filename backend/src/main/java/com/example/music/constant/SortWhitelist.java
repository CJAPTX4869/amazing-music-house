package com.example.music.constant;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 排序字段白名单
 * <p>
 * 排序字段是动态拼接的列名，无法使用 #{} 参数绑定，必须经过白名单校验，
 * 否则会形成 SQL 注入漏洞。
 */
public final class SortWhitelist {

    /** 默认排序字段 */
    public static final String DEFAULT_SORT_FIELD = "create_time";

    /** 默认排序方向（true = 升序，false = 降序） */
    public static final boolean DEFAULT_ASC = false;

    private static final Map<String, String> ALLOWED;

    static {
        Map<String, String> map = new HashMap<>(8);
        map.put("create_time", "create_time");
        map.put("download_count", "download_count");
        map.put("play_count", "play_count");
        map.put("title", "title");
        ALLOWED = Collections.unmodifiableMap(map);
    }

    private SortWhitelist() {
    }

    /**
     * 校验并解析排序字段
     *
     * @param field 前端传入的字段名
     * @return 安全的数据库列名；非法或空则返回 null（由调用方回退到默认排序）
     */
    public static String resolve(String field) {
        if (field == null || field.trim().isEmpty()) {
            return null;
        }
        return ALLOWED.get(field.trim().toLowerCase());
    }
}
