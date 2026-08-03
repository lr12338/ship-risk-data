package com.hifleet.tankeropensea;

public enum ReplayCompleteness {
    COMPLETE,
    TRUNCATED_RETRY_SPLIT,
    TRUNCATED_UNRESOLVED,
    PARTIAL_TIMEOUT,
    FAILED
}
