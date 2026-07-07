package com.hifleet.analysis.alongside.output.db;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.InstantiationUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class AlongsideEventJdbcSinkTest {

    @Test
    public void shouldIgnoreEventsWhenDisabled() throws Exception {
        AlongsideDbSinkConfig config = new AlongsideDbSinkConfig();
        config.setEnabled(false);

        AlongsideEventJdbcSink sink = new AlongsideEventJdbcSink(config);
        sink.open(new Configuration());
        sink.invoke(new AlongsideEvent(), null);
        sink.close();
    }

    @Test
    public void shouldBeSerializableForFlinkClosureCleaner() throws Exception {
        AlongsideDbSinkConfig config = new AlongsideDbSinkConfig();
        config.setEnabled(false);

        InstantiationUtil.serializeObject(new AlongsideEventJdbcSink(config));
    }

    @Test
    public void shouldAllowAllSeverityLevelsWhenFilterIsEmpty() {
        AlongsideDbSinkConfig config = new AlongsideDbSinkConfig();
        AlongsideEventJdbcSink sink = new AlongsideEventJdbcSink(config);
        AlongsideEvent event = new AlongsideEvent();
        event.setSeverityLevel("L1");

        Assert.assertTrue(sink.isAllowedSeverity(event));
    }

    @Test
    public void shouldOnlyAllowConfiguredSeverityLevels() {
        AlongsideDbSinkConfig config = new AlongsideDbSinkConfig();
        config.setAllowedSeverityLevels(Collections.singleton("L2"));
        AlongsideEventJdbcSink sink = new AlongsideEventJdbcSink(config);
        AlongsideEvent l1 = new AlongsideEvent();
        l1.setSeverityLevel("L1");
        AlongsideEvent l2 = new AlongsideEvent();
        l2.setSeverityLevel("L2");

        Assert.assertFalse(sink.isAllowedSeverity(l1));
        Assert.assertTrue(sink.isAllowedSeverity(l2));
    }
}
