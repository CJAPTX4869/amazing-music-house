package com.example.music.constant;

/**
 * 角色常量（对应 sys_user.role）
 */
public final class RoleConstants {

    /** 普通用户 */
    public static final int USER = 0;

    /** 管理员 */
    public static final int ADMIN = 1;

    private RoleConstants() {
    }

    public static boolean isAdmin(Integer role) {
        return role != null && role == ADMIN;
    }
}
