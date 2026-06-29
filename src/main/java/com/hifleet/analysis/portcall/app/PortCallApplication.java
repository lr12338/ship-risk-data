package com.hifleet.analysis.portcall.app;

import com.hifleet.analysis.portcall.flink.FlinkPortCallJob;

/**
 * Compatibility entry point that starts the Flink ship-stop detection job.
 */
public final class PortCallApplication {

    private PortCallApplication() {
    }

    /**
     * Starts the service process.
     *
     * @param args startup arguments
     * @throws Exception when the Flink job fails
     */
    public static void main(String[] args) throws Exception {
        FlinkPortCallJob.main(args);
    }
}
