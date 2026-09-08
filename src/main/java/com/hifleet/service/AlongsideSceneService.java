package com.hifleet.service;

import com.hifleet.domain.ZoneDefinition;
import com.hifleet.loitering.LoiteringPortSceneProperties;
import com.hifleet.loitering.LoiteringPortSceneService;
import com.hifleet.loitering.LoiteringPortSceneSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 将事件位置映射为已知港口、已知锚地或已知区域之外。这里没有全球海岸线，不能判定开放海域。
 */
@Component
public class AlongsideSceneService {

    @Autowired
    private ZoneLoaderService zoneLoaderService;

    @Autowired(required = false)
    private LoiteringPortSceneService loiteringPortSceneService;

    @Autowired(required = false)
    private LoiteringPortSceneProperties loiteringPortSceneProperties;

    private volatile SceneIndex sceneIndex;

    public Scene classify(double lon, double lat) {
        if (loiteringPortSceneService != null && loiteringPortSceneProperties != null) {
            try {
                return Scene.from(loiteringPortSceneService.requireSnapshot().match(lon, lat,
                        loiteringPortSceneProperties.getMaxDistanceNm()));
            } catch (RuntimeException ex) {
                return Scene.unknown();
            }
        }
        SceneIndex index = getSceneIndex();
        if (!index.available) {
            return Scene.unknown();
        }
        boolean nearPort = index.portMatcher != null && index.portMatcher.match(lon, lat) != null;
        boolean nearAnchor = index.anchorMatcher != null && index.anchorMatcher.match(lon, lat) != null;
        return new Scene(nearPort, nearAnchor, !nearPort && !nearAnchor, true);
    }

    private SceneIndex getSceneIndex() {
        SceneIndex current = sceneIndex;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (sceneIndex == null) {
                List<ZoneDefinition> ports = zoneLoaderService.loadPortZones(Collections.<Integer>emptyList());
                List<ZoneDefinition> anchors = zoneLoaderService.loadAnchorZones();
                boolean available = !ports.isEmpty() && !anchors.isEmpty();
                sceneIndex = new SceneIndex(
                        ports.isEmpty() ? null : new ZoneSpatialMatcher(ports),
                        anchors.isEmpty() ? null : new ZoneSpatialMatcher(anchors),
                        available);
            }
            return sceneIndex;
        }
    }

    public static class Scene {
        private final boolean nearPort;
        private final boolean nearAnchor;
        private final boolean outsideKnownPortAndAnchor;
        private final boolean dataAvailable;
        private final String sceneType;
        private final Integer nearestPortId;
        private final String nearestPortName;
        private final double distanceToNearestPortNm;

        private Scene(boolean nearPort, boolean nearAnchor, boolean outsideKnownPortAndAnchor,
                      boolean dataAvailable) {
            this.nearPort = nearPort;
            this.nearAnchor = nearAnchor;
            this.outsideKnownPortAndAnchor = outsideKnownPortAndAnchor;
            this.dataAvailable = dataAvailable;
            this.sceneType = !dataAvailable ? "UNKNOWN"
                    : nearPort ? "PORT_OPERATIONAL" : nearAnchor ? "ANCHORAGE" : "OFFSHORE";
            this.nearestPortId = null;
            this.nearestPortName = null;
            this.distanceToNearestPortNm = Double.NaN;
        }

        private Scene(LoiteringPortSceneSnapshot.Match match) {
            this.nearPort = match.isOperational(); this.nearAnchor = match.isAnchor();
            this.outsideKnownPortAndAnchor = !nearPort && !nearAnchor; this.dataAvailable = true;
            this.sceneType = match.getSceneType(); this.nearestPortId = match.getNearestPortId();
            this.nearestPortName = match.getNearestPortName(); this.distanceToNearestPortNm = match.getDistanceToPortBoundaryNm();
        }

        public static Scene unknown() {
            return new Scene(false, false, false, false);
        }
        public static Scene from(LoiteringPortSceneSnapshot.Match match) { return new Scene(match); }

        public boolean isNearPort() {
            return nearPort;
        }

        public boolean isNearAnchor() {
            return nearAnchor;
        }

        public boolean isOpenSea() {
            return false;
        }

        public boolean isOutsideKnownPortAndAnchor() { return outsideKnownPortAndAnchor; }

        public boolean isDataAvailable() {
            return dataAvailable;
        }
        public String getSceneType() { return sceneType; }
        public Integer getNearestPortId() { return nearestPortId; }
        public String getNearestPortName() { return nearestPortName; }
        public double getDistanceToNearestPortNm() { return distanceToNearestPortNm; }
    }

    private static class SceneIndex {
        private final ZoneSpatialMatcher portMatcher;
        private final ZoneSpatialMatcher anchorMatcher;
        private final boolean available;

        private SceneIndex(ZoneSpatialMatcher portMatcher, ZoneSpatialMatcher anchorMatcher, boolean available) {
            this.portMatcher = portMatcher;
            this.anchorMatcher = anchorMatcher;
            this.available = available;
        }
    }
}
