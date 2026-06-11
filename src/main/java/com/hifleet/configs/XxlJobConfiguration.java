package com.hifleet.configs;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
@ConditionalOnProperty(value = "xxljob.enabled", havingValue = "true", matchIfMissing = true)
public class XxlJobConfiguration {

    @Autowired
    private XxlJobProperties xxlJobProperties;

    @Bean
    @ConditionalOnClass()
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> xxl-job config init.");
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(xxlJobProperties.getAdminAddresses());
        executor.setAppname(xxlJobProperties.getAppname());
        executor.setIp(xxlJobProperties.getIp());
        executor.setPort(xxlJobProperties.getPort());
        executor.setAccessToken(xxlJobProperties.getAccessToken());
        executor.setLogPath(xxlJobProperties.getLogPath());
        executor.setLogRetentionDays(xxlJobProperties.getLogRetentionDays());
        return executor;
    }
}
