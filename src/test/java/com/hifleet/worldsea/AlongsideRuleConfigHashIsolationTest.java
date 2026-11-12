package com.hifleet.worldsea;

import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.loitering.LoiteringPortSceneLoader;
import com.hifleet.loitering.LoiteringPortSceneProperties;
import com.hifleet.loitering.LoiteringPortSceneService;
import com.hifleet.loitering.LoiteringPortSceneSnapshot;
import org.junit.Test;
import org.locationtech.jts.index.strtree.STRtree;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertNotNull;

public class AlongsideRuleConfigHashIsolationTest {
    @Test
    public void alongsideRuleHashDoesNotRequireLoiteringSceneSnapshot() {
        STRtree index = new STRtree();
        index.build();
        final WorldSeaMaskSnapshot worldSnapshot = new WorldSeaMaskSnapshot(index, 1, 1, 0, 0,
                "test", "test-sha", "world-fingerprint", 0, "LON_LAT");
        WorldSeaMaskService worldSeaMaskService = new WorldSeaMaskService(new WorldSeaMaskLoader(null, null) {
            @Override public WorldSeaMaskSnapshot load() { return worldSnapshot; }
        });
        LoiteringPortSceneService unavailableSceneService = new LoiteringPortSceneService(
                new LoiteringPortSceneLoader(null, null, new LoiteringPortSceneProperties()) {
                    @Override public LoiteringPortSceneSnapshot load() {
                        throw new IllegalStateException("scene unavailable");
                    }
                });
        ShipBehaviorAnalysisService service = new ShipBehaviorAnalysisService();
        ReflectionTestUtils.setField(service, "worldSeaMaskService", worldSeaMaskService);
        ReflectionTestUtils.setField(service, "loiteringPortSceneService", unavailableSceneService);
        ReflectionTestUtils.setField(service, "loiteringPortSceneProperties", new LoiteringPortSceneProperties());

        assertNotNull(service.alongsideRuleConfigHash());
    }
}
