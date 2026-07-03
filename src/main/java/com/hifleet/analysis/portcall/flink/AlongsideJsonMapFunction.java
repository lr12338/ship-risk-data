package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import com.hifleet.analysis.alongside.output.AlongsideEventMapper;
import org.apache.flink.api.common.functions.MapFunction;

/**
 * Debug mapper delegates to the output adapter so Flink code does not own business JSON.
 */
public class AlongsideJsonMapFunction implements MapFunction<AlongsideEvent, String> {

    private static final long serialVersionUID = 1L;
    private static final AlongsideEventMapper MAPPER = new AlongsideEventMapper();

    @Override
    public String map(AlongsideEvent event) {
        return toJson(event);
    }

    public static String toJson(AlongsideEvent event) {
        return MAPPER.toJson(event);
    }
}
