package com.hifleet.analysis.alongside.flink.state;

import com.hifleet.analysis.portcall.model.CleanAisPoint;

import java.util.Collection;

public interface BucketVesselStateStore {
    void update(CleanAisPoint point);
    Collection<CleanAisPoint> findNearby(CleanAisPoint point);
    void cleanup(long watermark);
}
