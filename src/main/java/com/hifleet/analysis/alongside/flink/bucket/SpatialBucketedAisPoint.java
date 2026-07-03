package com.hifleet.analysis.alongside.flink.bucket;

import com.hifleet.analysis.portcall.model.CleanAisPoint;

import java.io.Serializable;

public class SpatialBucketedAisPoint implements Serializable {

    private static final long serialVersionUID = 1L;

    private String bucketId;
    private String primaryBucketId;
    private CleanAisPoint point;

    public SpatialBucketedAisPoint() {
    }

    public SpatialBucketedAisPoint(String bucketId, String primaryBucketId, CleanAisPoint point) {
        this.bucketId = bucketId;
        this.primaryBucketId = primaryBucketId;
        this.point = point;
    }

    public String getBucketId() {
        return bucketId;
    }

    public void setBucketId(String bucketId) {
        this.bucketId = bucketId;
    }

    public String getPrimaryBucketId() {
        return primaryBucketId;
    }

    public void setPrimaryBucketId(String primaryBucketId) {
        this.primaryBucketId = primaryBucketId;
    }

    public CleanAisPoint getPoint() {
        return point;
    }

    public void setPoint(CleanAisPoint point) {
        this.point = point;
    }
}
