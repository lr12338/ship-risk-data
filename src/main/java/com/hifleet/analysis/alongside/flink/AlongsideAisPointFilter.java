package com.hifleet.analysis.alongside.flink;

import com.hifleet.analysis.portcall.config.AlongsideConfig;
import com.hifleet.analysis.portcall.model.CleanAisPoint;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlongsideAisPointFilter extends RichFilterFunction<CleanAisPoint> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AlongsideAisPointFilter.class);

    private final AlongsideConfig config;
    private transient Counter rejectByInvalidAis;
    private transient Counter rejectBySpeed;

    public AlongsideAisPointFilter(AlongsideConfig config) {
        this.config = config;
    }

    @Override
    public void open(Configuration parameters) {
        rejectByInvalidAis = getRuntimeContext().getMetricGroup().counter("rejectByInvalidAis");
        rejectBySpeed = getRuntimeContext().getMetricGroup().counter("rejectBySpeed");
    }

    @Override
    public boolean filter(CleanAisPoint point) {
        if (config == null || !config.isPreFilterEnabled()) {
            return true;
        }
        if (point == null || !point.isUsable() || point.getUpdateTime() == null || !point.hasValidPosition()) {
            rejectByInvalidAis.inc();
            LOG.debug("ALONGSIDE_REALTIME prefilter_summary reject=invalid_ais mmsi={}",
                point == null ? null : point.getMmsi());
            return false;
        }
        if (config.isPreFilterExcludeInvalidMmsi()
            && (point.getMmsi() == null || point.getMmsi().longValue() <= 0L || point.getMmsi().longValue() == 999999999L)) {
            rejectByInvalidAis.inc();
            LOG.debug("ALONGSIDE_REALTIME prefilter_summary reject=invalid_mmsi mmsi={}", point.getMmsi());
            return false;
        }
        if (point.getSpeed() == null || point.getSpeed() > config.getPreFilterMaxSpeedKnots()) {
            rejectBySpeed.inc();
            LOG.debug("ALONGSIDE_REALTIME prefilter_summary reject=speed mmsi={} speed={}",
                point.getMmsi(), point.getSpeed());
            return false;
        }
        return true;
    }
}
