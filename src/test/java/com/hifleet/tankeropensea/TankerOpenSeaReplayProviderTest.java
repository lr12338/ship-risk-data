package com.hifleet.tankeropensea;

import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.service.SpatialAisReplayClient;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TankerOpenSeaReplayProviderTest {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    public void truncatedQueryIsSplitUntilComplete() {
        TankerOpenSeaReplayProvider provider = provider(new SplitClient(false));

        TankerOpenSeaReplayProvider.SearchResult result =
                provider.search("123456789", points(), "test-batch");

        assertEquals(ReplayCompleteness.TRUNCATED_RETRY_SPLIT,
                result.getCompleteness());
        assertEquals(0, result.getUnresolvedTruncations());
    }

    @Test
    public void unresolvedTruncationNeverBecomesComplete() {
        TankerOpenSeaReplayProvider provider = provider(new SplitClient(true));

        TankerOpenSeaReplayProvider.SearchResult result =
                provider.search("123456789", points(), "test-batch");

        assertEquals(ReplayCompleteness.TRUNCATED_UNRESOLVED,
                result.getCompleteness());
    }

    private TankerOpenSeaReplayProvider provider(SpatialAisReplayClient client) {
        TankerOpenSeaReplayProvider provider = new TankerOpenSeaReplayProvider();
        ReflectionTestUtils.setField(provider, "client", client);
        ReflectionTestUtils.setField(provider, "devSize", 50000);
        ReflectionTestUtils.setField(provider, "initialWindowMinutes", 30);
        ReflectionTestUtils.setField(provider, "minSplitMinutes", 5);
        ReflectionTestUtils.setField(provider, "bboxBufferNm", 0.5);
        return provider;
    }

    private List<TrajectoryPoint> points() {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i <= 6; i++) {
            result.add(new TrajectoryPoint("123456789",
                    start.plusMinutes(i * 5L), 120.0 + i * 0.001,
                    20.0, 2.0, 90.0, "under way"));
        }
        return result;
    }

    private static class SplitClient extends SpatialAisReplayClient {
        private final boolean alwaysTruncated;

        private SplitClient(boolean alwaysTruncated) {
            this.alwaysTruncated = alwaysTruncated;
        }

        @Override
        public ReplayQueryResult query(ReplayQueryRequest request) {
            LocalDateTime start = LocalDateTime.parse(request.getStartTime(), FORMATTER);
            LocalDateTime end = LocalDateTime.parse(request.getEndTime(), FORMATTER);
            ReplayQueryResult result = new ReplayQueryResult();
            result.setSuccess(true);
            result.setTruncated(alwaysTruncated
                    || Duration.between(start, end).toMinutes() > 5L);
            return result;
        }
    }
}
