package com.hifleet.analysis.alongside.flink.bucket;

import org.apache.flink.api.java.functions.KeySelector;

public class SpatialBucketKeySelector implements KeySelector<SpatialBucketedAisPoint, String> {

    private static final long serialVersionUID = 1L;

    @Override
    public String getKey(SpatialBucketedAisPoint value) {
        return value.getBucketId();
    }
}
