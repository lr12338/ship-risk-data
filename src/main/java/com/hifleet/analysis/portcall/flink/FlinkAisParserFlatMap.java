package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.consumer.AisMessageParser;
import com.hifleet.analysis.portcall.model.AisPoint;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.util.Collector;

import java.time.Instant;
import java.util.List;

/**
 * Parses one Kafka record value into zero or more AIS points for the Flink stream.
 */
public class FlinkAisParserFlatMap implements FlatMapFunction<String, AisPoint> {

    private static final long serialVersionUID = 1L;

    private transient AisMessageParser parser;

    @Override
    public void flatMap(String payload, Collector<AisPoint> out) {
        AisMessageParser activeParser = parser();
        List<AisPoint> points = activeParser.parseMany(payload);
        Instant receiveTime = Instant.now();
        for (AisPoint point : points) {
            if (point != null && point.getServerReceiveTime() == null) {
                point.setServerReceiveTime(receiveTime);
            }
            out.collect(point);
        }
    }

    private AisMessageParser parser() {
        if (parser == null) {
            parser = new AisMessageParser();
        }
        return parser;
    }
}
