package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.AisPoint;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;

/**
 * Uses AIS updatetime as the Flink event-time timestamp.
 */
public class AisPointTimestampAssigner implements SerializableTimestampAssigner<AisPoint> {

    private static final long serialVersionUID = 1L;

    @Override
    public long extractTimestamp(AisPoint point, long recordTimestamp) {
        return point.getUpdateTime().toEpochMilli();
    }
}
