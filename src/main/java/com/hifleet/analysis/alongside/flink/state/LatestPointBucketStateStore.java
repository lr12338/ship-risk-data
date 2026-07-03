package com.hifleet.analysis.alongside.flink.state;

import com.hifleet.analysis.portcall.model.CleanAisPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class LatestPointBucketStateStore implements BucketVesselStateStore {

    private final Map<Long, CleanAisPoint> latestByMmsi = new LinkedHashMap<Long, CleanAisPoint>();

    @Override
    public void update(CleanAisPoint point) {
        if (point != null && point.getMmsi() != null) {
            latestByMmsi.put(point.getMmsi(), point);
        }
    }

    @Override
    public Collection<CleanAisPoint> findNearby(CleanAisPoint point) {
        return new ArrayList<CleanAisPoint>(latestByMmsi.values());
    }

    @Override
    public void cleanup(long watermark) {
        // Flink state TTL handles production cleanup; this in-memory store is for tests/future replacement.
    }
}
