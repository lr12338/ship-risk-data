package com.hifleet.service;

import com.hifleet.service.LoiteringV3EpisodeBuilder.Episode;
import com.hifleet.service.LoiteringV3EpisodeBuilder.Observation;
import com.hifleet.service.LoiteringV3EventReconciler.Candidate;
import com.hifleet.service.LoiteringV3EventReconciler.ExistingEvent;
import com.hifleet.service.LoiteringV3EventReconciler.Reconciliation;
import com.hifleet.service.LoiteringV3FeatureExtractor.Features;
import com.hifleet.service.LoiteringV3RuleEngine.Context;
import com.hifleet.service.LoiteringV3RuleEngine.Decision;
import com.hifleet.trajectory.TrajectoryPoint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loitering V3 小时分析编排核心。数据查询、场景匹配和持久化由外部适配器负责。
 */
public class LoiteringV3AnalyzerService {

    public static final String EVENT_TYPE = "LOITERING";
    public static final String RULE_VERSION = "LOITERING_V3_SHADOW";

    private final MerchantShipProvider merchantShipProvider;
    private final TrajectorySource trajectorySource;
    private final PortContextProvider portContextProvider;
    private final LoiteringV3EpisodeBuilder episodeBuilder = new LoiteringV3EpisodeBuilder();
    private final LoiteringV3FeatureExtractor featureExtractor = new LoiteringV3FeatureExtractor();
    private final LoiteringV3RuleEngine ruleEngine = new LoiteringV3RuleEngine();
    private final LoiteringV3EventReconciler reconciler = new LoiteringV3EventReconciler();

    public LoiteringV3AnalyzerService(MerchantShipProvider merchantShipProvider,
                                     TrajectorySource trajectorySource,
                                     PortContextProvider portContextProvider) {
        if (merchantShipProvider == null || trajectorySource == null
                || portContextProvider == null) {
            throw new IllegalArgumentException("analyzer providers must not be null");
        }
        this.merchantShipProvider = merchantShipProvider;
        this.trajectorySource = trajectorySource;
        this.portContextProvider = portContextProvider;
    }

    public RunResult analyze(LocalDateTime runTime) {
        if (runTime == null) {
            throw new IllegalArgumentException("runTime must not be null");
        }
        LocalDateTime windowStart = runTime.minusHours(24);
        List<Ship> ships = merchantShipProvider.listEligibleShips(windowStart, runTime);
        if (ships == null) {
            ships = Collections.emptyList();
        }

        List<AnalyzedEvent> events = new ArrayList<AnalyzedEvent>();
        List<String> failedMmsi = new ArrayList<String>();
        for (Ship ship : ships) {
            try {
                analyzeShip(ship, windowStart, runTime, events);
            } catch (RuntimeException exception) {
                failedMmsi.add(ship == null || ship.getMmsi() == null
                        ? "UNKNOWN" : ship.getMmsi());
            }
        }
        return new RunResult(windowStart, runTime, ships.size(), failedMmsi, events);
    }

