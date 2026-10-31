package com.example.music.common.exception;

import com.example.music.common.response.Result;
import com.example.music.common.response.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.nio.file.InvalidPathException;
import java.util.Locale;

/**
 * 全局异常处理器
 * <p>
 * 约定：所有异常统一转换为 {@link Result}，不向前端暴露堆栈、SQL 与内部路径。
 * 注意：Spring Boot 2.7 / Spring 5.3 没有 NoResourceFoundException（Spring 6 才引入）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常（可预期）
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [{}] {} -> code={}, msg={}",
                request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * @Valid 校验失败（请求体 JSON）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("参数校验失败：{}", message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 表单/查询参数绑定失败
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("参数绑定失败：{}", message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 单参数校验失败（@Validated + @RequestParam）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("参数校验失败：{}", message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 缺少必填请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(ResultCode.PARAM_ERROR, "缺少必要参数：" + e.getParameterName());
    }

    /**
     * 请求体不可读（JSON 格式错误）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.error(ResultCode.PARAM_ERROR, "请求体格式错误");
    }

    /**
     * 上传文件超过大小限制（由 multipart 配置触发）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过限制：{}", e.getMessage());
        return Result.error(ResultCode.FILE_TOO_LARGE);
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.error(ResultCode.METHOD_NOT_ALLOWED, "不支持的请求方法：" + e.getMethod());
    }

    /**
     * 404：需配合 spring.mvc.throw-exception-if-no-handler-found=true
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandlerFound(NoHandlerFoundException e) {
        log.warn("接口不存在：{} {}", e.getHttpMethod(), e.getRequestURL());
        return Result.error(ResultCode.NOT_FOUND, "请求的接口不存在");
    }

    /**
     * 文件路径含非法字符（如 NUL）：Paths.get() 解析失败，按参数错误返回而非 500
     */
    @ExceptionHandler(InvalidPathException.class)
    public Result<Void> handleInvalidPath(InvalidPathException e) {
        log.warn("非法的文件路径：{}", e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR, "非法的文件路径");
    }

    /**
     * 兜底：未预期的异常
     * 记录完整堆栈，但只向客户端返回通用提示（不暴露内部细节）
     * <p>
     * 二进制流接口（音频播放 / 图片直出）在写出阶段抛错时，响应头已按 audio/* 等类型设置，
     * 此时再写 JSON 会因缺少对应转换器而抛出二次异常（客户端只能拿到 500 + 空响应体）。
     * 因此做两级保护：已提交则放弃写响应体；未提交但已按二进制流响应则重置为 404。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        if (response.isCommitted()) {
            log.warn("响应已提交，无法写出错误响应体 [{}] {} -> {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
            return null;
        }
        if (isBinaryResponse(response)) {
            log.warn("流式响应中发生异常，降级为 404 [{}] {} -> {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
            response.reset();
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return null;
        }
        log.error("系统异常 [{}] {}", request.getMethod(), request.getRequestURI(), e);
        return Result.error(ResultCode.SERVER_ERROR);
    }

    /**
     * 判断响应是否已按二进制流（非 JSON / 非文本）设置内容类型
     */
    private boolean isBinaryResponse(HttpServletResponse response) {
        String contentType = response.getContentType();
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return !lower.contains(MediaType.APPLICATION_JSON_VALUE) && !lower.contains("text/");
    }
}
