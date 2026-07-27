package com.hifleet.job.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorEventDetail;
import com.hifleet.domain.ZoneDefinition;
import com.hifleet.repository.ShipBehaviorEventDetailRepository;
import com.hifleet.repository.ShipBehaviorEventRepository;
import com.hifleet.repository.ShipBehaviorLoiteringBatchStatusRepository;
import com.hifleet.service.LoiteringV3AnalyzerService;
import com.hifleet.service.LoiteringV3AnalyzerService.AnalyzedEvent;
import com.hifleet.service.LoiteringV3AnalyzerService.PortContextProvider;
import com.hifleet.service.LoiteringV3AnalyzerService.RunResult;
import com.hifleet.service.LoiteringV3AnalyzerService.Ship;
import com.hifleet.service.LoiteringV3AnalyzerService.TrajectorySource;
import com.hifleet.service.LoiteringV3EpisodeBuilder.Episode;
import com.hifleet.service.LoiteringV3EpisodeBuilder.Observation;
import com.hifleet.service.LoiteringV3FeatureExtractor.Features;
import com.hifleet.service.LoiteringV3HBaseTrajectorySource;
import com.hifleet.service.LoiteringV3RuleEngine;
import com.hifleet.service.LoiteringV3RuleEngine.Context;
import com.hifleet.service.ZoneLoaderService;
import com.hifleet.service.ZoneSpatialMatcher;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import com.xxl.job.core.log.XxlJobLogger;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Loitering V3 的分页批量运行适配器。只负责任务参数、分页、切窗、持久化和失败隔离。
 */
