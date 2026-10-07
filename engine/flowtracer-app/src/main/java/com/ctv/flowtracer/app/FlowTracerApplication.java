package com.ctv.flowtracer.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Điểm khởi động Analysis Engine. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FlowTracerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowTracerApplication.class, args);
    }
}
