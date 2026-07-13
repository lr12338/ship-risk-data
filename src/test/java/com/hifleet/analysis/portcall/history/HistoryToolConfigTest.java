package com.hifleet.analysis.portcall.history;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;

public class HistoryToolConfigTest {

    @Test
    public void shouldParseNaiveSqlTimestampAsBeijingTime() {
        Instant parsed = HistoryToolConfig.parseInstant("2026-07-07 19:10:51");

        Assert.assertEquals(Instant.parse("2026-07-07T11:10:51Z"), parsed);
    }

    @Test
    public void shouldKeepTimestampWithZoneUnchanged() {
        Assert.assertEquals(
            Instant.parse("2026-07-07T11:10:51Z"),
            HistoryToolConfig.parseInstant("2026-07-07T11:10:51Z"));
        Assert.assertEquals(
            Instant.parse("2026-07-07T11:10:51Z"),
            HistoryToolConfig.parseInstant("2026-07-07T19:10:51+08:00"));
    }
}
