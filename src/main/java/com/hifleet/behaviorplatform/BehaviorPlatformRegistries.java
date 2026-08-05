package com.hifleet.behaviorplatform;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public final class BehaviorPlatformRegistries {
    @Value("${ship.behavior.profile-dir:}") private String externalProfileDirectory;
    private BehaviorRunProfileRegistry profiles;
    private RuleSetRegistry ruleSets;

    @PostConstruct
    public void load() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Path external = StringUtils.isBlank(externalProfileDirectory)
                ? null : Paths.get(externalProfileDirectory.trim());
        profiles = new BehaviorRunProfileLoader().loadClasspathAndExternal(
                loader, "behavior-profiles/index.txt", external);
        ruleSets = new RuleSetLoader().loadClasspath(loader, "behavior-rule-sets/index.txt");
        for (BehaviorRunProfile profile : profiles.all())
            ruleSets.require(profile.getRuleSetId(), profile.getRuleSetVersion());
    }

    public BehaviorRunProfileRegistry profiles() { return profiles; }
    public RuleSetRegistry ruleSets() { return ruleSets; }
}
