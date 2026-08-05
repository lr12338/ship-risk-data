package com.hifleet.behaviorplatform;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class BehaviorProfileLoaderTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void loadsInheritanceAndProducesStableResolvedHash() throws Exception {
        Files.write(folder.newFile("base.json").toPath(), ("{\"profileId\":\"alongside-base-v1\","
                + "\"version\":1,\"behaviorType\":\"ALONGSIDE\",\"ruleSetId\":\"alongside-v1\","
                + "\"ruleSetVersion\":1,\"sceneMode\":\"OFF\",\"writeMode\":\"NONE\","
                + "\"primaryShipTypePolicy\":\"MERCHANT_STRICT\","
                + "\"counterpartyShipTypePolicy\":\"STRICT_SAME_AS_PRIMARY\"}")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(folder.newFile("child.json").toPath(), ("{\"profileId\":\"alongside-child-v1\","
                + "\"version\":1,\"extendsProfileId\":\"alongside-base-v1\",\"sceneMode\":\"SHADOW\"}")
                .getBytes(StandardCharsets.UTF_8));

        BehaviorRunProfileRegistry first = new BehaviorRunProfileLoader().load(folder.getRoot().toPath());
        BehaviorRunProfileRegistry second = new BehaviorRunProfileLoader().load(folder.getRoot().toPath());
        BehaviorRunProfile child = first.require("alongside-child-v1");
        assertEquals(SceneMode.SHADOW, child.getSceneMode());
        assertEquals(BehaviorType.ALONGSIDE, child.getBehaviorType());
        assertEquals(64, child.getProfileHash().length());
        assertEquals(child.getProfileHash(), second.require("alongside-child-v1").getProfileHash());
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownProfileIsRejected() {
        new BehaviorRunProfileRegistry().require("missing-v1");
    }

    @Test
    public void classpathDefaultsReferenceExistingRuleSets() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        BehaviorRunProfileRegistry profiles = new BehaviorRunProfileLoader()
                .loadClasspathAndExternal(loader, "behavior-profiles/index.txt", null);
        RuleSetRegistry rules = new RuleSetLoader().loadClasspath(
                loader, "behavior-rule-sets/index.txt");
        assertEquals(9, profiles.all().size());
        for (BehaviorRunProfile profile : profiles.all()) {
            RuleSetDefinition ruleSet = rules.require(profile.getRuleSetId(), profile.getRuleSetVersion());
            assertEquals(64, ruleSet.getRuleSetHash().length());
            assertFalse(ruleSet.isProductionApproved());
        }
    }
}
