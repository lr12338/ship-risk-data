package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.AisPoint;
import org.apache.flink.api.java.functions.KeySelector;

/**
 * Keys the stream by MMSI so each vessel can later own its Flink keyed state.
 */
public class AisPointMmsiKeySelector implements KeySelector<AisPoint, Long> {

    private static final long serialVersionUID = 1L;

    @Override
    public Long getKey(AisPoint point) {
        return point.getMmsi();
    }
}