@Service
public class ShipLoiteringV3BatchService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ShipLoiteringV3BatchService.class);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String BATCH_PREFIX = "loitering-v3-";

    private final ShipPageSource shipPageSource;
    private final ShipWindowAnalyzer shipWindowAnalyzer;
    private final EventStore eventStore;
    private final ProgressStore progressStore;
    private final JobLog jobLog;
    private final Clock clock;
    private final int testShipLimit;
    private final int pageSize;
    private final int maxPageSize;
    private final int windowHours;

    @Autowired
    public ShipLoiteringV3BatchService(
            @Qualifier("aid41JdbcTemplate") JdbcTemplate ais41JdbcTemplate,
            @Qualifier("newDataanalysisJdbcTemplate") JdbcTemplate dataanalysisJdbcTemplate,
            @Qualifier("newDataanalysisDataSource") DataSource dataanalysisDataSource,
            TrajectoryLoaderService trajectoryLoaderService,
            ZoneLoaderService zoneLoaderService,
            ShipBehaviorEventRepository eventRepository,
            ShipBehaviorEventDetailRepository detailRepository,
            ShipBehaviorLoiteringBatchStatusRepository statusRepository,
            @Value("${ship.behavior.loitering.v3.batch.test-ship-limit:100}") int testShipLimit,
            @Value("${ship.behavior.loitering.v3.batch.page-size:500}") int pageSize,
            @Value("${ship.behavior.loitering.v3.batch.max-page-size:500}") int maxPageSize,
            @Value("${ship.behavior.loitering.v3.batch.window-hours:24}") int windowHours,
            @Value("${ship.behavior.loitering.v3.batch.trajectory-load-slice-hours:6}")
                    int trajectorySliceHours,
            @Value("${ship.behavior.loitering.v3.batch.max-points-per-window:100000}")
                    int maxPointsPerWindow) {
        this(new JdbcShipPageSource(ais41JdbcTemplate),
                new DefaultShipWindowAnalyzer(trajectoryLoaderService, zoneLoaderService,
                        trajectorySliceHours, maxPointsPerWindow),
                new ReplacingEventStore(dataanalysisJdbcTemplate, dataanalysisDataSource,
                        eventRepository, detailRepository),
                new JdbcProgressStore(dataanalysisJdbcTemplate, statusRepository),
                new XxlJobLog(), Clock.system(BUSINESS_ZONE),
                testShipLimit, pageSize, maxPageSize, windowHours);
    }

    ShipLoiteringV3BatchService(ShipPageSource shipPageSource,
                                ShipWindowAnalyzer shipWindowAnalyzer,
                                EventStore eventStore,
                                ProgressStore progressStore,
                                JobLog jobLog,
                                Clock clock,
                                int testShipLimit,
                                int pageSize,
                                int maxPageSize,
                                int windowHours) {
        this.shipPageSource = shipPageSource;
        this.shipWindowAnalyzer = shipWindowAnalyzer;
        this.eventStore = eventStore;
        this.progressStore = progressStore;
        this.jobLog = jobLog;
        this.clock = clock;
        this.testShipLimit = Math.max(1, testShipLimit);
        this.pageSize = Math.max(1, pageSize);
        this.maxPageSize = Math.max(1, maxPageSize);
        this.windowHours = Math.max(1, windowHours);
    }

    public BatchResult execute(String params) {
        JobRequest request = parseRequest(
                params, clock, testShipLimit, pageSize, maxPageSize);
        long startedAt = System.currentTimeMillis();
        List<Ship> ships = shipPageSource.select(
                request.getOffset(), request.getLimit(), request.getUpdatedAfter());
        if (ships == null) {
            ships = Collections.emptyList();
        }

        jobLog.log("Loitering V3 Batch Start mode=" + request.getMode()
                + " startTime=" + format(request.getStartTime())
                + " endTime=" + format(request.getEndTime())
                + " offset=" + request.getOffset()
                + " limit=" + request.getLimit()
                + " ship count=" + ships.size()
                + " batchNo=" + request.getBatchNo());

        int success = 0;
        int failed = 0;
        int totalEvents = 0;
        int totalPoints = 0;
        for (Ship ship : ships) {
            LocalDateTime shipStart = request.getStartTime();
            if ("increment".equals(request.getMode())) {
                LocalDateTime lastCalculatedAt =
                        progressStore.lastCalculatedAt(ship == null ? null : ship.getMmsi());
                if (lastCalculatedAt != null && lastCalculatedAt.isAfter(shipStart)) {
                    shipStart = lastCalculatedAt;
                }
            }
            if (!shipStart.isBefore(request.getEndTime())) {
                success++;
                jobLog.log("MMSI=" + mmsi(ship)
                        + " status=SKIPPED_ALREADY_CALCULATED trajectory points=0 events=0");
                continue;
            }

            boolean shipFailed = false;
            int shipEvents = 0;
            int shipPoints = 0;
            LocalDateTime cursor = shipStart;
            while (cursor.isBefore(request.getEndTime())) {
                LocalDateTime windowEnd = cursor.plusHours(windowHours);
                if (windowEnd.isAfter(request.getEndTime())) {
                    windowEnd = request.getEndTime();
                }
                try {
                    WindowResult windowResult =
                            shipWindowAnalyzer.analyze(ship, cursor, windowEnd);
                    eventStore.replace(ship, cursor, windowEnd, request.getBatchNo(),
                            windowResult.getEvents());
                    progressStore.markDone(request.getBatchNo(), ship.getMmsi(),
                            cursor, windowEnd, windowResult.getPointCount(),
                            windowResult.getEvents().size());
                    shipPoints += windowResult.getPointCount();
                    shipEvents += windowResult.getEvents().size();
                    totalPoints += windowResult.getPointCount();
                    totalEvents += windowResult.getEvents().size();
                    jobLog.log("MMSI=" + ship.getMmsi()
                            + " startTime=" + format(cursor)
                            + " endTime=" + format(windowEnd)
                            + " trajectory points=" + windowResult.getPointCount()
                            + " events=" + windowResult.getEvents().size()
                            + " status=SUCCESS");
                } catch (Exception ex) {
                    shipFailed = true;
                    failed++;
                    markFailedSafely(request.getBatchNo(), mmsi(ship),
                            cursor, windowEnd, ex.getMessage());
                    LOGGER.error("Loitering V3 ship window failed, mmsi={}, start={}, end={}",
                            mmsi(ship), cursor, windowEnd, ex);
                    jobLog.log("MMSI=" + mmsi(ship)
                            + " startTime=" + format(cursor)
                            + " endTime=" + format(windowEnd)
                            + " status=FAILED error=" + safeMessage(ex));
                    break;
                }
                cursor = windowEnd;
            }
            if (!shipFailed) {
                success++;
                jobLog.log("MMSI=" + mmsi(ship)
                        + " trajectory points=" + shipPoints
                        + " events=" + shipEvents
                        + " status=DONE");
            }
        }

        long durationSeconds = (System.currentTimeMillis() - startedAt) / 1000L;
        BatchResult result = new BatchResult(request.getMode(), ships.size(),
                success, failed, totalPoints, totalEvents, durationSeconds);
        jobLog.log("Loitering V3 Batch Summary mode=" + request.getMode()
                + " ship count=" + ships.size()
                + " success=" + success
                + " failed=" + failed
                + " trajectory points=" + totalPoints
                + " events=" + totalEvents
                + " duration=" + durationSeconds + "s"
                + " batchNo=" + request.getBatchNo());
        return result;
    }

    private void markFailedSafely(String batchNo, String mmsi,
                                  LocalDateTime startTime, LocalDateTime endTime,
                                  String errorMessage) {
        try {
            progressStore.markFailed(batchNo, mmsi, startTime, endTime, errorMessage);
        } catch (Exception progressException) {
            LOGGER.error("mark Loitering V3 progress failed, mmsi={}", mmsi,
                    progressException);
        }
    }

    static JobRequest parseRequest(String params, Clock clock,
                                   int testShipLimit, int pageSize, int maxPageSize) {
        JSONObject json = StringUtils.isBlank(params)
                ? new JSONObject() : JSON.parseObject(params.trim());
        String mode = StringUtils.defaultIfBlank(
                json.getString("mode"), "test").trim().toLowerCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        int offset = integer(json, "offset", 0);
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }

        LocalDateTime startTime;
        LocalDateTime endTime;
        LocalDateTime updatedAfter = null;
        int defaultLimit;
        if ("test".equals(mode)) {
            int days = positiveInteger(json, "days", 30);
            endTime = now;
            startTime = endTime.minusDays(days);
            defaultLimit = testShipLimit;
        } else if ("history".equals(mode)) {
            startTime = parseRequiredTime(json.getString("startTime"), "startTime");
            endTime = parseRequiredTime(json.getString("endTime"), "endTime");
            defaultLimit = pageSize;
        } else if ("increment".equals(mode)) {
            int hours = positiveInteger(json, "hours", 24);
            endTime = now;
            startTime = endTime.minusHours(hours);
            updatedAfter = startTime;
            defaultLimit = pageSize;
        } else {
            throw new IllegalArgumentException(
                    "mode must be test, history or increment");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }

        int limit = positiveInteger(json, "limit", defaultLimit);
        if (limit > maxPageSize) {
            throw new IllegalArgumentException(
                    "limit must not exceed " + maxPageSize);
        }
        String batchNo = StringUtils.defaultIfBlank(json.getString("batchNo"),
                defaultBatchNo(mode, startTime, endTime, offset));
        if (batchNo.length() > 64) {
            throw new IllegalArgumentException("batchNo must not exceed 64 characters");
        }
        return new JobRequest(mode, startTime, endTime, updatedAfter,
                limit, offset, batchNo);
    }

    private static int integer(JSONObject json, String key, int defaultValue) {
        Integer value = json.getInteger(key);
        return value == null ? defaultValue : value;
    }

    private static int positiveInteger(JSONObject json, String key, int defaultValue) {
        int value = integer(json, key, defaultValue);
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return value;
    }

    private static LocalDateTime parseRequiredTime(String value, String field) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(
                    "history mode requires " + field);
        }
        try {
            return LocalDateTime.parse(normalizeTime(value), TIME_FORMATTER);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    field + " must use yyyy-MM-dd HH:mm:ss", ex);
        }
    }

    private static String normalizeTime(String value) {
        String trimmed = value.trim();
        return trimmed.length() == 16 ? trimmed + ":00" : trimmed;
    }

    private static String defaultBatchNo(String mode, LocalDateTime startTime,
                                         LocalDateTime endTime, int offset) {
        if ("increment".equals(mode)) {
            return BATCH_PREFIX + "increment";
        }
        DateTimeFormatter compact = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return BATCH_PREFIX + mode + "-"
                + startTime.format(compact) + "-"
                + endTime.format(compact) + "-o" + offset;
    }

    private static String format(LocalDateTime time) {
        return time == null ? "null" : time.format(TIME_FORMATTER);
    }

    private static String mmsi(Ship ship) {
        return ship == null || StringUtils.isBlank(ship.getMmsi())
                ? "UNKNOWN" : ship.getMmsi();
    }

    private static String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return StringUtils.defaultIfBlank(message,
                exception == null ? "unknown" : exception.getClass().getSimpleName());
    }

    public interface ShipPageSource {
        List<Ship> select(int offset, int limit, LocalDateTime updatedAfter);
    }

    public interface ShipWindowAnalyzer {
        WindowResult analyze(Ship ship, LocalDateTime startTime, LocalDateTime endTime);
    }

    public interface EventStore {
        void replace(Ship ship, LocalDateTime startTime, LocalDateTime endTime,
                     String batchNo, List<AnalyzedEvent> analyzedEvents);
    }

    public interface ProgressStore {
        LocalDateTime lastCalculatedAt(String mmsi);

        void markDone(String batchNo, String mmsi, LocalDateTime startTime,
                      LocalDateTime endTime, int pointCount, int eventCount);

        void markFailed(String batchNo, String mmsi, LocalDateTime startTime,
                        LocalDateTime endTime, String errorMessage);
    }

    public interface JobLog {
        void log(String message);
    }

    public static class WindowResult {
        private final int pointCount;
        private final List<AnalyzedEvent> events;

        public WindowResult(int pointCount, List<AnalyzedEvent> events) {
            this.pointCount = pointCount;
            this.events = events == null
                    ? Collections.<AnalyzedEvent>emptyList() : events;
        }

        public int getPointCount() {
            return pointCount;
        }

        public List<AnalyzedEvent> getEvents() {
            return events;
        }
    }

    public static class JobRequest {
        private final String mode;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final LocalDateTime updatedAfter;
        private final int limit;
        private final int offset;
        private final String batchNo;

        private JobRequest(String mode, LocalDateTime startTime,
                           LocalDateTime endTime, LocalDateTime updatedAfter,
                           int limit, int offset, String batchNo) {
            this.mode = mode;
            this.startTime = startTime;
            this.endTime = endTime;
            this.updatedAfter = updatedAfter;
            this.limit = limit;
            this.offset = offset;
            this.batchNo = batchNo;
        }

        public String getMode() {
            return mode;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public LocalDateTime getUpdatedAfter() {
            return updatedAfter;
        }

        public int getLimit() {
            return limit;
        }

        public int getOffset() {
            return offset;
        }

        public String getBatchNo() {
            return batchNo;
        }
    }

    public static class BatchResult {
        private final String mode;
        private final int shipCount;
        private final int success;
        private final int failed;
        private final int trajectoryPoints;
        private final int events;
        private final long durationSeconds;

        private BatchResult(String mode, int shipCount, int success, int failed,
                            int trajectoryPoints, int events, long durationSeconds) {
            this.mode = mode;
            this.shipCount = shipCount;
            this.success = success;
            this.failed = failed;
            this.trajectoryPoints = trajectoryPoints;
            this.events = events;
            this.durationSeconds = durationSeconds;
        }

        public boolean isSuccess() {
            return failed == 0;
        }

        public int getShipCount() {
            return shipCount;
        }

        public int getSuccess() {
            return success;
        }

        public int getFailed() {
            return failed;
        }

        public String getSummary() {
            return "mode=" + mode
                    + ", shipCount=" + shipCount
                    + ", success=" + success
                    + ", failed=" + failed
                    + ", trajectoryPoints=" + trajectoryPoints
                    + ", events=" + events
                    + ", duration=" + durationSeconds + "s";
        }
    }

    static class JdbcShipPageSource implements ShipPageSource {

        private static final String FILTER =
                " FROM ais41.recentships "
                        + "WHERE `type`!='渔船' AND `type`!='-' AND `type`!='游艇' "
                        + "AND `type`!='工程及服务船' AND `type`!='客船' "
                        + "AND `type`!='拖带船' AND `type`!='拖轮' "
                        + "AND `type`!='专用船' AND `type`!='其他' "
                        + "AND `type`!='未知类型船舶' AND `type`!='近海作业船' "
                        + "AND length>70 AND updatetime>'2025-01-01 00:00:00' "
                        + "AND mmsi BETWEEN 100000000 AND 999999999 ";

        private final JdbcTemplate jdbcTemplate;

        JdbcShipPageSource(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public List<Ship> select(int offset, int limit, LocalDateTime updatedAfter) {
            String sql = "SELECT mmsi, imonumber, name, `type`" + FILTER;
            List<Object> arguments = new ArrayList<Object>();
            if (updatedAfter != null) {
                sql += "AND updatetime>=? ";
                arguments.add(Timestamp.valueOf(updatedAfter));
            }
            sql += "ORDER BY mmsi LIMIT ? OFFSET ?";
            arguments.add(limit);
            arguments.add(offset);
            return jdbcTemplate.query(sql, arguments.toArray(),
                    (rs, rowNum) -> new Ship(rs.getString("mmsi"),
                            rs.getString("imonumber"), rs.getString("name"),
                            rs.getString("type")));
        }
    }

    private static class DefaultShipWindowAnalyzer implements ShipWindowAnalyzer {

        private final LoiteringV3HBaseTrajectorySource trajectorySource;
        private final PortContextProvider portContextProvider;

        private DefaultShipWindowAnalyzer(TrajectoryLoaderService trajectoryLoaderService,
                                          ZoneLoaderService zoneLoaderService,
                                          int trajectorySliceHours,
                                          int maxPointsPerWindow) {
            this.trajectorySource = new LoiteringV3HBaseTrajectorySource(
                    trajectoryLoaderService, Math.max(1, trajectorySliceHours),
                    Math.max(1, maxPointsPerWindow));
            this.portContextProvider = new ScenePortContextProvider(zoneLoaderService);
        }

        @Override
        public WindowResult analyze(final Ship ship, final LocalDateTime startTime,
                                    final LocalDateTime endTime) {
            if (ship == null || StringUtils.isBlank(ship.getMmsi())) {
                throw new IllegalArgumentException("ship MMSI must not be blank");
            }
            final int[] pointCount = new int[1];
            TrajectorySource boundedSource = new TrajectorySource() {
                @Override
                public List<TrajectoryPoint> load(String mmsi,
                                                  LocalDateTime ignoredStart,
                                                  LocalDateTime ignoredEnd) {
                    List<TrajectoryPoint> points =
                            trajectorySource.load(mmsi, startTime, endTime);
                    pointCount[0] = points == null ? 0 : points.size();
                    return points;
                }
            };
            LoiteringV3AnalyzerService analyzer = new LoiteringV3AnalyzerService(
                    (ignoredStart, ignoredEnd) -> Collections.singletonList(ship),
                    boundedSource, portContextProvider);
            RunResult result = analyzer.analyze(endTime);
            if (result.getFailedShipCount() > 0) {
                throw new IllegalStateException(
                        "Loitering V3 analyzer failed for " + result.getFailedMmsi());
            }
            return new WindowResult(pointCount[0], result.getEvents());
        }
    }

    private static class ScenePortContextProvider implements PortContextProvider {

        private final ZoneLoaderService zoneLoaderService;
        private volatile PortSceneIndex sceneIndex;

        private ScenePortContextProvider(ZoneLoaderService zoneLoaderService) {
            this.zoneLoaderService = zoneLoaderService;
        }

        @Override
        public List<Observation> annotate(Ship ship, List<TrajectoryPoint> points) {
            List<Observation> observations = new ArrayList<Observation>();
            if (points == null) {
                return observations;
            }
            for (TrajectoryPoint point : points) {
                PortScene scene = classify(point.getLon(), point.getLat());
                boolean candidateArea = !scene.dataAvailable || scene.insidePort;
                observations.add(new Observation(point, candidateArea,
                        false, false));
            }
            return observations;
        }

        @Override
        public Context contextFor(Ship ship, Episode episode, Features features,
                                  List<Observation> windowObservations) {
            PortScene scene = classify(features.getCenterLon(), features.getCenterLat());
            return Context.builder()
                    .merchantShip(true)
                    .portDataAvailable(scene.dataAvailable)
                    .berthDataAvailable(false)
                    .nearPort(scene.insidePort)
                    .insidePort(scene.insidePort)
                    .insideBerth(false)
                    .nearBerthPoint(false)
                    .nearChannel(false)
                    .build();
        }

        private PortScene classify(double lon, double lat) {
            PortSceneIndex index = getSceneIndex();
            return new PortScene(index.available,
                    index.matcher != null && index.matcher.match(lon, lat) != null);
        }

        private PortSceneIndex getSceneIndex() {
            PortSceneIndex current = sceneIndex;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                if (sceneIndex == null) {
                    List<ZoneDefinition> ports = zoneLoaderService.loadPortZones(
                            Collections.<Integer>emptyList());
                    boolean available = ports != null && !ports.isEmpty();
                    sceneIndex = new PortSceneIndex(available
                            ? new ZoneSpatialMatcher(ports) : null, available);
                }
                return sceneIndex;
            }
        }
    }

    private static class PortScene {
        private final boolean dataAvailable;
        private final boolean insidePort;

        private PortScene(boolean dataAvailable, boolean insidePort) {
            this.dataAvailable = dataAvailable;
            this.insidePort = insidePort;
        }
    }

    private static class PortSceneIndex {
        private final ZoneSpatialMatcher matcher;
        private final boolean available;

        private PortSceneIndex(ZoneSpatialMatcher matcher, boolean available) {
            this.matcher = matcher;
            this.available = available;
        }
    }

    static class ReplacingEventStore implements EventStore {

        private final JdbcTemplate jdbcTemplate;
        private final TransactionTemplate transactionTemplate;
        private final ShipBehaviorEventRepository eventRepository;
        private final ShipBehaviorEventDetailRepository detailRepository;

        ReplacingEventStore(JdbcTemplate jdbcTemplate, DataSource dataSource,
                            ShipBehaviorEventRepository eventRepository,
                            ShipBehaviorEventDetailRepository detailRepository) {
            this.jdbcTemplate = jdbcTemplate;
            this.transactionTemplate = new TransactionTemplate(
                    new DataSourceTransactionManager(dataSource));
            this.eventRepository = eventRepository;
            this.detailRepository = detailRepository;
        }

        @Override
        public void replace(final Ship ship,
                            final LocalDateTime startTime,
                            final LocalDateTime endTime,
                            final String batchNo,
                            final List<AnalyzedEvent> analyzedEvents) {
            transactionTemplate.execute(status -> {
                deleteExisting(ship.getMmsi(), startTime, endTime);
                eventRepository.saveEvents(toEvents(analyzedEvents, batchNo));
                detailRepository.saveDetails(toDetails(analyzedEvents, batchNo));
                return null;
            });
        }

        private void deleteExisting(String mmsi, LocalDateTime startTime,
                                    LocalDateTime endTime) {
            Object[] args = new Object[]{LoiteringV3AnalyzerService.EVENT_TYPE, mmsi,
                    LoiteringV3AnalyzerService.RULE_VERSION,
                    Timestamp.valueOf(startTime), Timestamp.valueOf(endTime)};
            jdbcTemplate.update(
                    "DELETE FROM dataanalysis.ship_behavior_event_detail "
                            + "WHERE behavior_type=? AND mmsi=? AND rule_version=? "
                            + "AND event_start_time>=? AND event_start_time<?", args);
            jdbcTemplate.update(
                    "DELETE FROM dataanalysis.ship_behavior_event "
                            + "WHERE behavior_type=? AND mmsi=? AND rule_version=? "
                            + "AND start_time>=? AND start_time<?", args);
        }

        private List<ShipBehaviorEvent> toEvents(List<AnalyzedEvent> analyzedEvents,
                                                 String batchNo) {
            List<ShipBehaviorEvent> result = new ArrayList<ShipBehaviorEvent>();
            if (analyzedEvents == null) {
                return result;
            }
            for (AnalyzedEvent analyzed : analyzedEvents) {
                List<TrajectoryPoint> trajectory = analyzed.getTrajectory();
                if (trajectory == null || trajectory.isEmpty()) {
                    continue;
                }
                TrajectoryPoint first = trajectory.get(0);
                TrajectoryPoint last = trajectory.get(trajectory.size() - 1);
                double[] center = center(trajectory);
                ShipBehaviorEvent event = new ShipBehaviorEvent();
                event.setBehaviorType(LoiteringV3AnalyzerService.EVENT_TYPE);
                event.setMmsi(analyzed.getMmsi());
                event.setStartTime(format(analyzed.getStartTime()));
                event.setEndTime(format(analyzed.getEndTime()));
                event.setDurationMinutes(analyzed.getDurationMinutes());
                event.setStartLon(first.getLon());
                event.setStartLat(first.getLat());
                event.setEndLon(last.getLon());
                event.setEndLat(last.getLat());
                event.setMaxDistanceNm(analyzed.getRadiusNm());
                boolean nearPort = analyzed.isNearPort() || analyzed.isInsidePort();
                boolean unknownScene = LoiteringV3RuleEngine.REASON_SCENE_DATA_UNAVAILABLE
                        .equals(analyzed.getReasonCode()) && !nearPort;
                event.setNearPort(unknownScene ? null : nearPort);
                event.setOpenSea(unknownScene ? null : !nearPort);
                event.setOwnShipType(analyzed.getShipType());
                event.setAvgSpeed(analyzed.getAverageSpeed());
                event.setPointCount(trajectory.size());
                event.setRuleVersion(LoiteringV3AnalyzerService.RULE_VERSION);
                event.setBatchNo(batchNo);
                event.setEventSubType(analyzed.getTrajectoryPattern());
                event.setSeverityLevel(severity(analyzed.getRiskScore()));
                event.setEventScore(analyzed.getRiskScore());
                event.setConfidenceLevel(confidence(analyzed.getConfidenceScore()));
                event.setExclusionFlag(exclusionFlag(analyzed.getStatus()));
                event.setExclusionReason(analyzed.getReasonCode());
                event.setTrackCoverageRatio(trackCoverage(
                        analyzed.getDurationMinutes(), trajectory.size()));
                event.setCenterLon(center[0]);
                event.setCenterLat(center[1]);
                event.setRadiusNm(analyzed.getRadiusNm());
                event.setScoreDetailJson(scoreDetail(analyzed));
                result.add(event);
            }
            return result;
        }

        private List<ShipBehaviorEventDetail> toDetails(
                List<AnalyzedEvent> analyzedEvents, String batchNo) {
            List<ShipBehaviorEventDetail> result =
                    new ArrayList<ShipBehaviorEventDetail>();
            if (analyzedEvents == null) {
                return result;
            }
            for (AnalyzedEvent event : analyzedEvents) {
                List<TrajectoryPoint> trajectory = event.getTrajectory();
                if (trajectory == null) {
                    continue;
                }
                for (int i = 0; i < trajectory.size(); i++) {
                    TrajectoryPoint point = trajectory.get(i);
                    ShipBehaviorEventDetail detail = new ShipBehaviorEventDetail();
                    detail.setBehaviorType(LoiteringV3AnalyzerService.EVENT_TYPE);
                    detail.setMmsi(event.getMmsi());
                    detail.setPointRole(ShipBehaviorEventDetail.ROLE_OWN);
                    detail.setEventStartTime(format(event.getStartTime()));
                    detail.setEventEndTime(format(event.getEndTime()));
                    detail.setPointTime(format(point.getTime()));
                    detail.setPointIndex(i);
                    detail.setLon(point.getLon());
                    detail.setLat(point.getLat());
                    detail.setSpeed(point.getSpeed());
                    detail.setCourse(point.getCourse());
                    detail.setNavStatus(point.getNavStatus());
                    detail.setBatchNo(batchNo);
                    detail.setRuleVersion(LoiteringV3AnalyzerService.RULE_VERSION);
                    result.add(detail);
                }
            }
            return result;
        }

        private String scoreDetail(AnalyzedEvent event) {
            JSONObject detail = new JSONObject();
            detail.put("eventKey", event.getEventKey());
            detail.put("decisionStatus", event.getStatus());
            detail.put("trajectoryPattern", event.getTrajectoryPattern());
            detail.put("reasonCode", event.getReasonCode());
            detail.put("confidenceScore", event.getConfidenceScore());
            detail.put("riskScore", event.getRiskScore());
            detail.put("openEpisode", event.isOpen());
            return detail.toJSONString();
        }

        private String exclusionFlag(String status) {
            if (LoiteringV3RuleEngine.STATUS_FINAL.equals(status)) {
                return "VALID_RISK_EVENT";
            }
            if (LoiteringV3RuleEngine.STATUS_EXCLUDED.equals(status)) {
                return "EXCLUDED_NORMAL";
            }
            return "REVIEW_REQUIRED";
        }

        private String severity(double riskScore) {
            if (riskScore >= 80.0) {
                return "L4";
            }
            if (riskScore >= 60.0) {
                return "L3";
            }
            if (riskScore >= 40.0) {
                return "L2";
            }
            return riskScore > 0.0 ? "L1" : "L0";
        }

        private String confidence(double score) {
            return score >= 0.80 ? "HIGH" : score >= 0.65 ? "MEDIUM" : "LOW";
        }

        private double trackCoverage(long durationMinutes, int pointCount) {
            double expected = durationMinutes / 5.0 + 1.0;
            return Math.min(1.0, pointCount / Math.max(1.0, expected));
        }

        private double[] center(List<TrajectoryPoint> trajectory) {
            double lon = 0.0;
            double lat = 0.0;
            for (TrajectoryPoint point : trajectory) {
                lon += point.getLon();
                lat += point.getLat();
            }
            return new double[]{lon / trajectory.size(), lat / trajectory.size()};
        }
    }

    private static class JdbcProgressStore implements ProgressStore {

        private final JdbcTemplate jdbcTemplate;
        private final ShipBehaviorLoiteringBatchStatusRepository statusRepository;

        private JdbcProgressStore(
                JdbcTemplate jdbcTemplate,
                ShipBehaviorLoiteringBatchStatusRepository statusRepository) {
            this.jdbcTemplate = jdbcTemplate;
            this.statusRepository = statusRepository;
        }

        @Override
        public LocalDateTime lastCalculatedAt(String mmsi) {
            if (StringUtils.isBlank(mmsi)) {
                return null;
            }
            Timestamp timestamp = jdbcTemplate.queryForObject(
                    "SELECT MAX(window_end_time) "
                            + "FROM dataanalysis.ship_behavior_loitering_batch_status "
                            + "WHERE mmsi=? AND status=? AND batch_no LIKE ?",
                    new Object[]{mmsi,
                            ShipBehaviorLoiteringBatchStatusRepository.STATUS_DONE,
                            BATCH_PREFIX + "%"},
                    Timestamp.class);
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }

        @Override
        public void markDone(String batchNo, String mmsi, LocalDateTime startTime,
                             LocalDateTime endTime, int pointCount, int eventCount) {
            statusRepository.markDone(batchNo, mmsi, format(startTime),
                    format(endTime), pointCount, eventCount);
        }

        @Override
        public void markFailed(String batchNo, String mmsi, LocalDateTime startTime,
                               LocalDateTime endTime, String errorMessage) {
            statusRepository.markFailed(batchNo, mmsi, format(startTime),
                    format(endTime), errorMessage);
        }
    }

    private static class XxlJobLog implements JobLog {
        @Override
        public void log(String message) {
            LOGGER.info(message);
            XxlJobLogger.log(message);
        }
    }
}
