package com.hifleet.sts.service;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class StsFleetBackfillFailurePolicyTest {

    @Test
    public void failedShipMakesTaskFailClosed() {
        IllegalStateException failure = StsFleetBackfillService.failedShipException(
                1, Arrays.asList("IMO:1234567: trajectory failed"));

        assertTrue(failure.getMessage().contains("FAILED_SHIPS=1"));
        assertTrue(failure.getMessage().contains("IMO:1234567"));
    }
}
