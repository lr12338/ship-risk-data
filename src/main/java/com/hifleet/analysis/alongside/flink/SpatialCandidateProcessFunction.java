package com.hifleet.analysis.alongside.flink;

import com.hifleet.analysis.alongside.core.classifier.AlongsidePairPriorityClassifier;
import com.hifleet.analysis.alongside.core.classifier.AlongsidePairPriorityClassifierImpl;
import com.hifleet.analysis.alongside.core.classifier.VesselTypeClassifierImpl;
import com.hifleet.analysis.alongside.core.model.PairPriority;
import com.hifleet.analysis.alongside.core.model.PairProximityEvidence;
import com.hifleet.analysis.alongside.core.model.VesselClass;
import com.hifleet.analysis.alongside.core.model.VesselProfile;
import com.hifleet.analysis.alongside.core.model.VesselType;
import com.hifleet.analysis.alongside.flink.bucket.SpatialBucketedAisPoint;
import com.hifleet.analysis.portcall.config.AlongsideConfig;
import com.hifleet.analysis.portcall.model.CleanAisPoint;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpatialCandidateProcessFunction
    extends KeyedProcessFunction<String, SpatialBucketedAisPoint, PairProximityEvidence> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(SpatialCandidateProcessFunction.class);

    private final AlongsideConfig config;
    private transient MapState<String, CleanAisPoint> recentPoints;
    private transient VesselTypeClassifierImpl vesselTypeClassifier;
    private transient AlongsidePairPriorityClassifier pairPriorityClassifier;
    private transient Counter rejectByInvalidAis;
    private transient Counter rejectByShipType;
    private transient Counter rejectByDistance;
    private transient Counter rejectByTimeGap;
    private transient Counter rejectBySameVessel;
    private transient Counter rejectByPairPriority;
    private transient Counter candidateEvidenceCount;

    public SpatialCandidateProcessFunction(AlongsideConfig config) {
        this.config = config;
    }

    @Override
    public void open(Configuration parameters) {
        MapStateDescriptor<String, CleanAisPoint> descriptor =
            new MapStateDescriptor<String, CleanAisPoint>("alongside-bucket-recent", String.class, CleanAisPoint.class);
        descriptor.enableTimeToLive(StateTtlConfig
            .newBuilder(Time.minutes(Math.max(1L, config.getBucketPointTtlMinutes())))
            .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
            .cleanupFullSnapshot()
            .build());
        recentPoints = getRuntimeContext().getMapState(descriptor);
        vesselTypeClassifier = new VesselTypeClassifierImpl(
            config.getHighPriorityShipTypes(),
            config.getNormalServiceShipTypes(),
            config.getLowPriorityShipTypes()
        );
        pairPriorityClassifier = new AlongsidePairPriorityClassifierImpl();
        rejectByInvalidAis = getRuntimeContext().getMetricGroup().counter("rejectByInvalidAis");
        rejectByShipType = getRuntimeContext().getMetricGroup().counter("rejectByShipType");
        rejectByDistance = getRuntimeContext().getMetricGroup().counter("rejectByDistance");
        rejectByTimeGap = getRuntimeContext().getMetricGroup().counter("rejectByTimeGap");
        rejectBySameVessel = getRuntimeContext().getMetricGroup().counter("rejectBySameVessel");
        rejectByPairPriority = getRuntimeContext().getMetricGroup().counter("rejectByPairPriority");
        candidateEvidenceCount = getRuntimeContext().getMetricGroup().counter("candidateEvidenceCount");
    }

    @Override
    public void processElement(SpatialBucketedAisPoint value, Context ctx, Collector<PairProximityEvidence> out)
        throws Exception {
        if (value == null || value.getPoint() == null || value.getPoint().getMmsi() == null
            || value.getPoint().getUpdateTime() == null) {
            return;
        }
        CleanAisPoint point = value.getPoint();
        if (value.getPrimaryBucketId() != null && value.getPrimaryBucketId().equals(value.getBucketId())) {
            recentPoints.put(pointKey(point), point);
        }
        List<PairProximityEvidence> evidence = findNearestEvidence(
            point,
            recentPoints.values(),
            config,
            vesselTypeClassifier,
            pairPriorityClassifier,
            new CandidateMetrics(
                rejectByInvalidAis,
                rejectByShipType,
                rejectByDistance,
                rejectByTimeGap,
                rejectBySameVessel,
                rejectByPairPriority,
                candidateEvidenceCount
            )
        );
        for (PairProximityEvidence item : evidence) {
            LOG.debug("ALONGSIDE_REALTIME pair_evidence pair_id={} distance_nm={} time_gap_seconds={} priority={}",
                item.getPairId(), item.getDistanceNm(), item.getTimeGapSeconds(), item.getPairPriority());
            out.collect(item);
        }
        LOG.debug("ALONGSIDE_REALTIME bucket_summary bucket_id={} primary_bucket_id={} mmsi={} evidence_count={}",
            value.getBucketId(), value.getPrimaryBucketId(), point.getMmsi(), evidence.size());
    }

    public static List<PairProximityEvidence> findNearestEvidence(
        CleanAisPoint point,
        Iterable<CleanAisPoint> candidates,
        AlongsideConfig config
    ) {
        return findNearestEvidence(point, candidates, config,
            new VesselTypeClassifierImpl(
                config.getHighPriorityShipTypes(),
                config.getNormalServiceShipTypes(),
                config.getLowPriorityShipTypes()
            ),
            new AlongsidePairPriorityClassifierImpl(),
            null);
    }

    static List<PairProximityEvidence> findNearestEvidence(
        CleanAisPoint point,
        Iterable<CleanAisPoint> candidates,
        AlongsideConfig config,
        VesselTypeClassifierImpl vesselTypeClassifier,
        AlongsidePairPriorityClassifier pairPriorityClassifier,
        CandidateMetrics metrics
    ) {
        List<PairProximityEvidence> evidence = new ArrayList<PairProximityEvidence>();
        if (point == null || candidates == null) {
            return evidence;
        }
        List<CleanAisPoint> candidateList = new ArrayList<CleanAisPoint>();
        for (CleanAisPoint candidate : candidates) {
            candidateList.add(candidate);
        }
        for (CleanAisPoint other : candidateList) {
            PairProximityEvidence item = buildEvidence(point, other, config, vesselTypeClassifier, pairPriorityClassifier, metrics);
            if (item == null) {
                continue;
            }
            item.setNearbyStationaryVesselCount(nearbyStationaryVesselCount(point, item, candidateList, config));
            PairProximityEvidence previous = findEvidence(evidence, other.getMmsi());
            if (previous == null) {
                evidence.add(item);
            } else if (item.getTimeGapSeconds() != null && previous.getTimeGapSeconds() != null
                && item.getTimeGapSeconds() < previous.getTimeGapSeconds()) {
                evidence.remove(previous);
                evidence.add(item);
            }
        }
        return evidence;
    }

    private static int nearbyStationaryVesselCount(CleanAisPoint point, PairProximityEvidence evidence,
                                                   List<CleanAisPoint> candidates, AlongsideConfig config) {
        if (point == null || evidence == null || candidates == null || config == null) {
            return 0;
        }
        Double centerLon = midpoint(evidence.getLonA(), evidence.getLonB());
        Double centerLat = midpoint(evidence.getLatA(), evidence.getLatB());
        if (centerLon == null || centerLat == null || evidence.getEvidenceTime() == null) {
            return 0;
        }
        Set<Long> mmsis = new HashSet<Long>();
        addStationaryNearby(mmsis, point, centerLat, centerLon, evidence.getEvidenceTime(), config);
        for (CleanAisPoint candidate : candidates) {
            addStationaryNearby(mmsis, candidate, centerLat, centerLon, evidence.getEvidenceTime(), config);
        }
        return mmsis.size();
    }

    private static void addStationaryNearby(Set<Long> mmsis, CleanAisPoint point, double centerLat, double centerLon,
                                            Instant evidenceTime, AlongsideConfig config) {
        if (point == null || point.getMmsi() == null || point.getSpeed() == null || point.getUpdateTime() == null
            || !point.hasValidPosition()
            || point.getSpeed() > config.getHighMatchStationaryMaxSpeedKnots()) {
            return;
        }
        long gapSeconds = Math.abs(Duration.between(point.getUpdateTime(), evidenceTime).getSeconds());
        if (gapSeconds > config.getCandidateMaxTimeGapMinutes() * 60L) {
            return;
        }
        if (distanceNm(centerLat, centerLon, point.getLat(), point.getLon())
            <= config.getHighMatchStationaryClusterRadiusNm()) {
            mmsis.add(point.getMmsi());
        }
    }

    private static PairProximityEvidence findEvidence(List<PairProximityEvidence> evidence, Long otherMmsi) {
        for (PairProximityEvidence item : evidence) {
            if (item.getMmsiA().equals(otherMmsi) || item.getMmsiB().equals(otherMmsi)) {
                return item;
            }
        }
        return null;
    }

    private static PairProximityEvidence buildEvidence(
        CleanAisPoint point,
        CleanAisPoint other,
        AlongsideConfig config,
        VesselTypeClassifierImpl vesselTypeClassifier,
        AlongsidePairPriorityClassifier pairPriorityClassifier,
        CandidateMetrics metrics
    ) {
        if (other == null || other.getMmsi() == null || other.getUpdateTime() == null || !other.hasValidPosition()
            || other.getSpeed() == null || point.getSpeed() == null) {
            inc(metrics == null ? null : metrics.rejectByInvalidAis);
            return null;
        }
        if (point.getMmsi().equals(other.getMmsi())) {
            inc(metrics == null ? null : metrics.rejectBySameVessel);
            return null;
        }
        if (point.getSpeed() > config.getCandidateMaxSpeedKnots()
            || other.getSpeed() > config.getCandidateMaxSpeedKnots()) {
            inc(metrics == null ? null : metrics.rejectByInvalidAis);
            return null;
        }
        long gapSeconds = Math.abs(Duration.between(point.getUpdateTime(), other.getUpdateTime()).getSeconds());
        if (gapSeconds > config.getCandidateMaxTimeGapMinutes() * 60L) {
            inc(metrics == null ? null : metrics.rejectByTimeGap);
            return null;
        }
        double distanceNm = distanceNm(point.getLat(), point.getLon(), other.getLat(), other.getLon());
        if (distanceNm > config.getCandidateMaxDistanceNm()) {
            inc(metrics == null ? null : metrics.rejectByDistance);
            LOG.debug("ALONGSIDE_REALTIME candidate_summary reject=distance mmsi_a={} mmsi_b={} distance_nm={}",
                point.getMmsi(), other.getMmsi(), distanceNm);
            return null;
        }
        CleanAisPoint a = point.getMmsi() <= other.getMmsi() ? point : other;
        CleanAisPoint b = point.getMmsi() <= other.getMmsi() ? other : point;
        VesselProfile vesselA = profile(a, vesselTypeClassifier);
        VesselProfile vesselB = profile(b, vesselTypeClassifier);
        PairPriority priority = pairPriorityClassifier.classify(vesselA, vesselB);
        if (config.isShipTypeFilterEnabled()
            && (vesselA.getVesselClass() == com.hifleet.analysis.alongside.core.model.VesselClass.LOW_PRIORITY
            || vesselB.getVesselClass() == com.hifleet.analysis.alongside.core.model.VesselClass.LOW_PRIORITY)) {
            inc(metrics == null ? null : metrics.rejectByShipType);
            return null;
        }
        if (priority == PairPriority.EXCLUDE) {
            inc(metrics == null ? null : metrics.rejectByPairPriority);
            LOG.debug("ALONGSIDE_REALTIME candidate_summary reject=pair_priority mmsi_a={} mmsi_b={}",
                vesselA.getMmsi(), vesselB.getMmsi());
            return null;
        }
        PairProximityEvidence evidence = new PairProximityEvidence();
        evidence.setPairId(PairProximityEvidence.pairId(a.getMmsi(), b.getMmsi()));
        evidence.setMmsiA(a.getMmsi());
        evidence.setMmsiB(b.getMmsi());
        evidence.setEvidenceTime(max(point.getUpdateTime(), other.getUpdateTime()));
        evidence.setPointATime(a.getUpdateTime());
        evidence.setPointBTime(b.getUpdateTime());
        evidence.setLonA(a.getLon());
        evidence.setLatA(a.getLat());
        evidence.setLonB(b.getLon());
        evidence.setLatB(b.getLat());
        evidence.setSpeedA(a.getSpeed());
        evidence.setSpeedB(b.getSpeed());
        evidence.setCourseA(a.getCourse());
        evidence.setCourseB(b.getCourse());
        evidence.setNavigationStatusA(a.getNavigationStatus());
        evidence.setNavigationStatusB(b.getNavigationStatus());
        evidence.setDistanceNm(distanceNm);
        evidence.setTimeGapSeconds(gapSeconds);
        evidence.setRelativeSpeedKnots(Math.abs(point.getSpeed() - other.getSpeed()));
        evidence.setHeadingParallelDiff(headingParallelDiff(point.getCourse(), other.getCourse()));
        evidence.setVesselA(vesselA);
        evidence.setVesselB(vesselB);
        evidence.setPairPriority(priority);
        inc(metrics == null ? null : metrics.candidateEvidenceCount);
        return evidence;
    }

    private static VesselProfile profile(CleanAisPoint point, VesselTypeClassifierImpl classifier) {
        VesselClass vesselClass = classifier.classify(point);
        VesselType vesselType = classifier.resolveType(point);
        return new VesselProfile(point.getMmsi(), vesselType, vesselClass, point.getNavigationStatus());
    }

    private String pointKey(CleanAisPoint point) {
        return point.getMmsi() + "|" + point.getUpdateTime().toEpochMilli();
    }

    private static Double midpoint(Double a, Double b) {
        if (a == null || b == null) {
            return null;
        }
        return (a + b) / 2.0d;
    }

    public static Double headingParallelDiff(Double courseA, Double courseB) {
        if (courseA == null || courseB == null || courseA < 0.0d || courseA > 360.0d
            || courseB < 0.0d || courseB > 360.0d) {
            return null;
        }
        double diff = Math.abs(courseA - courseB);
        if (diff > 180.0d) {
            diff = 360.0d - diff;
        }
        return Math.min(diff, Math.abs(diff - 180.0d));
    }

    public static double distanceNm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMeters = 6371000.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2.0d) * Math.sin(dLon / 2.0d);
        double c = 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
        return earthRadiusMeters * c / 1852.0d;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static void inc(Counter counter) {
        if (counter != null) {
            counter.inc();
        }
    }

    static class CandidateMetrics {
        private final Counter rejectByInvalidAis;
        private final Counter rejectByShipType;
        private final Counter rejectByDistance;
        private final Counter rejectByTimeGap;
        private final Counter rejectBySameVessel;
        private final Counter rejectByPairPriority;
        private final Counter candidateEvidenceCount;

        CandidateMetrics(Counter rejectByInvalidAis, Counter rejectByShipType, Counter rejectByDistance,
                         Counter rejectByTimeGap, Counter rejectBySameVessel, Counter rejectByPairPriority,
                         Counter candidateEvidenceCount) {
            this.rejectByInvalidAis = rejectByInvalidAis;
            this.rejectByShipType = rejectByShipType;
            this.rejectByDistance = rejectByDistance;
            this.rejectByTimeGap = rejectByTimeGap;
            this.rejectBySameVessel = rejectBySameVessel;
            this.rejectByPairPriority = rejectByPairPriority;
            this.candidateEvidenceCount = candidateEvidenceCount;
        }
    }
}
