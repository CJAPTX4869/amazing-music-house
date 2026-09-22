package com.example.music.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * <p>
 * 说明：若不自定义 Executor，Spring 默认使用 {@code SimpleAsyncTaskExecutor}，
 * 它<b>不复用线程且线程数无上限</b>，高并发下会持续创建线程，存在资源耗尽风险。
 * 本配置改为有界线程池，并采用 CallerRunsPolicy 在队列满时由调用线程兜底执行。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /** CPU 核心数 */
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：CPU 核心数与 4 取较大值（异步任务多为轻量 DB 更新）
        executor.setCorePoolSize(Math.max(CPU_COUNT, 4));
        executor.setMaxPoolSize(Math.max(CPU_COUNT * 2, 8));
        // 队列容量
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("music-async-");
        executor.setKeepAliveSeconds(60);
        // 队列满时由提交任务的线程执行，避免丢弃计数任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 允许核心线程超时回收
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();
        log.info("异步线程池已初始化：core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), 200);
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("异步任务执行异常：method={}, params={}",
                        method.getName(), Arrays.toString(params), ex);
            }
        };
    }
}
