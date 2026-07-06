package com.hifleet.analysis.alongside.flink;

import com.hifleet.analysis.alongside.core.model.AlongsideContext;
import com.hifleet.analysis.alongside.core.model.AlongsideDetectionResult;
import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import com.hifleet.analysis.alongside.core.model.AlongsideScoreResult;
import com.hifleet.analysis.alongside.core.model.PairEvidenceWindow;
import com.hifleet.analysis.alongside.core.model.PairProximityEvidence;
import com.hifleet.analysis.alongside.core.model.SceneAdjustmentResult;
import com.hifleet.analysis.alongside.core.rule.AlongsideRuleEngine;
import com.hifleet.analysis.alongside.core.rule.AlongsideRuleEngineImpl;
import com.hifleet.analysis.alongside.core.score.AlongsideRiskScorer;
import com.hifleet.analysis.alongside.core.score.AlongsideRiskScorerImpl;
import com.hifleet.analysis.alongside.core.score.AlongsideSceneAdjustmentService;
import com.hifleet.analysis.alongside.core.score.AlongsideSceneAdjustmentServiceImpl;
import com.hifleet.analysis.alongside.output.db.AlongsideEventIdGenerator;
import com.hifleet.analysis.portcall.config.AlongsideConfig;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AlongsidePairDetectorProcessFunction
    extends KeyedProcessFunction<String, PairProximityEvidence, AlongsideEvent> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AlongsidePairDetectorProcessFunction.class);

    private final AlongsideConfig config;
    private transient ValueState<PairState> pairState;
    private transient AlongsideRuleEngine ruleEngine;
    private transient AlongsideSceneAdjustmentService sceneAdjustmentService;
    private transient AlongsideRiskScorer riskScorer;
    private transient Counter rejectByNavStatus;
    private transient Counter weakCandidateCount;
    private transient Counter confirmedCandidateCount;
    private transient Counter downgradedNormalLikelyCount;
    private transient Counter emittedEventCount;

    public AlongsidePairDetectorProcessFunction(AlongsideConfig config) {
        this.config = config;
    }

    @Override
    public void open(Configuration parameters) {
        ValueStateDescriptor<PairState> descriptor =
            new ValueStateDescriptor<PairState>("alongside-pair-state", PairState.class);
        descriptor.enableTimeToLive(StateTtlConfig
            .newBuilder(Time.minutes(Math.max(1L, config.getPairTtlMinutes())))
            .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
            .cleanupFullSnapshot()
            .build());
        pairState = getRuntimeContext().getState(descriptor);
        ruleEngine = new AlongsideRuleEngineImpl();
        sceneAdjustmentService = new AlongsideSceneAdjustmentServiceImpl();
        riskScorer = new AlongsideRiskScorerImpl(
            config.getSeverityCapNoSceneDataMaxLevel(),
            config.isAutoL4Enabled()
        );
        rejectByNavStatus = getRuntimeContext().getMetricGroup().counter("rejectByNavStatus");
        weakCandidateCount = getRuntimeContext().getMetricGroup().counter("weakCandidateCount");
        confirmedCandidateCount = getRuntimeContext().getMetricGroup().counter("confirmedCandidateCount");
        downgradedNormalLikelyCount = getRuntimeContext().getMetricGroup().counter("downgradedNormalLikelyCount");
        emittedEventCount = getRuntimeContext().getMetricGroup().counter("emittedEventCount");
    }

    @Override
    public void processElement(PairProximityEvidence evidence, Context ctx, Collector<AlongsideEvent> out)
        throws Exception {
        if (evidence == null || evidence.getPairId() == null || evidence.getEvidenceTime() == null) {
            return;
        }
        PairState state = pairState.value();
        if (state != null && shouldSplit(state, evidence)) {
            AlongsideEvent end = buildEndEventIfNeeded(state, config);
            if (end != null) {
                out.collect(end);
                emittedEventCount.inc();
                LOG.info("ALONGSIDE_REALTIME event pair_id={} event_sub_type={} duration_minutes={}",
                    end.getPairId(), end.getEventSubType(), end.getDurationMinutes());
            }
            LOG.debug("ALONGSIDE_REALTIME state_summary action=split pair_id={} last_time={} next_time={}",
                state.pairId, state.lastEvidenceTime, evidence.getEvidenceTime());
            state = new PairState();
        } else if (state == null) {
            state = new PairState();
        }
        addEvidence(state, evidence, config);
        AlongsideEvent event = buildEventIfReady(state, config, ruleEngine, sceneAdjustmentService, riskScorer);
        if (event != null) {
            out.collect(event);
            emittedEventCount.inc();
            markEmitted(state, event);
            if (AlongsideRuleEngineImpl.SUB_TYPE_CONFIRMED.equals(event.getEventSubType())) {
                confirmedCandidateCount.inc();
            } else if (AlongsideRuleEngineImpl.SUB_TYPE_WEAK.equals(event.getEventSubType())) {
                weakCandidateCount.inc();
            }
            if ("DOWNGRADED_NORMAL_LIKELY".equals(event.getExclusionFlag())) {
                downgradedNormalLikelyCount.inc();
                rejectByNavStatus.inc();
            }
            LOG.info("ALONGSIDE_REALTIME event pair_id={} event_sub_type={} score={} severity={}",
                event.getPairId(), event.getEventSubType(), event.getEventScore(), event.getSeverityLevel());
        }
        pairState.update(state);
    }

    private boolean shouldSplit(PairState state, PairProximityEvidence evidence) {
        if (state.lastEvidenceTime == null) {
            return false;
        }
        long gapMinutes = Duration.between(state.lastEvidenceTime, evidence.getEvidenceTime()).toMinutes();
        if (gapMinutes < 0L) {
            return false;
        }
        return !config.isMergeEnabled() || gapMinutes > config.getMergeMaxGapMinutes();
    }

    private boolean shouldEmit(PairState state, AlongsideEvent event) {
        if (AlongsideRuleEngineImpl.SUB_TYPE_CONFIRMED.equals(event.getEventSubType())) {
            return !state.formalEmitted;
        }
        return !state.weakEmitted;
    }

    public static void addEvidence(PairState state, PairProximityEvidence evidence, AlongsideConfig config) {
        if (state.pointCount == 0) {
            state.pairId = evidence.getPairId();
            state.mmsiA = evidence.getMmsiA();
            state.mmsiB = evidence.getMmsiB();
            state.startTime = evidence.getEvidenceTime();
            state.startLon = midpoint(evidence.getLonA(), evidence.getLonB());
            state.startLat = midpoint(evidence.getLatA(), evidence.getLatB());
            state.minDistanceNm = safe(evidence.getDistanceNm());
            state.maxDistanceNm = safe(evidence.getDistanceNm());
            state.vesselA = evidence.getVesselA();
            state.vesselB = evidence.getVesselB();
            state.pairPriority = evidence.getPairPriority();
        }
        state.endTime = evidence.getEvidenceTime();
        state.endLon = midpoint(evidence.getLonA(), evidence.getLonB());
        state.endLat = midpoint(evidence.getLatA(), evidence.getLatB());
        state.lastEvidenceTime = evidence.getEvidenceTime();
        state.pointCount++;
        state.sumDistanceNm += safe(evidence.getDistanceNm());
        state.sumRelativeSpeedKnots += safe(evidence.getRelativeSpeedKnots());
        state.sumCenterLon += safe(midpoint(evidence.getLonA(), evidence.getLonB()));
        state.sumCenterLat += safe(midpoint(evidence.getLatA(), evidence.getLatB()));
        state.minDistanceNm = Math.min(state.minDistanceNm, safe(evidence.getDistanceNm()));
        state.maxDistanceNm = Math.max(state.maxDistanceNm, safe(evidence.getDistanceNm()));
        state.maxSpeedKnots = Math.max(state.maxSpeedKnots,
            Math.max(safe(evidence.getSpeedA()), safe(evidence.getSpeedB())));
        state.maxTimeGapSeconds = Math.max(state.maxTimeGapSeconds, safeLong(evidence.getTimeGapSeconds()));
        if (safeLong(evidence.getTimeGapSeconds()) <= config.getConfirmHighConfidenceTimeGapMinutes() * 60L) {
            state.highTimeGapPointCount++;
        }
        if (evidence.getHeadingParallelDiff() != null) {
            state.headingKnownCount++;
            if (evidence.getHeadingParallelDiff() <= config.getHeadingParallelThresholdDegrees()) {
                state.headingAcceptedCount++;
            }
        }
        if (hasAnchoredOrMooredStatus(evidence.getNavigationStatusA())
            || hasAnchoredOrMooredStatus(evidence.getNavigationStatusB())) {
            state.anchoredOrMooredSeen = true;
        }
        if (state.previousOwnTime != null && evidence.getPointATime() != null) {
            long gap = Math.abs(Duration.between(state.previousOwnTime, evidence.getPointATime()).toMinutes());
            if (gap > config.getAisGapMinutes()) {
                state.aisGapCount++;
                state.maxAisGapMinutes = Math.max(state.maxAisGapMinutes, gap);
            }
        }
        state.previousOwnTime = evidence.getPointATime();
        if (config.getDbSinkConfig() != null && config.getDbSinkConfig().isEnabled()
            && config.getDbSinkConfig().isDetailEnabled()
            && config.getDbSinkConfig().getMaxDetailPointsPerEvent() > 0) {
            if (state.evidencePoints == null) {
                state.evidencePoints = new ArrayList<PairProximityEvidence>();
            }
            if (state.evidencePoints.size() < config.getDbSinkConfig().getMaxDetailPointsPerEvent()) {
                state.evidencePoints.add(evidence);
            }
        }
    }

    public static AlongsideEvent buildEventIfReady(PairState state, AlongsideConfig config) {
        return buildEventIfReady(state, config, new AlongsideRuleEngineImpl(),
            new AlongsideSceneAdjustmentServiceImpl(),
            new AlongsideRiskScorerImpl(config.getSeverityCapNoSceneDataMaxLevel(), config.isAutoL4Enabled()));
    }

    static AlongsideEvent buildEventIfReady(
        PairState state,
        AlongsideConfig config,
        AlongsideRuleEngine ruleEngine,
        AlongsideSceneAdjustmentService sceneAdjustmentService,
        AlongsideRiskScorer riskScorer
    ) {
        PairEvidenceWindow window = toWindow(state, config);
        AlongsideDetectionResult detection = ruleEngine.evaluate(window, config.toRuleConfig());
        if (!detection.isDetected()) {
            return null;
        }
        String eventSubType = resolveOutputSubType(state, detection, window, config);
        if (eventSubType == null) {
            return null;
        }
        AlongsideContext context = new AlongsideContext();
        context.setSceneDataAvailable(config.isSceneDataAvailable());
        context.setCounterpartyRiskAvailable(config.isCounterpartyRiskAvailable());
        SceneAdjustmentResult adjustment = config.isNavStatusDowngradeEnabled()
            ? sceneAdjustmentService.adjust(detection, context)
            : new SceneAdjustmentResult();
        context.setSceneAdjustment(adjustment);
        AlongsideScoreResult score = riskScorer.score(detection, context);
        AlongsideEvent event = new AlongsideEvent();
        event.setPairId(state.pairId);
        event.setEventType("ALONGSIDE_CANDIDATE");
        event.setEventSubType(eventSubType);
        event.setSourceMode("REALTIME_AUTO_BUCKET");
        event.setRuleVersion(config.getRuleVersion());
        event.setMmsiA(state.mmsiA);
        event.setMmsiB(state.mmsiB);
        event.setStartTime(state.startTime);
        event.setEndTime(state.endTime);
        event.setDurationMinutes(window.getDurationMinutes());
        event.setStartLon(state.startLon);
        event.setStartLat(state.startLat);
        event.setEndLon(state.endLon);
        event.setEndLat(state.endLat);
        event.setCenterLon(state.sumCenterLon / Math.max(1, state.pointCount));
        event.setCenterLat(state.sumCenterLat / Math.max(1, state.pointCount));
        event.setMinDistanceNm(window.getMinDistanceNm());
        event.setMaxDistanceNm(window.getMaxDistanceNm());
        event.setAvgDistanceNm(window.getAvgDistanceNm());
        event.setAvgRelativeSpeedKnots(window.getAvgRelativeSpeedKnots());
        event.setHeadingParallelRatio(window.getHeadingKnownCount() == 0
            ? null
            : window.getHeadingAcceptedCount() / (double) window.getHeadingKnownCount());
        event.setPointCount(state.pointCount);
        event.setPairPriority(window.getPairPriority() == null ? null : window.getPairPriority().name());
        event.setShipClassA(window.getVesselA() == null || window.getVesselA().getVesselClass() == null
            ? null : window.getVesselA().getVesselClass().name());
        event.setShipClassB(window.getVesselB() == null || window.getVesselB().getVesselClass() == null
            ? null : window.getVesselB().getVesselClass().name());
        event.setSeverityLevel(score.getSeverityLevel());
        event.setEventScore(score.getScore());
        event.setConfidenceLevel(score.getConfidenceLevel());
        event.setExclusionFlag(adjustment.getExclusionFlag());
        event.setExclusionReason(adjustment.getExclusionReason());
        event.setScoreDetailJson(score.getScoreDetailJson());
        event.setAisGapCount(state.aisGapCount);
        event.setMaxAisGapMinutes(state.maxAisGapMinutes);
        event.setTrackCoverageRatio(window.getTrackCoverageRatio());
        event.setEmitTime(Instant.now());
        event.setEvidencePoints(copyEvidence(state));
        AlongsideEventIdGenerator.ensureEventId(event);
        return event;
    }

    static AlongsideEvent buildEndEventIfNeeded(PairState state, AlongsideConfig config) {
        if (state == null || (!state.weakEmitted && !state.formalEmitted) || state.endEmitted) {
            return null;
        }
        PairEvidenceWindow window = toWindow(state, config);
        AlongsideEvent event = new AlongsideEvent();
        event.setPairId(state.pairId);
        event.setEventType("ALONGSIDE_CANDIDATE");
        event.setEventSubType(AlongsideRuleEngineImpl.SUB_TYPE_END);
        event.setSourceMode("REALTIME_AUTO_BUCKET");
        event.setRuleVersion(config.getRuleVersion());
        event.setMmsiA(state.mmsiA);
        event.setMmsiB(state.mmsiB);
        event.setStartTime(state.startTime);
        event.setEndTime(state.endTime);
        event.setDurationMinutes(window.getDurationMinutes());
        event.setStartLon(state.startLon);
        event.setStartLat(state.startLat);
        event.setEndLon(state.endLon);
        event.setEndLat(state.endLat);
        event.setCenterLon(state.sumCenterLon / Math.max(1, state.pointCount));
        event.setCenterLat(state.sumCenterLat / Math.max(1, state.pointCount));
        event.setMinDistanceNm(window.getMinDistanceNm());
        event.setMaxDistanceNm(window.getMaxDistanceNm());
        event.setAvgDistanceNm(window.getAvgDistanceNm());
        event.setAvgRelativeSpeedKnots(window.getAvgRelativeSpeedKnots());
        event.setHeadingParallelRatio(window.getHeadingKnownCount() == 0
            ? null
            : window.getHeadingAcceptedCount() / (double) window.getHeadingKnownCount());
        event.setPointCount(state.pointCount);
        event.setPairPriority(window.getPairPriority() == null ? null : window.getPairPriority().name());
        event.setShipClassA(window.getVesselA() == null || window.getVesselA().getVesselClass() == null
            ? null : window.getVesselA().getVesselClass().name());
        event.setShipClassB(window.getVesselB() == null || window.getVesselB().getVesselClass() == null
            ? null : window.getVesselB().getVesselClass().name());
        event.setSeverityLevel("L0");
        event.setEventScore(0.0d);
        event.setConfidenceLevel("LOW");
        event.setExclusionFlag("EVENT_ENDED");
        event.setExclusionReason("PAIR_EVIDENCE_GAP");
        event.setScoreDetailJson("{\"end_reason\":\"PAIR_EVIDENCE_GAP\"}");
        event.setAisGapCount(state.aisGapCount);
        event.setMaxAisGapMinutes(state.maxAisGapMinutes);
        event.setTrackCoverageRatio(window.getTrackCoverageRatio());
        event.setEmitTime(Instant.now());
        event.setEvidencePoints(copyEvidence(state));
        AlongsideEventIdGenerator.ensureEventId(event);
        state.endEmitted = true;
        return event;
    }

    private static List<PairProximityEvidence> copyEvidence(PairState state) {
        if (state.evidencePoints == null || state.evidencePoints.isEmpty()) {
            return new ArrayList<PairProximityEvidence>();
        }
        return new ArrayList<PairProximityEvidence>(state.evidencePoints);
    }

    private static String resolveOutputSubType(PairState state, AlongsideDetectionResult detection,
                                               PairEvidenceWindow window, AlongsideConfig config) {
        if (AlongsideRuleEngineImpl.SUB_TYPE_CONFIRMED.equals(detection.getEventSubType())) {
            if (!state.formalEmitted) {
                return AlongsideRuleEngineImpl.SUB_TYPE_CONFIRMED;
            }
            if (shouldOutputUpdate(state, window, config)) {
                return AlongsideRuleEngineImpl.SUB_TYPE_UPDATE;
            }
            return null;
        }
        if (state.weakEmitted) {
            return null;
        }
        return AlongsideRuleEngineImpl.SUB_TYPE_WEAK;
    }

    private static boolean shouldOutputUpdate(PairState state, PairEvidenceWindow window, AlongsideConfig config) {
        if (state.lastEmittedEventTime == null || window.getEndTime() == null) {
            return true;
        }
        long gapMinutes = Duration.between(state.lastEmittedEventTime, window.getEndTime()).toMinutes();
        return gapMinutes >= Math.max(1L, config.getOutputUpdateIntervalMinutes());
    }

    static void markEmitted(PairState state, AlongsideEvent event) {
        if (state == null || event == null) {
            return;
        }
        if (AlongsideRuleEngineImpl.SUB_TYPE_CONFIRMED.equals(event.getEventSubType())) {
            state.formalEmitted = true;
        } else if (AlongsideRuleEngineImpl.SUB_TYPE_WEAK.equals(event.getEventSubType())) {
            state.weakEmitted = true;
        }
        state.lastEmittedEventTime = event.getEndTime();
    }

    static PairEvidenceWindow toWindow(PairState state, AlongsideConfig config) {
        PairEvidenceWindow window = new PairEvidenceWindow();
        window.setPairId(state.pairId);
        window.setMmsiA(state.mmsiA);
        window.setMmsiB(state.mmsiB);
        window.setStartTime(state.startTime);
        window.setEndTime(state.endTime);
        window.setStartLon(state.startLon);
        window.setStartLat(state.startLat);
        window.setEndLon(state.endLon);
        window.setEndLat(state.endLat);
        window.setPointCount(state.pointCount);
        window.setMinDistanceNm(state.minDistanceNm);
        window.setMaxDistanceNm(state.maxDistanceNm);
        window.setAvgDistanceNm(state.sumDistanceNm / Math.max(1, state.pointCount));
        window.setAvgRelativeSpeedKnots(state.sumRelativeSpeedKnots / Math.max(1, state.pointCount));
        window.setMaxSpeedKnots(state.maxSpeedKnots);
        window.setDurationMinutes(durationMinutes(state));
        window.setMaxTimeGapSeconds(state.maxTimeGapSeconds);
        window.setHighTimeGapPointCount(state.highTimeGapPointCount);
        window.setHeadingKnownCount(state.headingKnownCount);
        window.setHeadingAcceptedCount(state.headingAcceptedCount);
        window.setAisGapCount(state.aisGapCount);
        window.setMaxAisGapMinutes(state.maxAisGapMinutes);
        window.setTrackCoverageRatio(calculateCoverage(window.getDurationMinutes(), state.pointCount, config.getAisGapMinutes()));
        window.setAnchoredOrMooredSeen(state.anchoredOrMooredSeen);
        window.setVesselA(state.vesselA);
        window.setVesselB(state.vesselB);
        window.setPairPriority(state.pairPriority);
        return window;
    }

    private static double calculateCoverage(long durationMinutes, int pointCount, long maxGapMinutes) {
        long expected = Math.max(1L, durationMinutes / Math.max(1L, maxGapMinutes) + 1L);
        return Math.min(1.0d, pointCount / (double) expected);
    }

    private static long durationMinutes(PairState state) {
        if (state.startTime == null || state.endTime == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(state.startTime, state.endTime).toMinutes());
    }

    private static boolean hasAnchoredOrMooredStatus(Integer status) {
        return status != null && (status.intValue() == 1 || status.intValue() == 5);
    }

    private static Double midpoint(Double a, Double b) {
        if (a == null || b == null) {
            return null;
        }
        return (a + b) / 2.0d;
    }

    private static double safe(Double value) {
        return value == null ? 0.0d : value.doubleValue();
    }

    private static long safeLong(Long value) {
        return value == null ? 0L : value.longValue();
    }

    public static class PairState implements Serializable {

        private static final long serialVersionUID = 1L;

        private String pairId;
        private Long mmsiA;
        private Long mmsiB;
        private Instant startTime;
        private Instant endTime;
        private Instant lastEvidenceTime;
        private Instant previousOwnTime;
        private Double startLon;
        private Double startLat;
        private Double endLon;
        private Double endLat;
        private int pointCount;
        private double minDistanceNm;
        private double maxDistanceNm;
        private double maxSpeedKnots;
        private double sumDistanceNm;
        private double sumRelativeSpeedKnots;
        private double sumCenterLon;
        private double sumCenterLat;
        private long maxTimeGapSeconds;
        private int highTimeGapPointCount;
        private int headingKnownCount;
        private int headingAcceptedCount;
        private int aisGapCount;
        private long maxAisGapMinutes;
        private boolean anchoredOrMooredSeen;
        private boolean weakEmitted;
        private boolean formalEmitted;
        private boolean endEmitted;
        private Instant lastEmittedEventTime;
        private com.hifleet.analysis.alongside.core.model.VesselProfile vesselA;
        private com.hifleet.analysis.alongside.core.model.VesselProfile vesselB;
        private com.hifleet.analysis.alongside.core.model.PairPriority pairPriority;
        private List<PairProximityEvidence> evidencePoints;
    }
}
