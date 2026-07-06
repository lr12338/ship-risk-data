package com.hifleet.analysis.alongside.output.db;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;

public class AlongsideEventIdGeneratorTest {

    @Test
    public void shouldGenerateStableIdForSameEvent() {
        AlongsideEvent event = event("100-200", "WEAK_ALONGSIDE_CANDIDATE");

        Assert.assertEquals(AlongsideEventIdGenerator.generate(event), AlongsideEventIdGenerator.generate(event));
        Assert.assertEquals(32, AlongsideEventIdGenerator.generate(event).length());
    }

    @Test
    public void shouldGenerateDifferentIdWhenPairOrSubtypeChanges() {
        String base = AlongsideEventIdGenerator.generate(event("100-200", "WEAK_ALONGSIDE_CANDIDATE"));

        Assert.assertNotEquals(base, AlongsideEventIdGenerator.generate(event("100-201", "WEAK_ALONGSIDE_CANDIDATE")));
        Assert.assertNotEquals(base, AlongsideEventIdGenerator.generate(event("100-200", "ALONGSIDE_CONFIRMED_CANDIDATE")));
    }

    private AlongsideEvent event(String pairId, String subType) {
        AlongsideEvent event = new AlongsideEvent();
        event.setRuleVersion("ALONGSIDE_V2_INSURANCE");
        event.setPairId(pairId);
        event.setEventSubType(subType);
        event.setStartTime(Instant.parse("2026-06-29T00:00:00Z"));
        event.setEndTime(Instant.parse("2026-06-29T00:30:00Z"));
        return event;
    }
}
