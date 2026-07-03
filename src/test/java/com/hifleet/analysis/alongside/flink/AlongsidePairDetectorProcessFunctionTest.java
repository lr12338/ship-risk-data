package com.hifleet.analysis.alongside.flink;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import com.hifleet.analysis.alongside.core.model.PairPriority;
import com.hifleet.analysis.alongside.core.model.PairProximityEvidence;
import com.hifleet.analysis.alongside.core.model.VesselClass;
import com.hifleet.analysis.alongside.core.model.VesselProfile;
import com.hifleet.analysis.alongside.core.model.VesselType;
import com.hifleet.analysis.alongside.core.rule.AlongsideRuleEngineImpl;
import com.hifleet.analysis.portcall.config.AlongsideConfig;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.Properties;

public class AlongsidePairDetectorProcessFunctionTest {

    @Test
    public void shouldEmitUpdateAfterConfirmedEventAndConfiguredInterval() {
        AlongsideConfig config = config();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();

        for (int i = 0; i < 6; i++) {
            AlongsidePairDetectorProcessFunction.addEvidence(state, evidence(i), config);
        }
        AlongsideEvent confirmed = AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config);
        AlongsidePairDetectorProcessFunction.markEmitted(state, confirmed);
        AlongsidePairDetectorProcessFunction.addEvidence(state, evidence(7), config);

        AlongsideEvent update = AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config);

        Assert.assertEquals(AlongsideRuleEngineImpl.SUB_TYPE_UPDATE, update.getEventSubType());
    }

    @Test
    public void shouldBuildEndEventAfterAnyCandidateWasEmitted() {
        AlongsideConfig config = config();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();

        for (int i = 0; i < 3; i++) {
            AlongsidePairDetectorProcessFunction.addEvidence(state, evidence(i * 2), config);
        }
        AlongsideEvent weak = AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config);
        AlongsidePairDetectorProcessFunction.markEmitted(state, weak);

        AlongsideEvent end = AlongsidePairDetectorProcessFunction.buildEndEventIfNeeded(state, config);

        Assert.assertEquals(AlongsideRuleEngineImpl.SUB_TYPE_END, end.getEventSubType());
        Assert.assertEquals("EVENT_ENDED", end.getExclusionFlag());
    }

    private AlongsideConfig config() {
        Properties props = new Properties();
        AlongsideConfig.addDefaults(props);
        props.setProperty("alongside.output.update-interval-minutes", "5");
        return AlongsideConfig.fromProperties(props);
    }

    private PairProximityEvidence evidence(int index) {
        Instant time = Instant.parse("2026-06-29T00:00:00Z").plusSeconds(index * 360L);
        PairProximityEvidence evidence = new PairProximityEvidence();
        evidence.setPairId("100-200");
        evidence.setMmsiA(100L);
        evidence.setMmsiB(200L);
        evidence.setEvidenceTime(time);
        evidence.setPointATime(time);
        evidence.setPointBTime(time.plusSeconds(30L));
        evidence.setLonA(121.0d);
        evidence.setLatA(31.0d);
        evidence.setLonB(121.001d);
        evidence.setLatB(31.0d);
        evidence.setSpeedA(1.0d);
        evidence.setSpeedB(1.1d);
        evidence.setCourseA(90.0d);
        evidence.setCourseB(91.0d);
        evidence.setDistanceNm(0.05d);
        evidence.setTimeGapSeconds(30L);
        evidence.setRelativeSpeedKnots(0.1d);
        evidence.setHeadingParallelDiff(1.0d);
        evidence.setPairPriority(PairPriority.HIGH);
        evidence.setVesselA(new VesselProfile(100L, VesselType.TANKER, VesselClass.HIGH_PRIORITY, null));
        evidence.setVesselB(new VesselProfile(200L, VesselType.TANKER, VesselClass.HIGH_PRIORITY, null));
        return evidence;
    }
}
