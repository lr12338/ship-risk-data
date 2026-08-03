package com.hifleet.maritime;

import java.util.LinkedHashMap;
import java.util.Map;

public class MaritimeSceneTileCache {
    public interface Loader<T> {
        T load(String tileId);
    }

    private final int maxTiles;
    private final LinkedHashMap<String, MaritimeSceneTileLoader.TileData> values =
            new LinkedHashMap<String, MaritimeSceneTileLoader.TileData>(16, 0.75f, true);

    public MaritimeSceneTileCache(int maxTiles) {
        this.maxTiles = Math.max(1, maxTiles);
    }

    public synchronized MaritimeSceneTileLoader.TileData get(String tileId) {
        return values.get(tileId);
    }

    public synchronized MaritimeSceneTileLoader.TileData getOrLoad(
            String tileId, Loader<MaritimeSceneTileLoader.TileData> loader) {
        MaritimeSceneTileLoader.TileData value = values.get(tileId);
        if (value == null) {
            value = loader.load(tileId);
            values.put(tileId, value);
            while (values.size() > maxTiles) {
                values.remove(values.entrySet().iterator().next().getKey());
            }
        }
        return value;
    }

    public synchronized int size() { return values.size(); }
    public synchronized void clear() { values.clear(); }
    public int getMaxTiles() { return maxTiles; }
}
