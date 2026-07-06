package com.hifleet.analysis.alongside.output.db;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.InstantiationUtil;
import org.junit.Test;

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
}
