package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.CleanAisPoint;
import com.hifleet.analysis.portcall.model.StopPointEvent;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Per-MMSI state machine that emits throttled stop-state events ready for persistence.
 */
public class StopPointDetectorProcessFunction extends KeyedProcessFunction<Long, CleanAisPoint, StopPointEvent> {

    private static final long serialVersionUID = 1L;

    private final double candidateMaxSpeedKnots;
    private final long candidateMinDurationSeconds;
    private final int candidateMinPointCount;
    private final double candidateMaxNetDisplacementMeters;
    private final long stableMinDurationSeconds;
    private final int stableMinPointCount;
    private final double stableMaxSpeedKnots;
    private final double stableMaxRadiusMeters;
    private final double stableCenterDeltaMeters;
    private final double positionOutlierRadiusMeters;
    private final double resumeMinSpeedKnots;
    private final long resumeMinDurationSeconds;
    private final double resumeLeaveRadiusMeters;
    private final long updateMinIntervalSeconds;
    private final double updateMinPositionDeltaMeters;
    private final double updateMinConfidenceDelta;
    private final long unknownTimeoutSeconds;
    private final int stateIdleTtlHours;

    private transient ValueState<DetectorState> detectorState;

    public StopPointDetectorProcessFunction(
        double candidateMaxSpeedKnots,
        long candidateMinDurationSeconds,
        int candidateMinPointCount,
        double candidateMaxNetDisplacementMeters,
        long stableMinDurationSeconds,
        int stableMinPointCount,
        double stableMaxSpeedKnots,
        double stableMaxRadiusMeters,
        double stableCenterDeltaMeters,
        double positionOutlierRadiusMeters,
        double resumeMinSpeedKnots,
        long resumeMinDurationSeconds,
        double resumeLeaveRadiusMeters,
        long updateMinIntervalSeconds,
        double updateMinPositionDeltaMeters,
        double updateMinConfidenceDelta,
        long unknownTimeoutSeconds,
        int stateIdleTtlHours
    ) {
        this.candidateMaxSpeedKnots = candidateMaxSpeedKnots;
        this.candidateMinDurationSeconds = candidateMinDurationSeconds;
        this.candidateMinPointCount = candidateMinPointCount;
        this.candidateMaxNetDisplacementMeters = candidateMaxNetDisplacementMeters;
        this.stableMinDurationSeconds = stableMinDurationSeconds;
        this.stableMinPointCount = stableMinPointCount;
        this.stableMaxSpeedKnots = stableMaxSpeedKnots;
        this.stableMaxRadiusMeters = stableMaxRadiusMeters;
        this.stableCenterDeltaMeters = stableCenterDeltaMeters;
        this.positionOutlierRadiusMeters = positionOutlierRadiusMeters;
        this.resumeMinSpeedKnots = resumeMinSpeedKnots;
        this.resumeMinDurationSeconds = resumeMinDurationSeconds;
        this.resumeLeaveRadiusMeters = resumeLeaveRadiusMeters;
        this.updateMinIntervalSeconds = updateMinIntervalSeconds;
        this.updateMinPositionDeltaMeters = updateMinPositionDeltaMeters;
        this.updateMinConfidenceDelta = updateMinConfidenceDelta;
        this.unknownTimeoutSeconds = unknownTimeoutSeconds;
        this.stateIdleTtlHours = stateIdleTtlHours;
    }

