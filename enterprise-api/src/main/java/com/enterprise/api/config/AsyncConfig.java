package com.enterprise.api.config;

import com.enterprise.api.async.MdcTaskDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async} support. With {@code spring.threads.virtual.enabled=true} Boot
 * auto-configures {@code applicationTaskExecutor} as a virtual-thread
 * {@code SimpleAsyncTaskExecutor} (concurrency capped by
 * {@code spring.task.execution.simple.concurrency-limit}) and applies the single
 * {@link TaskDecorator} bean ({@link MdcTaskDecorator}) to it and to the scheduler,
 * so the MDC (correlation id) follows the work onto other threads.
 * <p>
 * Do not declare {@code @Bean} methods here: Boot wraps {@link AsyncConfigurer}
 * beans, and factory methods on this class would be invoked on the wrapper.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("@Async method {} failed", method.getName(), ex);
    }
}
