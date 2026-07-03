package com.hifleet.analysis.alongside.flink.bucket;

import com.hifleet.analysis.portcall.model.CleanAisPoint;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.util.Collector;

public class SpatialBucketAssigner implements FlatMapFunction<CleanAisPoint, SpatialBucketedAisPoint> {

    private static final long serialVersionUID = 1L;

    private final double bucketSizeDegrees;
    private final double maxSpeedKnots;

    public SpatialBucketAssigner(double bucketSizeDegrees, double maxSpeedKnots) {
        this.bucketSizeDegrees = bucketSizeDegrees;
        this.maxSpeedKnots = maxSpeedKnots;
    }

    @Override
    public void flatMap(CleanAisPoint point, Collector<SpatialBucketedAisPoint> out) {
        if (point == null || !point.isUsable() || point.getMmsi() == null || point.getUpdateTime() == null
            || !point.hasValidPosition() || point.getSpeed() == null || point.getSpeed() > maxSpeedKnots) {
            return;
        }
        int lonBucket = bucketIndex(point.getLon());
        int latBucket = bucketIndex(point.getLat());
        String primaryBucketId = bucketId(lonBucket, latBucket);
        for (int lonOffset = -1; lonOffset <= 1; lonOffset++) {
            for (int latOffset = -1; latOffset <= 1; latOffset++) {
                out.collect(new SpatialBucketedAisPoint(
                    bucketId(lonBucket + lonOffset, latBucket + latOffset),
                    primaryBucketId,
                    point
                ));
            }
        }
    }

    private int bucketIndex(double value) {
        double size = bucketSizeDegrees <= 0.0d ? 0.02d : bucketSizeDegrees;
        return (int) Math.floor(value / size);
    }

    private String bucketId(int lonBucket, int latBucket) {
        return lonBucket + ":" + latBucket;
    }
}
