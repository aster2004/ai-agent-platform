package com.ai.agentplatform.module.codegen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 代码生成模块专用线程池
 * 用于 SSE 流式生成异步执行，避免裸线程创建
 */
@Configuration
public class CodeGenThreadPoolConfig {

    /** 流式生成专用线程池 */
    @Bean("codeGenStreamExecutor")
    public Executor codeGenStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("codegen-stream-");
        // 队列满时由调用线程执行（CallerRunsPolicy），避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
