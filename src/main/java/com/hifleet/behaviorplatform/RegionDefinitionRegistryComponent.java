package com.hifleet.behaviorplatform;

import com.hifleet.regionbehavior.RegionBehaviorConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.file.Paths;

@Component
public final class RegionDefinitionRegistryComponent {
    private final RegionBehaviorConfig config;
    @Value("${ship.behavior.region-definition-dir:}") private String directory;
    private RegionDefinitionRegistry registry;
    public RegionDefinitionRegistryComponent(RegionBehaviorConfig config) { this.config = config; }
    @PostConstruct public void load() {
        registry = new RegionDefinitionLoader().load(StringUtils.isBlank(directory)
                ? null : Paths.get(directory.trim()), config);
    }
    public RegionDefinition resolve(BehaviorJobEnvelope envelope) {
        return envelope.getRegionId() == null ? registry.bbox(envelope.getBbox(), envelope.getShipLimit())
                : registry.require(envelope.getRegionId());
    }
}
