package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.CleanAisPoint;
import org.apache.flink.api.java.functions.KeySelector;

/**
 * Keys preprocessed AIS points by MMSI for downstream vessel recognition state.
 */
public class CleanAisPointMmsiKeySelector implements KeySelector<CleanAisPoint, Long> {

    private static final long serialVersionUID = 1L;

    @Override
    public Long getKey(CleanAisPoint point) {
        return point.getMmsi();
    }
}
