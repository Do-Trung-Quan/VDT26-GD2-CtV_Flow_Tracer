package com.ctv.flowtracer.app.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Executor virtual threads cho quét nền và pha extract song song. */
@Configuration
public class ExecutorConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService scanExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
