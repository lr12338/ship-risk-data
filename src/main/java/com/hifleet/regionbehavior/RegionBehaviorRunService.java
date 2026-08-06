package com.hifleet.regionbehavior;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorTrace;
import org.locationtech.jts.geom.Envelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class RegionBehaviorRunService {
    private final RegionBehaviorConfig config;
    private final RegionAisDiscoveryService discoveryService;
    private final RegionShipMetadataService metadataService;
    private final RegionLoiteringRunner loiteringRunner;
    private final RegionAlongsideRunner alongsideRunner;
    private final RegionBehaviorReportWriter reportWriter;
    private final RegionEventContextEnricher contextEnricher;
    @Autowired(required = false)
    private RegionBehaviorStateRepository stateRepository;
    @Autowired(required = false)
    private RegionFormalEventStore formalEventStore;

    @Autowired
    public RegionBehaviorRunService(RegionBehaviorConfig config,
                                    RegionAisDiscoveryService discoveryService,
                                    RegionShipMetadataService metadataService,
                                    RegionLoiteringRunner loiteringRunner,
                                    RegionAlongsideRunner alongsideRunner,
                                    RegionBehaviorReportWriter reportWriter,
                                    RegionEventContextEnricher contextEnricher) {
        this.config = config;
        this.discoveryService = discoveryService;
        this.metadataService = metadataService;
        this.loiteringRunner = loiteringRunner;
        this.alongsideRunner = alongsideRunner;
        this.reportWriter = reportWriter;
        this.contextEnricher = contextEnricher;
    }

    public JSONObject preflight(String rawParams) {
        JSONObject result = new JSONObject(true);
        RegionBehaviorJobParams params;
        try {
            params = RegionBehaviorJobParams.parse(rawParams);
        } catch (RuntimeException ex) {
            return blocked(result, RegionBehaviorStatus.BLOCKED_INVALID_GEOMETRY, ex.getMessage());
        }
        if (!config.isEnabled()) {
            return blocked(result, RegionBehaviorStatus.BLOCKED_UNSAFE_STORE_MODE,
                    "ship.behavior.region.enabled=false");
        }
        if (params.isFormalWrite() && !config.isWriteEnabled()) {
            return blocked(result, RegionBehaviorStatus.BLOCKED_UNSAFE_STORE_MODE,
                    "ship.behavior.region.write-enabled=false");
        }
        if (!params.isFormalWrite() && (!params.isDryRun() || params.isStoreCandidates())) {
            return blocked(result, RegionBehaviorStatus.BLOCKED_UNSAFE_STORE_MODE,
                    "V1 requires dryRun=true and storeCandidates=false");
        }
        try {
            params.validate(config);
        } catch (RuntimeException ex) {
            RegionBehaviorStatus status = ex.getMessage() != null && ex.getMessage().contains("time range")
                    ? RegionBehaviorStatus.BLOCKED_TIME_RANGE_TOO_LARGE
                    : RegionBehaviorStatus.BLOCKED_INVALID_GEOMETRY;
            return blocked(result, status, ex.getMessage());
        }
        RegionGeometry region;
        try {
            region = RegionGeometry.parse(params.getPolygonWkt(),
                    params.effectiveRegionBufferNm(config), config);
        } catch (RuntimeException ex) {
            return blocked(result, RegionBehaviorStatus.BLOCKED_INVALID_GEOMETRY, ex.getMessage());
        }
        if (region.getAreaSqKm() > config.getMaxRegionAreaSqKm()) {
            return blocked(result, RegionBehaviorStatus.BLOCKED_REGION_TOO_LARGE,
                    "areaSqKm=" + region.getAreaSqKm() + " max=" + config.getMaxRegionAreaSqKm());
        }
        RegionQueryPlan plan;
        try {
            plan = new RegionQueryPlanner(config).plan(params, region);
        } catch (RuntimeException ex) {
            return blocked(result, RegionBehaviorStatus.BLOCKED_QUERY_PLAN_TOO_LARGE, ex.getMessage());
        }
        if (!outputRootWritable()) {
            return blocked(result, RegionBehaviorStatus.BLOCKED_UNSAFE_STORE_MODE,
                    "output root parent is not writable");
        }
        Envelope core = region.getCoreRegion().getEnvelopeInternal();
        Envelope analysis = region.getAnalysisEnvelope();
        result.put("status", RegionBehaviorStatus.PASS.name());
        result.put("regionFingerprint", region.getFingerprint());
        result.put("areaSqKm", region.getAreaSqKm());
        result.put("coreBbox", bbox(core));
        result.put("analysisBbox", bbox(analysis));
        result.put("querySlices", plan.getSlices().size());
        result.put("estimatedMaxQueryCount", estimateMaxQueries(plan.getSlices().size()));
        result.put("candidateRisk", candidateRisk(region.getAreaSqKm(), params.runHours()));
        result.put("outputRoot", Paths.get(config.getOutputRoot()).toAbsolutePath().normalize().toString());
        result.put("requiredBeansPresent", true);
        result.put("realAisQueryExecuted", false);
        result.put("databaseWrites", 0);
        return result;
    }

    public RegionBehaviorRunResult run(String rawParams) {
        JSONObject preflight = preflight(rawParams);
        if (!RegionBehaviorStatus.PASS.name().equals(preflight.getString("status"))) {
            throw new IllegalArgumentException(preflight.toJSONString());
        }
        RegionBehaviorJobParams params = RegionBehaviorJobParams.parse(rawParams);
        params.validate(config);
        RegionGeometry region = RegionGeometry.parse(params.getPolygonWkt(),
                params.effectiveRegionBufferNm(config), config);
        region.assertWithinAreaLimit(config);
        RegionQueryPlan plan = new RegionQueryPlanner(config).plan(params, region);
        if (params.isFormalWrite()) {
            if (stateRepository == null || formalEventStore == null) {
                throw new IllegalStateException("region formal persistence unavailable");
            }
            stateRepository.claim(params, region.getFingerprint(), paramsHash(params, region.getFingerprint()));
        }
        RegionAisDiscoveryResult discovery = discoveryService.discover(region, plan,
                params.getBatchNo(), params.effectiveMaxCandidateVessels(config),
                params.isFormalWrite() && params.isResume(), params.isFormalWrite());
        RegionShipMetadataSummary metadata = metadataService.filter(discovery.candidates);
        RegionBehaviorRunResult result = RegionBehaviorRunResult.fromDiscovery(discovery);
        result.setEligibleVessels(metadata.getEligibleMainShips());
        if (metadata.getMetadataQueryFailures() > 0) {
            result.dependencyFailure("SHIP_METADATA",
                    "metadata query batches failed=" + metadata.getMetadataQueryFailures());
        }
        RegionEventMatcher matcher = new RegionEventMatcher(region, config, params.getBatchNo(),
                params.getRegionId(), params.getRegionName());
        List<RegionMatchedEvent> loitering = new ArrayList<RegionMatchedEvent>();
        List<RegionMatchedEvent> alongside = new ArrayList<RegionMatchedEvent>();
        Set<String> completedAnalysis = params.isFormalWrite() && params.isResume()
                ? stateRepository.completed(params.getBatchNo(), "ANALYSIS")
                : java.util.Collections.<String>emptySet();
        Set<String> successfulAnalysis = new HashSet<String>();
        for (RegionShipCandidate candidate : discovery.getCandidates().values()) {
            if (!candidate.isEligible()) {
                continue;
            }
            if (completedAnalysis.contains(candidate.getMmsi())) continue;
            result.analyzedVessel();
            if (params.getBehaviorTypes().contains("LOITERING")) {
                RegionBehaviorRunnerResult computed = loiteringRunner.run(candidate.getMmsi(), params);
                result.addLoiteringCandidates(computed.getRawCandidates());
                if (!computed.isComplete()) {
                    result.analysisFailure("LOITERING", candidate.getMmsi(), computed.getFailure());
                    if (params.isFormalWrite()) stateRepository.mark(params.getBatchNo(), "ANALYSIS",
                            candidate.getMmsi(), false, computed.getFailure());
                } else {
                    successfulAnalysis.add(candidate.getMmsi());
                }
                for (ShipBehaviorTrace trace : computed.getTraces()) {
                    loitering.add(contextEnricher.enrich(matcher.matchLoitering(trace)));
                }
            }
            if (params.getBehaviorTypes().contains("ALONGSIDE")) {
                RegionBehaviorRunnerResult computed = alongsideRunner.run(candidate.getMmsi(), params);
                result.addAlongsideCandidates(computed.getRawCandidates());
                if (!computed.isComplete()) {
                    result.analysisFailure("ALONGSIDE", candidate.getMmsi(), computed.getFailure());
                    if (params.isFormalWrite()) stateRepository.mark(params.getBatchNo(), "ANALYSIS",
                            candidate.getMmsi(), false, computed.getFailure());
                } else {
                    successfulAnalysis.add(candidate.getMmsi());
                }
                for (ShipBehaviorTrace trace : computed.getTraces()) {
                    alongside.add(contextEnricher.enrich(matcher.matchAlongside(trace)));
                }
            }
        }
        alongside = matcher.deduplicateAlongside(alongside);
        result.setDuplicatePairEvents(matcher.getLastDuplicateCount());
        addMatched(result, loitering);
        addMatched(result, alongside);
        if (!discovery.isQueryComplete() || result.getAnalysisFailures() > 0) {
            result.markPartial();
            for (RegionMatchedEvent event : result.getRegionEvents()) {
                event.setQueryCompleteness(RegionBehaviorStatus.PARTIAL);
            }
            for (RegionMatchedEvent event : result.getBoundaryEvents()) {
                event.setQueryCompleteness(RegionBehaviorStatus.PARTIAL);
            }
        }
        if (params.isFormalWrite()) {
            int writes = 0;
            for (String mmsi : successfulAnalysis) {
                writes += formalEventStore.replace(params, region.getFingerprint(), mmsi,
                        result.getRegionEvents());
            }
            result.setFormalWriteResult(writes);
            stateRepository.finish(params.getBatchNo(), result.isSuccess(), result.summaryJson().toJSONString());
        }
        Path output = reportWriter.write(params, region, plan, discovery, metadata, result);
        result.setOutputDirectory(output.toString());
        return result;
    }

    private String paramsHash(RegionBehaviorJobParams params, String fingerprint) {
        String value = params.getRegionId() + "|" + fingerprint + "|" + params.getStartTime()
                + "|" + params.getEndTime() + "|" + params.getBehaviorTypes() + "|"
                + params.getShipTypePolicy() + "|" + params.effectiveTimeSliceMinutes(config)
                + "|" + params.effectiveContextBeforeHours(config) + "|"
                + params.effectiveContextAfterHours(config) + "|"
                + params.effectiveRegionBufferNm(config) + "|"
                + params.effectiveMaxCandidateVessels(config) + "|" + params.getWriteMode();
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void addMatched(RegionBehaviorRunResult result, List<RegionMatchedEvent> events) {
        for (RegionMatchedEvent event : events) {
            if (event.getRegionMatchStatus() == RegionMatchStatus.STRICT_CORE) {
                result.addRegionEvent(event);
            } else if (event.getRegionMatchStatus() == RegionMatchStatus.REGION_BOUNDARY_PARTIAL) {
                result.addBoundaryEvent(event);
            }
        }
    }

    private JSONObject blocked(JSONObject result, RegionBehaviorStatus status, String reason) {
        result.put("status", status.name());
        result.put("reason", reason);
        result.put("realAisQueryExecuted", false);
        result.put("databaseWrites", 0);
        return result;
    }

    private JSONObject bbox(Envelope envelope) {
        JSONObject bbox = new JSONObject(true);
        bbox.put("minLon", envelope.getMinX());
        bbox.put("maxLon", envelope.getMaxX());
        bbox.put("minLat", envelope.getMinY());
        bbox.put("maxLat", envelope.getMaxY());
        return bbox;
    }

    private long estimateMaxQueries(int initialSlices) {
        long spatialTree = 1L;
        long level = 1L;
        for (int i = 0; i < config.getMaxSpatialRefinementDepth(); i++) {
            level *= 4L;
            spatialTree += level;
        }
        long estimate = initialSlices * spatialTree;
        return Math.min(Integer.MAX_VALUE, estimate);
    }

    private String candidateRisk(double areaSqKm, long hours) {
        double exposure = areaSqKm * Math.max(1L, hours);
        if (exposure > 10000.0) {
            return "HIGH";
        }
        if (exposure > 2000.0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean outputRootWritable() {
        Path path = Paths.get(config.getOutputRoot()).toAbsolutePath().normalize();
        Path existing = path;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        return existing != null && Files.isDirectory(existing) && Files.isWritable(existing);
    }
}
