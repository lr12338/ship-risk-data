package com.hifleet.configs;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@MapperScan(basePackages = DataSourceConfig.PACKAGE, sqlSessionTemplateRef = "aisSqlSessionTemplate")
public class DataSourceConfig {

    @Autowired
    private Environment environment;

    static final String PACKAGE = "com.hifleet.dao";
    static final String MAPPER_LOCATION = "classpath:mapping/ais41/*.xml";

    @Bean(name = "ais41DataSource")
    @Qualifier("ais41DataSource")
    @ConfigurationProperties(prefix = "spring.datasource.ais41")
    @Primary
    public DataSource ais41DataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "aisTransactionManager")
    @Primary
    public DataSourceTransactionManager aisTransactionManager(@Qualifier("ais41DataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "aisSqlSessionFactory")
    @Primary
    public SqlSessionFactory aisSqlSessionFactory(@Qualifier("ais41DataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(MAPPER_LOCATION));
        Properties pro = new Properties();
        pro.setProperty("taipySchemaName", environment.getProperty("schemaName.taipySchemaName", ""));
        bean.setConfigurationProperties(pro);
        return bean.getObject();
    }

    @Bean(name = "aisSqlSessionTemplate")
    @Primary
    public SqlSessionTemplate aisSqlSessionTemplate(@Qualifier("aisSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "ais41ReadOnlyDataSource")
    @Qualifier("ais41ReadOnlyDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.ais41Readonly")
    public DataSource ais41ReadOnlyDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "pgDataSource")
    @Qualifier("pgDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.pgReadonly")
    public DataSource pgDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "dataanalysisDataSource")
    @Qualifier("dataanalysisDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.dataanalysis")
    public DataSource dataanalysisDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "newDataanalysisDataSource")
    @Qualifier("newDataanalysisDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.newdataanalysis")
    public DataSource newDataanalysisDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "DB3Ais41DataSource")
    @Qualifier("DB3Ais41DataSource")
    @ConfigurationProperties(prefix = "spring.datasource.db3newais41")
    public DataSource db3Ais41DataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "DB3AlertRsDataSource")
    @Qualifier("DB3AlertRsDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.db3newalertrs")
    public DataSource db3AlertRsDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "commodity47DataSource")
    @Qualifier("commodity47DataSource")
    @ConfigurationProperties(prefix = "spring.datasource.commodity")
    public DataSource commodity47DataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "aid41JdbcTemplate")
    public JdbcTemplate ais41JdbcTemplate(@Qualifier("ais41DataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "aid41ReadOnlyJdbcTemplate")
    public JdbcTemplate aid41ReadOnlyJdbcTemplate(@Qualifier("ais41ReadOnlyDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "dataanalysisJdbcTemplate")
    public JdbcTemplate dataanalysisJdbcTemplate(@Qualifier("dataanalysisDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "newDataanalysisJdbcTemplate")
    public JdbcTemplate newDataanalysisJdbcTemplate(@Qualifier("newDataanalysisDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "DB3Ais41JdbcTemplate")
    public JdbcTemplate db3Ais41JdbcTemplate(@Qualifier("DB3Ais41DataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "DB3AlertRsJdbcTemplate")
    public JdbcTemplate db3AlertRsJdbcTemplate(@Qualifier("DB3AlertRsDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "pgJdbcTemplate")
    public JdbcTemplate pgJdbcTemplate(@Qualifier("pgDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "commodity47JdbcTemplate")
    public JdbcTemplate commodity47JdbcTemplate(@Qualifier("commodity47DataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
