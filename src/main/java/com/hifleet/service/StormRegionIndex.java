package com.hifleet.service;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 任务批次内预加载的大风/大浪区域索引，供本地 JTS 空间计算，避免 per-ship 查询 PostgreSQL。
 */
public class StormRegionIndex {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    private static final int AREA_VALID_HOURS = 6;

    private final STRtree waveIndex;
    private final STRtree windIndex;
    private final Envelope waveEnvelope;
    private final Envelope windEnvelope;

    private StormRegionIndex(STRtree waveIndex, STRtree windIndex, Envelope waveEnvelope, Envelope windEnvelope) {
        this.waveIndex = waveIndex;
        this.windIndex = windIndex;
        this.waveEnvelope = waveEnvelope;
        this.windEnvelope = windEnvelope;
    }

    public static StormRegionIndex empty() {
        STRtree emptyWave = new STRtree();
        emptyWave.build();
        STRtree emptyWind = new STRtree();
        emptyWind.build();
        return new StormRegionIndex(emptyWave, emptyWind, null, null);
    }

    public static StormRegionIndex of(List<StormRegion> waveRegions, List<StormRegion> windRegions) {
        STRtree waveTree = buildTree(waveRegions);
        STRtree windTree = buildTree(windRegions);
        return new StormRegionIndex(waveTree, windTree, unionEnvelope(waveRegions), unionEnvelope(windRegions));
    }

    public boolean isEmpty() {
        return waveIndex.size() == 0 && windIndex.size() == 0;
    }

    public int getWaveCount() {
        return waveIndex.size();
    }

    public int getWindCount() {
        return windIndex.size();
    }

    public boolean mayIntersectWave(Envelope lineEnvelope) {
        return mayIntersect(waveEnvelope, lineEnvelope);
    }

    public boolean mayIntersectWind(Envelope lineEnvelope) {
        return mayIntersect(windEnvelope, lineEnvelope);
    }

    public List<StormRegion> findWaveCandidates(Envelope lineEnvelope, String segStart, String segEnd) {
        return findCandidates(waveIndex, lineEnvelope, segStart, segEnd);
    }

    public List<StormRegion> findWindCandidates(Envelope lineEnvelope, String segStart, String segEnd) {
        return findCandidates(windIndex, lineEnvelope, segStart, segEnd);
    }

    private static STRtree buildTree(List<StormRegion> regions) {
        STRtree tree = new STRtree();
        if (regions != null) {
            for (StormRegion region : regions) {
                if (region != null && region.getGeometry() != null) {
                    tree.insert(region.getGeometry().getEnvelopeInternal(), region);
                }
            }
        }
        tree.build();
        return tree;
    }

    private static Envelope unionEnvelope(List<StormRegion> regions) {
        Envelope envelope = null;
        if (regions != null) {
            for (StormRegion region : regions) {
                if (region != null && region.getGeometry() != null) {
                    Envelope regionEnvelope = region.getGeometry().getEnvelopeInternal();
                    if (envelope == null) {
                        envelope = new Envelope(regionEnvelope);
                    } else {
                        envelope.expandToInclude(regionEnvelope);
                    }
                }
            }
        }
        return envelope;
    }

    private static boolean mayIntersect(Envelope regionEnvelope, Envelope lineEnvelope) {
        return regionEnvelope != null && lineEnvelope != null && regionEnvelope.intersects(lineEnvelope);
    }

    @SuppressWarnings("unchecked")
    private List<StormRegion> findCandidates(STRtree tree, Envelope lineEnvelope, String segStart, String segEnd) {
        if (lineEnvelope == null || tree.size() == 0) {
            return Collections.emptyList();
        }
        List<StormRegion> candidates = new ArrayList<StormRegion>();
        List<StormRegion> queried = tree.query(lineEnvelope);
        for (StormRegion region : queried) {
            if (region != null && regionTimeOverlapsSegment(region.getTimeUtc(), segStart, segEnd)) {
                candidates.add(region);
            }
        }
        return candidates;
    }

    static boolean regionTimeOverlapsSegment(String areaTimeUtc, String segStartBj, String segEndBj) {
        try {
            ZonedDateTime areaStartUtc = LocalDateTime.parse(areaTimeUtc, TIME_FORMATTER).atZone(UTC_ZONE);
            ZonedDateTime areaEndUtc = areaStartUtc.plus(AREA_VALID_HOURS, ChronoUnit.HOURS);
            ZonedDateTime segStartUtc = LocalDateTime.parse(segStartBj, TIME_FORMATTER).atZone(BEIJING_ZONE)
                    .withZoneSameInstant(UTC_ZONE);
            ZonedDateTime segEndUtc = LocalDateTime.parse(segEndBj, TIME_FORMATTER).atZone(BEIJING_ZONE)
                    .withZoneSameInstant(UTC_ZONE);
            return areaStartUtc.isBefore(segEndUtc) && areaEndUtc.isAfter(segStartUtc);
        } catch (Exception ex) {
            return false;
        }
    }

    static final class StormRegion {
        private final int id;
        private final String name;
        private final String timeUtc;
        private final Geometry geometry;
        private final PreparedGeometry preparedGeometry;
        private final String tableName;
        private final String category;

        StormRegion(int id, String name, String timeUtc, Geometry geometry, String tableName, String category) {
            this.id = id;
            this.name = name;
            this.timeUtc = timeUtc;
            this.geometry = geometry;
            this.preparedGeometry = PreparedGeometryFactory.prepare(geometry);
            this.tableName = tableName;
            this.category = category;
        }

        int getId() {
            return id;
        }

        String getName() {
            return name;
        }

        String getTimeUtc() {
            return timeUtc;
        }

        Geometry getGeometry() {
            return geometry;
        }

        PreparedGeometry getPreparedGeometry() {
            return preparedGeometry;
        }

        String getTableName() {
            return tableName;
        }

        String getCategory() {
            return category;
        }
    }
}
