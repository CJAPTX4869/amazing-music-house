package com.example.music.common.response;

import lombok.Getter;

/**
 * 统一响应码定义（对应需求文档 4.8.4 错误码定义）
 */
@Getter
public enum ResultCode {

    /* ---------- 通用 ---------- */
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或登录已过期"),
    FORBIDDEN(403, "无权限操作"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    FILE_TOO_LARGE(413, "文件超过大小限制（最大 100MB）"),
    UNSUPPORTED_TYPE(415, "文件类型不支持"),
    SERVER_ERROR(500, "服务器内部错误"),

    /* ---------- 业务 ---------- */
    FILE_EMPTY(1001, "不能上传空文件"),
    USERNAME_EXISTS(1002, "用户名已被注册"),
    LOGIN_FAILED(1003, "用户名或密码错误"),
    FILE_WRITE_FAILED(1004, "文件写入失败，请重试"),
    FILE_MISSING(1005, "文件不存在或已被清理"),
    DISK_FULL(1006, "存储空间不足，请联系管理员"),
    ACCOUNT_DISABLED(1007, "账号已被禁用");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
