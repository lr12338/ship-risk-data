package com.hifleet.service;

import com.hifleet.domain.ZoneDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 将事件中心点映射为港口、锚地或开放海域。维表不可用时返回未知，避免误升风险。
 */
@Component
public class AlongsideSceneService {

    @Autowired
    private ZoneLoaderService zoneLoaderService;

    private volatile SceneIndex sceneIndex;

    public Scene classify(double lon, double lat) {
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
        private final boolean openSea;
        private final boolean dataAvailable;

        private Scene(boolean nearPort, boolean nearAnchor, boolean openSea, boolean dataAvailable) {
            this.nearPort = nearPort;
            this.nearAnchor = nearAnchor;
            this.openSea = openSea;
            this.dataAvailable = dataAvailable;
        }

        public static Scene unknown() {
            return new Scene(false, false, false, false);
        }

        public boolean isNearPort() {
            return nearPort;
        }

        public boolean isNearAnchor() {
            return nearAnchor;
        }

        public boolean isOpenSea() {
            return openSea;
        }

        public boolean isDataAvailable() {
            return dataAvailable;
        }
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
