package com.hifleet.configs;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public final class RequiredSecretConfigurationValidator {
    private static final String[] PREFIXES = new String[]{
            "spring.datasource.ais41", "spring.datasource.ais41Readonly",
            "spring.datasource.dataanalysis", "spring.datasource.newdataanalysis",
            "spring.datasource.db3newais41", "spring.datasource.db3newalertrs",
            "spring.datasource.commodity", "spring.datasource.wgq111.pg"
    };
    private final Environment environment;
    public RequiredSecretConfigurationValidator(Environment environment) { this.environment = environment; }

    @PostConstruct
    public void validate() {
        for (String prefix : PREFIXES) {
            String url = environment.getProperty(prefix + ".url");
            if (StringUtils.isNotBlank(url)
                    && StringUtils.isBlank(environment.getProperty(prefix + ".password"))) {
                throw new IllegalStateException("missing required secret configuration: "
                        + prefix + ".password");
            }
        }
    }
}
