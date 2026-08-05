package com.hifleet.regionbehavior;

import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.service.SpatialAisReplayClient;
import com.hifleet.trajectory.TrajectoryPoint;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RegionAisDiscoveryService {
    private final SpatialAisReplayClient replayClient;
    private final RegionBehaviorConfig config;

    @Autowired
    public RegionAisDiscoveryService(SpatialAisReplayClient replayClient, RegionBehaviorConfig config) {
        this.replayClient = replayClient;
        this.config = config;
    }

    public RegionAisDiscoveryResult discover(RegionGeometry region, RegionQueryPlan plan,
                                             String batchNo, int maxCandidates) {
        RegionAisDiscoveryResult result = new RegionAisDiscoveryResult();
        Set<String> pointKeys = new HashSet<String>();
        for (RegionReplaySlice slice : plan.getSlices()) {
            query(slice, region, batchNo, maxCandidates, result, pointKeys);
            if (result.getStatus() == RegionBehaviorStatus.CANDIDATE_LIMIT_EXCEEDED) {
                break;
            }
        }
        return result;
    }

    private void query(RegionReplaySlice slice, RegionGeometry region, String batchNo,
                       int maxCandidates, RegionAisDiscoveryResult aggregate, Set<String> pointKeys) {
        if (aggregate.getQuerySlices() >= config.getMaxQuerySlices()) {
            aggregate.failed();
            record(aggregate, slice, RegionBehaviorStatus.PARTIAL, null,
                    "actual replay query count exceeds maxQuerySlices=" + config.getMaxQuerySlices());
            return;
        }
        ReplayQueryRequest request = request(slice, batchNo);
        ReplayQueryResult response = replayClient.query(request);
        if (response == null) {
            aggregate.queried(0, 0, 0, 0L);
            aggregate.failed();
            record(aggregate, slice, RegionBehaviorStatus.PARTIAL, null, "NULL_REPLAY_RESULT");
            return;
        }
        aggregate.queried(response.getRawVesselCount(), response.getRawPointCount(),
                response.getValidPointCount(), response.getElapsedMs());
        if (response.isTimeout()) {
            aggregate.timeout();
            record(aggregate, slice, RegionBehaviorStatus.INCOMPLETE_TIMEOUT, response,
                    response.getErrorMessage());
            mergeDiagnosticPoints(response, region, maxCandidates, aggregate, pointKeys);
            return;
        }
        if (!response.isSuccess()) {
            aggregate.failed();
            record(aggregate, slice, RegionBehaviorStatus.PARTIAL, response, response.getErrorMessage());
            mergeDiagnosticPoints(response, region, maxCandidates, aggregate, pointKeys);
            return;
        }
        if (response.isTruncated()) {
            aggregate.truncated();
            if (slice.canSplitTime(config)) {
                for (RegionReplaySlice child : slice.splitTime()) {
                    query(child, region, batchNo, maxCandidates, aggregate, pointKeys);
                    if (aggregate.getStatus() == RegionBehaviorStatus.CANDIDATE_LIMIT_EXCEEDED) {
                        break;
                    }
                }
                return;
            }
            if (slice.canSplitSpace(config)) {
                for (RegionReplaySlice child : slice.splitSpace()) {
                    query(child, region, batchNo, maxCandidates, aggregate, pointKeys);
                    if (aggregate.getStatus() == RegionBehaviorStatus.CANDIDATE_LIMIT_EXCEEDED) {
                        break;
                    }
                }
                return;
            }
            aggregate.terminalTruncation();
            record(aggregate, slice, RegionBehaviorStatus.INCOMPLETE_TRUNCATED, response,
                    response.getErrorMessage());
            mergeDiagnosticPoints(response, region, maxCandidates, aggregate, pointKeys);
            return;
        }
        double parseRatio = response.getRawPointCount() == 0 ? 0.0
                : response.getParseErrorCount() / (double) response.getRawPointCount();
        if (parseRatio > config.getMaxParseErrorRatio()) {
            aggregate.parseError();
            record(aggregate, slice, RegionBehaviorStatus.INCOMPLETE_PARSE_ERROR, response,
                    "parseErrorRatio=" + parseRatio);
        } else {
            aggregate.completed();
            record(aggregate, slice, RegionBehaviorStatus.COMPLETE, response, null);
        }
        mergeDiagnosticPoints(response, region, maxCandidates, aggregate, pointKeys);
    }

    private void mergeDiagnosticPoints(ReplayQueryResult response, RegionGeometry region,
                                       int maxCandidates, RegionAisDiscoveryResult aggregate,
                                       Set<String> pointKeys) {
        if (response.getTracksByMmsi() == null) {
            return;
        }
        for (Map.Entry<String, List<TrajectoryPoint>> track : response.getTracksByMmsi().entrySet()) {
            String mmsi = track.getKey();
            if (mmsi == null || mmsi.trim().isEmpty() || mmsi.length() > 32
                    || track.getValue() == null) {
                continue;
            }
            for (TrajectoryPoint point : track.getValue()) {
                if (point == null || point.getTime() == null
                        || !region.containsAnalysis(new Coordinate(point.getLon(), point.getLat()))) {
                    continue;
                }
                String key = pointKey(mmsi, point);
                if (!pointKeys.add(key)) {
                    aggregate.countDuplicatePoint();
                    continue;
                }
                RegionShipCandidate candidate = aggregate.candidates.get(mmsi);
                if (candidate == null) {
                    if (aggregate.candidates.size() >= maxCandidates) {
                        aggregate.candidateLimitExceeded();
                        return;
                    }
                    candidate = new RegionShipCandidate(mmsi);
                    aggregate.candidates.put(mmsi, candidate);
                }
                candidate.addPoint(point);
                aggregate.countUniquePoint();
            }
        }
    }

    private ReplayQueryRequest request(RegionReplaySlice slice, String batchNo) {
        ReplayQueryRequest request = new ReplayQueryRequest();
        request.setStartTime(RegionBehaviorTimes.format(slice.getStart()));
        request.setEndTime(RegionBehaviorTimes.format(slice.getEnd()));
        request.setMinLon(slice.getMinLon());
        request.setMaxLon(slice.getMaxLon());
        request.setMinLat(slice.getMinLat());
        request.setMaxLat(slice.getMaxLat());
        request.setDevSize(config.getMaxReplayPointsPerSlice());
        request.setBatchNo(batchNo + ":" + slice.getSliceId());
        return request;
    }

    private void record(RegionAisDiscoveryResult aggregate, RegionReplaySlice slice,
                        RegionBehaviorStatus status, ReplayQueryResult response, String error) {
        aggregate.addSliceResult(new RegionReplaySliceResult(slice.getSliceId(), status,
                response == null ? 0 : response.getRawPointCount(),
                response == null ? 0 : response.getValidPointCount(),
                response == null ? 0 : response.getParseErrorCount(),
                response == null ? 0L : response.getElapsedMs(), error));
    }

    private String pointKey(String mmsi, TrajectoryPoint point) {
        return mmsi + "|" + point.getTime() + "|" + normalize(point.getLon()) + "|" + normalize(point.getLat());
    }

    private String normalize(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).toPlainString();
    }
}
