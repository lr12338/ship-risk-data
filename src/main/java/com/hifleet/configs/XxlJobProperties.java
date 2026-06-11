package com.hifleet.configs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "xxljob")
public class XxlJobProperties {

    private String adminAddresses;
    private String appname;
    private String ip;
    private int port;
    private String accessToken;
    private String logPath;
    private int logRetentionDays;
    private Boolean enabled = true;
}