    @Override
    public void open(Configuration parameters) {
        ValueStateDescriptor<DetectorState> descriptor =
            new ValueStateDescriptor<DetectorState>("stop-detector-state", DetectorState.class);
        StateTtlConfig ttlConfig = stateIdleTtlConfig();
        if (ttlConfig != null) {
            descriptor.enableTimeToLive(ttlConfig);
        }
        detectorState = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void processElement(CleanAisPoint point, Context ctx, Collector<StopPointEvent> out) throws Exception {
        if (point == null || !point.isUsable() || point.getUpdateTime() == null || !point.hasValidPosition()) {
            return;
        }

        DetectorState state = detectorState.value();
        if (state == null) {
            state = new DetectorState();
        }

        StopPointEvent.VesselState previousMode = state.mode;
        registerUnknownTimer(state, point, ctx);
        updateLastSeen(state, point);

        if (state.mode == StopPointEvent.VesselState.UNKNOWN) {
            state.mode = StopPointEvent.VesselState.SAILING;
            out.collect(buildSailingStart(state, point));
        }

        if (state.mode == null) {
            if (isCandidateLowSpeed(point)) {
                startCandidate(state, point);
            } else {
                state.mode = StopPointEvent.VesselState.SAILING;
                out.collect(buildSailingStart(state, point));
            }
            detectorState.update(state);
            return;
        }

        if (state.mode == StopPointEvent.VesselState.STOPPED) {
            handleStopped(state, point, out);
            detectorState.update(state);
            return;
        }

        if (isCandidateLowSpeed(point)) {
            if (state.mode != StopPointEvent.VesselState.STOP_CANDIDATE) {
                startCandidate(state, point);
            } else {
                addCandidatePoint(state, point);
            }
            handleCandidateProgress(state, point, out);
        } else {
            if (previousMode == StopPointEvent.VesselState.STOP_CANDIDATE) {
                resetToSailing(state);
                out.collect(buildSailingStart(state, point));
            }
        }
        detectorState.update(state);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<StopPointEvent> out) throws Exception {
        DetectorState state = detectorState.value();
        if (state == null || state.lastAisUpdateTime == null || unknownTimeoutSeconds <= 0L) {
            return;
        }
        if (state.activeUnknownTimerMillis == null || timestamp != state.activeUnknownTimerMillis.longValue()) {
            return;
        }
        long expectedUnknownAt = state.lastAisUpdateTime.plusSeconds(unknownTimeoutSeconds).toEpochMilli();
        if (timestamp < expectedUnknownAt || state.mode == StopPointEvent.VesselState.UNKNOWN) {
            state.activeUnknownTimerMillis = null;
            detectorState.update(state);
            return;
        }
        state.activeUnknownTimerMillis = null;
        state.mode = StopPointEvent.VesselState.UNKNOWN;
        StopPointEvent event = baseEvent(state, StopPointEvent.EventType.UNKNOWN, StopPointEvent.VesselState.UNKNOWN);
        event.setMessageId(String.valueOf(state.mmsi) + "-unknown-" + timestamp);
        event.setDbAction("UPSERT");
        event.setConfidence(0.0d);
        out.collect(event);
        detectorState.update(state);
    }

    private void registerUnknownTimer(DetectorState state, CleanAisPoint point, Context ctx) throws Exception {
        if (unknownTimeoutSeconds <= 0L) {
            return;
        }
        long nextTimerMillis = point.getUpdateTime().plusSeconds(unknownTimeoutSeconds).toEpochMilli();
        if (state.activeUnknownTimerMillis != null
            && state.activeUnknownTimerMillis.longValue() == nextTimerMillis) {
            return;
        }
        if (state.activeUnknownTimerMillis != null) {
            ctx.timerService().deleteEventTimeTimer(state.activeUnknownTimerMillis.longValue());
        }
        ctx.timerService().registerEventTimeTimer(nextTimerMillis);
        state.activeUnknownTimerMillis = nextTimerMillis;
    }

    private void handleCandidateProgress(DetectorState state, CleanAisPoint point, Collector<StopPointEvent> out) {
        if (state.candidateMaxDistanceFromStartMeters > candidateMaxNetDisplacementMeters) {
            startCandidate(state, point);
            return;
        }

        if (isCandidateReady(state) && !state.candidateEventEmitted) {
            state.candidateEventEmitted = true;
            out.collect(buildCandidateStart(state));
        }

        if (isStablePoint(state, point)) {
            addStablePoint(state, point);
        }

        if (isStableReady(state)) {
            state.mode = StopPointEvent.VesselState.STOPPED;
            state.stopId = "stop-" + state.mmsi + "-" + state.candidateStartTime.toEpochMilli();
            StopPointEvent event = buildStopEvent(state, StopPointEvent.EventType.STOP_START, StopPointEvent.PositionStatus.STABLE);
            state.lastOutputTime = point.getUpdateTime();
            state.lastOutputLon = state.stableCenterLon;
            state.lastOutputLat = state.stableCenterLat;
            state.lastOutputConfidence = event.getConfidence();
            out.collect(event);
        }
    }

    private void handleStopped(DetectorState state, CleanAisPoint point, Collector<StopPointEvent> out) {
        if (isStablePoint(state, point)) {
            addStablePoint(state, point);
            state.resumeStartTime = null;
        } else if (isResumePoint(state, point)) {
            if (state.resumeStartTime == null) {
                state.resumeStartTime = point.getUpdateTime();
            }
        } else {
            state.resumeStartTime = null;
        }

        if (state.resumeStartTime != null
            && secondsBetween(state.resumeStartTime, point.getUpdateTime()) >= resumeMinDurationSeconds) {
            StopPointEvent end = buildStopEvent(state, StopPointEvent.EventType.STOP_END, StopPointEvent.PositionStatus.FINAL);
            end.setDbAction("FINALIZE");
            end.setStopEndTime(point.getUpdateTime());
            end.setResumeSailingTime(point.getUpdateTime());
            end.setDurationSeconds(secondsBetween(state.candidateStartTime, point.getUpdateTime()));
            out.collect(end);
            resetToSailing(state);
            out.collect(buildSailingStart(state, point));
            return;
        }

        StopPointEvent updateProbe = buildStopEvent(state, StopPointEvent.EventType.STOP_UPDATE, StopPointEvent.PositionStatus.STABLE);
        if (shouldEmitUpdate(state, point, updateProbe.getConfidence())) {
            state.lastOutputTime = point.getUpdateTime();
            state.lastOutputLon = state.stableCenterLon;
            state.lastOutputLat = state.stableCenterLat;
            state.lastOutputConfidence = updateProbe.getConfidence();
            out.collect(updateProbe);
        }
    }

    private boolean shouldEmitUpdate(DetectorState state, CleanAisPoint point, double confidence) {
        if (state.lastOutputTime == null) {
            return true;
        }
        long secondsSinceLastOutput = secondsBetween(state.lastOutputTime, point.getUpdateTime());
        if (secondsSinceLastOutput >= updateMinIntervalSeconds) {
            return true;
        }
        if (state.lastOutputLon != null && state.lastOutputLat != null
            && distanceMeters(state.lastOutputLat, state.lastOutputLon, state.stableCenterLat, state.stableCenterLon)
            >= updateMinPositionDeltaMeters) {
            return true;
        }
        return Math.abs(confidence - state.lastOutputConfidence) >= updateMinConfidenceDelta;
    }

    private boolean isCandidateReady(DetectorState state) {
        return state.candidatePointCount >= candidateMinPointCount
            && secondsBetween(state.candidateStartTime, state.lastAisUpdateTime) >= candidateMinDurationSeconds
            && state.candidateMaxDistanceFromStartMeters <= candidateMaxNetDisplacementMeters;
    }

    private boolean isStableReady(DetectorState state) {
        return state.stablePointCount >= stableMinPointCount
            && secondsBetween(state.stableStartTime, state.lastAisUpdateTime) >= stableMinDurationSeconds
            && state.stableMaxSpeedKnots <= stableMaxSpeedKnots
            && state.stableSwingRadiusMeters <= stableMaxRadiusMeters;
    }

    private boolean isCandidateLowSpeed(CleanAisPoint point) {
        return point.getSpeed() != null && point.getSpeed() <= candidateMaxSpeedKnots;
    }

    private boolean isStablePoint(DetectorState state, CleanAisPoint point) {
        if (point.getSpeed() == null || point.getSpeed() > stableMaxSpeedKnots) {
            return false;
        }
        if (state.stablePointCount == 0) {
            return true;
        }
        double distanceToCenter = distanceMeters(state.stableCenterLat, state.stableCenterLon, point.getLat(), point.getLon());
        return distanceToCenter <= positionOutlierRadiusMeters;
    }

    private boolean isResumePoint(DetectorState state, CleanAisPoint point) {
        if (point.getSpeed() != null && point.getSpeed() >= resumeMinSpeedKnots) {
            return true;
        }
        if (state.stablePointCount == 0) {
            return false;
        }
        return distanceMeters(state.stableCenterLat, state.stableCenterLon, point.getLat(), point.getLon()) >= resumeLeaveRadiusMeters;
    }

    private void startCandidate(DetectorState state, CleanAisPoint point) {
        state.mode = StopPointEvent.VesselState.STOP_CANDIDATE;
        state.candidateEventEmitted = false;
        state.candidateStartTime = point.getUpdateTime();
        state.candidateStartLon = point.getLon();
        state.candidateStartLat = point.getLat();
        state.candidatePointCount = 0;
        state.candidateMaxDistanceFromStartMeters = 0.0d;
        resetStable(state);
        addCandidatePoint(state, point);
    }

    private void addCandidatePoint(DetectorState state, CleanAisPoint point) {
        state.candidatePointCount++;
        double distance = distanceMeters(state.candidateStartLat, state.candidateStartLon, point.getLat(), point.getLon());
        if (distance > state.candidateMaxDistanceFromStartMeters) {
            state.candidateMaxDistanceFromStartMeters = distance;
        }
    }

    private void addStablePoint(DetectorState state, CleanAisPoint point) {
        if (state.stablePointCount == 0) {
            state.stableStartTime = point.getUpdateTime();
            state.stableCenterLon = point.getLon();
            state.stableCenterLat = point.getLat();
        } else {
            double centerMove = distanceMeters(state.stableCenterLat, state.stableCenterLon, point.getLat(), point.getLon());
            if (centerMove > positionOutlierRadiusMeters) {
                return;
            }
        }
        state.stablePointCount++;
        state.stableSumLon += point.getLon();
        state.stableSumLat += point.getLat();
        state.stableSumSpeedKnots += point.getSpeed() == null ? 0.0d : point.getSpeed();
        if (point.getSpeed() != null && point.getSpeed() > state.stableMaxSpeedKnots) {
            state.stableMaxSpeedKnots = point.getSpeed();
        }
        state.stableCenterLon = state.stableSumLon / state.stablePointCount;
        state.stableCenterLat = state.stableSumLat / state.stablePointCount;
        double swing = distanceMeters(state.stableCenterLat, state.stableCenterLon, point.getLat(), point.getLon());
        if (swing > state.stableSwingRadiusMeters) {
            state.stableSwingRadiusMeters = swing;
        }
    }

    private StopPointEvent buildCandidateStart(DetectorState state) {
        StopPointEvent event = baseEvent(state, StopPointEvent.EventType.STOP_CANDIDATE_START, StopPointEvent.VesselState.STOP_CANDIDATE);
        event.setMessageId(String.valueOf(state.mmsi) + "-candidate-" + state.candidateStartTime.toEpochMilli());
        event.setStopStartTime(state.candidateStartTime);
        event.setDurationSeconds(secondsBetween(state.candidateStartTime, state.lastAisUpdateTime));
        event.setConfidence(0.5d);
        return event;
    }

    private StopPointEvent buildStopEvent(
        DetectorState state,
        StopPointEvent.EventType eventType,
        StopPointEvent.PositionStatus positionStatus
    ) {
        StopPointEvent event = baseEvent(state, eventType, eventType == StopPointEvent.EventType.STOP_END
            ? StopPointEvent.VesselState.SAILING : StopPointEvent.VesselState.STOPPED);
        event.setStopId(state.stopId);
        event.setStopStartTime(state.candidateStartTime);
        event.setStableStartTime(state.stableStartTime);
        event.setStopLon(state.stableCenterLon);
        event.setStopLat(state.stableCenterLat);
        event.setPositionStatus(positionStatus);
        event.setPositionPointCount(state.stablePointCount);
        event.setSwingRadiusMeters(state.stableSwingRadiusMeters);
        event.setDurationSeconds(secondsBetween(state.candidateStartTime, state.lastAisUpdateTime));
        event.setAverageSpeedKnots(state.stablePointCount == 0 ? 0.0d : state.stableSumSpeedKnots / state.stablePointCount);
        event.setMaxSpeedKnots(state.stableMaxSpeedKnots);
        event.setConfidence(confidence(state));
        event.setMessageId(state.mmsi + "-" + state.stopId + "-" + eventType.name().toLowerCase()
            + "-" + state.lastAisUpdateTime.toEpochMilli());
        return event;
    }

    private StopPointEvent buildSailingStart(DetectorState state, CleanAisPoint point) {
        StopPointEvent event = baseEvent(state, StopPointEvent.EventType.SAILING_START, StopPointEvent.VesselState.SAILING);
        event.setMessageId(String.valueOf(point.getMmsi()) + "-sailing-" + point.getUpdateTime().toEpochMilli());
        return event;
    }

    private StopPointEvent baseEvent(
        DetectorState state,
        StopPointEvent.EventType eventType,
        StopPointEvent.VesselState vesselState
    ) {
        StopPointEvent event = new StopPointEvent();
        event.setEventType(eventType);
        event.setVesselState(vesselState);
        event.setMmsi(state.mmsi);
        event.setImoNumber(state.imoNumber);
        event.setShipName(state.shipName);
        event.setLastAisUpdateTime(state.lastAisUpdateTime);
        event.setLastServerReceiveTime(state.lastServerReceiveTime);
        event.setEmitTime(Instant.now());
        event.setDataState(state.dataState);
        return event;
    }

    private double confidence(DetectorState state) {
        long duration = secondsBetween(state.candidateStartTime, state.lastAisUpdateTime);
        double durationScore = Math.min(1.0d, duration / (double) Math.max(1L, stableMinDurationSeconds * 2L));
        double pointScore = Math.min(1.0d, state.stablePointCount / (double) Math.max(1, stableMinPointCount * 2));
        double swingScore = 1.0d - Math.min(1.0d, state.stableSwingRadiusMeters / Math.max(1.0d, stableMaxRadiusMeters));
        return Math.max(0.6d, Math.min(0.99d, 0.45d + durationScore * 0.25d + pointScore * 0.20d + swingScore * 0.10d));
    }

    private void updateLastSeen(DetectorState state, CleanAisPoint point) {
        state.mmsi = point.getMmsi();
        if (state.imoNumber == null && point.getImoNumber() != null) {
            state.imoNumber = point.getImoNumber();
        }
        if (point.getShipName() != null) {
            state.shipName = point.getShipName();
        }
        state.lastAisUpdateTime = point.getUpdateTime();
        state.lastServerReceiveTime = point.getServerReceiveTime();
        state.dataState = point.getDataState();
    }

    private void resetToSailing(DetectorState state) {
        state.mode = StopPointEvent.VesselState.SAILING;
        state.stopId = null;
        state.candidateEventEmitted = false;
        state.resumeStartTime = null;
        state.lastOutputTime = null;
        state.lastOutputLon = null;
        state.lastOutputLat = null;
        state.lastOutputConfidence = 0.0d;
        resetCandidate(state);
        resetStable(state);
    }

    private void resetCandidate(DetectorState state) {
        state.candidateStartTime = null;
        state.candidateStartLon = 0.0d;
        state.candidateStartLat = 0.0d;
        state.candidatePointCount = 0;
        state.candidateMaxDistanceFromStartMeters = 0.0d;
    }

    private void resetStable(DetectorState state) {
        state.stableStartTime = null;
        state.stablePointCount = 0;
        state.stableSumLon = 0.0d;
        state.stableSumLat = 0.0d;
        state.stableSumSpeedKnots = 0.0d;
        state.stableMaxSpeedKnots = 0.0d;
        state.stableCenterLon = 0.0d;
        state.stableCenterLat = 0.0d;
        state.stableSwingRadiusMeters = 0.0d;
    }

    private long secondsBetween(Instant start, Instant end) {
        if (start == null || end == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(start, end).getSeconds());
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final double radius = 6371000.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2.0d) * Math.sin(dLon / 2.0d);
        double c = 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
        return radius * c;
    }

