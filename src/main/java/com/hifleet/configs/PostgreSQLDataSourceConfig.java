package com.hifleet.configs;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * PostgreSQL 数据源配置（与 hifleet.maintainance.tools 一致）。
 * 用于查询大风浪区域 regions_wave / regions_wind。
 */
@Configuration
public class PostgreSQLDataSourceConfig {

    @Bean(name = "wgq111PGDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.wgq111.pg")
    public DataSource wgq111PGDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "wgq111PGTransactionManager")
    public DataSourceTransactionManager wgq111PGTransactionManager(
            @Qualifier("wgq111PGDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "wgq111PGJdbcTemplate")
    public JdbcTemplate wgq111PGJdbcTemplate(@Qualifier("wgq111PGDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
