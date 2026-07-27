package com.hifleet.job.services;

import com.hifleet.domain.AlongsideReplaySearchResult;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorEventDetail;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.repository.ShipBehaviorEventDetailRepository;
import com.hifleet.repository.ShipBehaviorEventRepository;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.service.ShipAlongsideReplayCandidateProvider;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShipBehaviorAnalysisServiceHistoryTest {

    @Test
    public void historyWindowWritesOnlyWhenExplicitlyEnabled() {
        RecordingEventRepository eventRepository = new RecordingEventRepository();
        RecordingDetailRepository detailRepository = new RecordingDetailRepository();
        ShipBehaviorAnalysisService service = new ShipBehaviorAnalysisService();
        ReflectionTestUtils.setField(service, "trajectoryLoaderService", new EmptyTrajectoryLoader());
        ReflectionTestUtils.setField(service, "alongsideReplayCandidateProvider", new EmptyCandidateProvider());
        ReflectionTestUtils.setField(service, "ais41GeneralQueryRepository", new EmptyShipRepository());
        ReflectionTestUtils.setField(service, "shipBehaviorEventRepository", eventRepository);
        ReflectionTestUtils.setField(service, "shipBehaviorEventDetailRepository", detailRepository);

        service.analysisAlongsideHistoryWindow("413000001",
                "2025-01-01 00:00:00", "2025-01-02 00:00:00", "dry-run", false);

        assertFalse(eventRepository.called);
        assertFalse(detailRepository.called);

        service.analysisAlongsideHistoryWindow("413000001",
                "2025-01-01 00:00:00", "2025-01-02 00:00:00", "write-run", true);

        assertTrue(eventRepository.called);
        assertTrue(detailRepository.called);
        assertTrue(eventRepository.deleted);
        assertTrue(detailRepository.deleted);
    }

    private static class EmptyTrajectoryLoader extends TrajectoryLoaderService {
        @Override
        public List<TrajectoryPoint> loadSafely(String mmsi, String startTime, String endTime,
                                                int simplifyZoom, int sliceHours, int maxPointsPerWindow) {
            return Collections.emptyList();
        }
    }

    private static class EmptyCandidateProvider extends ShipAlongsideReplayCandidateProvider {
        @Override
        public AlongsideReplaySearchResult findCandidates(
                String ownMmsi, List<ShipAlongsideDetector.LowSpeedWindow> windows,
                Map<String, List<TrajectoryPoint>> ownPointsByWindow, String batchNo) {
            return new AlongsideReplaySearchResult();
        }
    }

    private static class EmptyShipRepository extends AIS41GeneralQueryRepository {
        @Override
        public Map<String, String> selectShipTypes(Set<String> mmsis) {
            return Collections.emptyMap();
        }
    }

    private static class RecordingEventRepository extends ShipBehaviorEventRepository {
        private boolean called;
        private boolean deleted;

        @Override
        public void saveEvents(List<ShipBehaviorEvent> events) {
            called = true;
        }

        @Override
        public void deleteAlongsideWindow(String batchNo, String mmsi, String startTime, String endTime) {
            deleted = true;
        }
    }

    private static class RecordingDetailRepository extends ShipBehaviorEventDetailRepository {
        private boolean called;
        private boolean deleted;

        @Override
        public void saveDetails(List<ShipBehaviorEventDetail> details) {
            called = true;
        }

        @Override
        public void deleteAlongsideWindow(String batchNo, String mmsi, String startTime, String endTime) {
            deleted = true;
        }
    }
}
