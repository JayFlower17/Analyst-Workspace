package com.analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication
public class DataAnalysisPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataAnalysisPlatformApplication.class, args);
    }
}