    private StateTtlConfig stateIdleTtlConfig() {
        if (stateIdleTtlHours <= 0) {
            return null;
        }
        return StateTtlConfig
            .newBuilder(Time.hours(stateIdleTtlHours))
            .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
            .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
            .cleanupFullSnapshot()
            .cleanupInRocksdbCompactFilter(1000)
            .build();
    }

    /**
     * Compact keyed state for one vessel.
     */
    public static class DetectorState implements Serializable {
        private static final long serialVersionUID = 1L;

        private StopPointEvent.VesselState mode;
        private Long mmsi;
        private String imoNumber;
        private String shipName;
        private String dataState;
        private Instant lastAisUpdateTime;
        private Instant lastServerReceiveTime;

        private String stopId;
        private boolean candidateEventEmitted;
        private Instant candidateStartTime;
        private double candidateStartLon;
        private double candidateStartLat;
        private int candidatePointCount;
        private double candidateMaxDistanceFromStartMeters;

        private Instant stableStartTime;
        private int stablePointCount;
        private double stableSumLon;
        private double stableSumLat;
        private double stableSumSpeedKnots;
        private double stableMaxSpeedKnots;
        private double stableCenterLon;
        private double stableCenterLat;
        private double stableSwingRadiusMeters;

        private Instant resumeStartTime;
        private Instant lastOutputTime;
        private Double lastOutputLon;
        private Double lastOutputLat;
        private double lastOutputConfidence;
        private Long activeUnknownTimerMillis;

        public DetectorState() {
        }
    }
}
