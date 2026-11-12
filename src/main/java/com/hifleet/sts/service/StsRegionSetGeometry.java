package com.hifleet.sts.service;

import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.regionbehavior.RegionGeometry;
import com.hifleet.sts.domain.StsRiskRegionRow;
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StsRegionSetGeometry {

    public static class ResolvedRegion {
        public final String regionCode;
        public final String regionVersion;
        public final RegionGeometry geometry;
        public final int priority;
        public final double coreArea;

        public ResolvedRegion(String regionCode, String regionVersion,
                              RegionGeometry geometry, int priority, double coreArea) {
            this.regionCode = regionCode;
            this.regionVersion = regionVersion;
            this.geometry = geometry;
            this.priority = priority;
            this.coreArea = coreArea;
        }
    }

    private final List<ResolvedRegion> regions;
    private final String regionSetCode;

    public StsRegionSetGeometry(String regionSetCode, List<StsRiskRegionRow> rows) {
        this.regionSetCode = regionSetCode;
        this.regions = new ArrayList<ResolvedRegion>();
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        for (StsRiskRegionRow row : rows) {
            RegionGeometry geom = RegionGeometry.parse(
                    row.getGeometryWkt(), row.getAnalysisBufferNm(), config);
            double area = geom.getCoreRegion() != null
                    ? geom.getCoreRegion().getArea()
                    : 0.0;
            regions.add(new ResolvedRegion(
                    row.getRegionCode(), row.getRegionVersion(),
                    geom, row.getPriority(), area));
        }
    }

    public String getRegionSetCode() { return regionSetCode; }
    public List<ResolvedRegion> getRegions() { return Collections.unmodifiableList(regions); }
    public int size() { return regions.size(); }

    public boolean analysisContainsAny(Coordinate pt) {
        for (ResolvedRegion r : regions) {
            if (r.geometry.containsAnalysis(pt)) return true;
        }
        return false;
    }

    public AttributionResult attributePrimary(Coordinate pt) {
        List<ResolvedRegion> matches = new ArrayList<ResolvedRegion>();
        for (ResolvedRegion r : regions) {
            if (r.geometry.containsCore(pt)) {
                matches.add(r);
            }
        }
        if (matches.isEmpty()) {
            return new AttributionResult(null, matches);
        }
        if (matches.size() == 1) {
            return new AttributionResult(matches.get(0), matches);
        }
        Collections.sort(matches, new Comparator<ResolvedRegion>() {
            @Override
            public int compare(ResolvedRegion a, ResolvedRegion b) {
                int p = Integer.compare(a.priority, b.priority);
                if (p != 0) return p;
                int ar = Double.compare(a.coreArea, b.coreArea);
                if (ar != 0) return ar;
                return a.regionCode.compareTo(b.regionCode);
            }
        });
        return new AttributionResult(matches.get(0), matches);
    }

    public static class AttributionResult {
        public final ResolvedRegion primary;
        public final List<ResolvedRegion> allMatches;

        public AttributionResult(ResolvedRegion primary, List<ResolvedRegion> allMatches) {
            this.primary = primary;
            this.allMatches = allMatches;
        }

        public boolean hasMatch() { return primary != null; }
        public int matchCount() { return allMatches.size(); }
    }
}