    private void analyzeShip(Ship ship, LocalDateTime windowStart,
                             LocalDateTime windowEnd, List<AnalyzedEvent> target) {
        if (ship == null || isBlank(ship.getMmsi())) {
            throw new IllegalArgumentException("ship MMSI must not be blank");
        }
        List<TrajectoryPoint> points = trajectorySource.load(
                ship.getMmsi(), windowStart, windowEnd);
        List<Observation> observations = portContextProvider.annotate(
                ship, points == null ? Collections.<TrajectoryPoint>emptyList() : points);
        List<Episode> episodes = episodeBuilder.build(observations);
        for (Episode episode : episodes) {
            Features features = featureExtractor.extract(episode.getPoints());
            Context context = portContextProvider.contextFor(
                    ship, episode, features, observations);
            Decision decision = ruleEngine.evaluate(features, context);
            Reconciliation reconciliation = reconciler.reconcile(
                    new Candidate(ship.getMmsi(), RULE_VERSION,
                            episode.getStartTime(), episode.getEndTime(),
                            features.getCenterLon(), features.getCenterLat()),
                    Collections.<ExistingEvent>emptyList());
            target.add(new AnalyzedEvent(reconciliation.getEventKey(), ship, episode,
                    features, context, decision));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface MerchantShipProvider {
        List<Ship> listEligibleShips(LocalDateTime windowStart, LocalDateTime windowEnd);
    }

    public interface TrajectorySource {
        List<TrajectoryPoint> load(String mmsi, LocalDateTime windowStart,
                                   LocalDateTime windowEnd);
    }

    public interface PortContextProvider {
        List<Observation> annotate(Ship ship, List<TrajectoryPoint> points);

        Context contextFor(Ship ship, Episode episode, Features features,
                           List<Observation> windowObservations);
    }

    public static class Ship {
        private final String mmsi;
        private final String imo;
        private final String shipName;
        private final String shipType;

        public Ship(String mmsi, String imo, String shipName, String shipType) {
            this.mmsi = mmsi;
            this.imo = imo;
            this.shipName = shipName;
            this.shipType = shipType;
        }

        public String getMmsi() {
            return mmsi;
        }

        public String getImo() {
            return imo;
        }

        public String getShipName() {
            return shipName;
        }

        public String getShipType() {
            return shipType;
        }
    }

    public static class AnalyzedEvent {
        private final String eventKey;
        private final Ship ship;
        private final Episode episode;
        private final Features features;
        private final Context context;
        private final Decision decision;

        private AnalyzedEvent(String eventKey, Ship ship, Episode episode,
                              Features features, Context context, Decision decision) {
            this.eventKey = eventKey;
            this.ship = ship;
            this.episode = episode;
            this.features = features;
            this.context = context;
            this.decision = decision;
        }

        public String getEventKey() {
            return eventKey;
        }

        public String getMmsi() {
            return ship.getMmsi();
        }

        public String getImo() {
            return ship.getImo();
        }

        public String getShipName() {
            return ship.getShipName();
        }

        public String getShipType() {
            return ship.getShipType();
        }

        public String getEventType() {
            return EVENT_TYPE;
        }

        public String getRuleVersion() {
            return RULE_VERSION;
        }

        public LocalDateTime getStartTime() {
            return episode.getStartTime();
        }

        public LocalDateTime getEndTime() {
            return episode.getEndTime();
        }

        public long getDurationMinutes() {
            return features.getDurationMinutes();
        }

        public double getAverageSpeed() {
            return features.getAverageSpeed();
        }

        public double getRadiusNm() {
            return features.getRadiusNm();
        }

        public String getTrajectoryPattern() {
            return decision.getTrajectoryPattern();
        }

        public boolean isNearPort() {
            return context != null && context.isNearPort();
        }

        public boolean isInsidePort() {
            return context != null && context.isInsidePort();
        }

        public double getConfidenceScore() {
            return decision.getConfidenceScore();
        }

        public double getRiskScore() {
            return decision.getRiskScore();
        }

        public String getStatus() {
            return decision.getStatus();
        }

        public String getReasonCode() {
            return decision.getReasonCode();
        }

        public List<TrajectoryPoint> getTrajectory() {
            return episode.getPoints();
        }

        public boolean isOpen() {
            return episode.isOpen();
        }
    }

    public static class RunResult {
        private final LocalDateTime windowStart;
        private final LocalDateTime windowEnd;
        private final int analyzedShipCount;
        private final List<String> failedMmsi;
        private final List<AnalyzedEvent> events;

        private RunResult(LocalDateTime windowStart, LocalDateTime windowEnd,
                          int analyzedShipCount, List<String> failedMmsi,
                          List<AnalyzedEvent> events) {
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.analyzedShipCount = analyzedShipCount;
            this.failedMmsi = Collections.unmodifiableList(
                    new ArrayList<String>(failedMmsi));
            this.events = Collections.unmodifiableList(
                    new ArrayList<AnalyzedEvent>(events));
        }

        public LocalDateTime getWindowStart() {
            return windowStart;
        }

        public LocalDateTime getWindowEnd() {
            return windowEnd;
        }

        public int getAnalyzedShipCount() {
            return analyzedShipCount;
        }

        public int getFailedShipCount() {
            return failedMmsi.size();
        }

        public List<String> getFailedMmsi() {
            return failedMmsi;
        }

        public List<AnalyzedEvent> getEvents() {
            return events;
        }
    }
}
