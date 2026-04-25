package com.analysis.config;

import java.io.File;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DuckDBConfig {

    @Value("${duckdb.path:./data/analysis.duckdb}")
    private String duckdbPath;

    @Bean(name = "duckdbDataSource")
    public DataSource duckdbDataSource() {
        File dataDir = new File(duckdbPath).getParentFile();
        if (dataDir != null && !dataDir.exists()) {
            dataDir.mkdirs();
        }
        return DataSourceBuilder.create()
                .driverClassName("org.duckdb.DuckDBDriver")
                .url("jdbc:duckdb:" + duckdbPath)
                .build();
    }
}
