package com.hifleet.worldsea;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorldSeaMaskService {
    private final WorldSeaMaskLoader loader;
    private volatile WorldSeaMaskSnapshot snapshot;

    @Autowired
    public WorldSeaMaskService(WorldSeaMaskLoader loader) { this.loader = loader; }

    public WorldSeaMaskSnapshot requireSnapshot() {
        WorldSeaMaskSnapshot current = snapshot;
        if (current == null) {
            synchronized (this) {
                current = snapshot;
                if (current == null) {
                    current = loader.load();
                    if (current == null || current.getFeatureCount() == 0) {
                        throw new IllegalStateException("WORLD_SEA_MASK index is empty");
                    }
                    snapshot = current;
                }
            }
        }
        return current;
    }

    public WorldSeaMatch match(double lon, double lat) { return requireSnapshot().match(lon, lat); }
}
