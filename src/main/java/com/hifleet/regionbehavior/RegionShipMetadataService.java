package com.hifleet.regionbehavior;

import com.hifleet.service.ShipTypeClassifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RegionShipMetadataService {
    private final JdbcTemplate jdbcTemplate;
    private final ShipTypeClassifier shipTypeClassifier;
    private final RegionBehaviorConfig config;

    @Autowired
    public RegionShipMetadataService(@Qualifier("aid41JdbcTemplate") JdbcTemplate jdbcTemplate,
                                     ShipTypeClassifier shipTypeClassifier,
                                     RegionBehaviorConfig config) {
        this.jdbcTemplate = jdbcTemplate;
        this.shipTypeClassifier = shipTypeClassifier;
        this.config = config;
    }

    public RegionShipMetadataSummary filter(Map<String, RegionShipCandidate> candidates) {
        RegionShipMetadataSummary summary = new RegionShipMetadataSummary();
        List<String> validMmsis = new ArrayList<String>();
        for (RegionShipCandidate candidate : candidates.values()) {
            summary.discovered();
            if (candidate.getMmsi() != null && candidate.getMmsi().matches("[1-9][0-9]{8}")) {
                validMmsis.add(candidate.getMmsi());
                summary.validMmsi();
            } else {
                candidate.setMetadata(null, false, false, "INVALID_MMSI");
            }
        }
        Map<String, ShipMetadata> metadata = new HashMap<String, ShipMetadata>();
        int batchSize = Math.max(1, config.getMetadataBatchSize());
        for (int start = 0; start < validMmsis.size(); start += batchSize) {
            int end = Math.min(validMmsis.size(), start + batchSize);
            try {
                for (ShipMetadata row : queryBatch(validMmsis.subList(start, end))) {
                    metadata.put(row.getMmsi(), row);
                }
            } catch (RuntimeException ex) {
                summary.metadataQueryFailure();
            }
        }
        for (String mmsi : validMmsis) {
            RegionShipCandidate candidate = candidates.get(mmsi);
            ShipMetadata row = metadata.get(mmsi);
            if (row == null || row.getShipType() == null || row.getShipType().trim().isEmpty()) {
                candidate.setMetadata(row == null ? null : row.getShipType(), row != null,
                        false, "UNKNOWN_SHIP_TYPE");
                if (row != null) {
                    summary.metadataFound();
                }
                summary.unknown();
                continue;
            }
            summary.metadataFound();
            boolean eligible = row.getLengthMeters() != null && row.getLengthMeters() > 70.0
                    && Boolean.TRUE.equals(shipTypeClassifier.isMerchant(row.getShipType()));
            candidate.setMetadata(row.getShipType(), true, eligible,
                    eligible ? "ELIGIBLE" : "EXCLUDED_BY_TYPE");
            if (eligible) {
                summary.eligible();
            } else {
                summary.excluded();
            }
        }
        return summary;
    }

    protected List<ShipMetadata> queryBatch(List<String> mmsis) {
        if (mmsis.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < mmsis.size(); i++) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
        }
        String sql = "SELECT CAST(mmsi AS CHAR) mmsi, `type`, length "
                + "FROM ais41.recentships WHERE mmsi IN (" + placeholders + ")";
        return jdbcTemplate.query(sql, mmsis.toArray(), (rs, rowNum) -> {
            double length = rs.getDouble("length");
            return new ShipMetadata(rs.getString("mmsi"), rs.getString("type"),
                    rs.wasNull() ? null : length);
        });
    }

    public static final class ShipMetadata {
        private final String mmsi;
        private final String shipType;
        private final Double lengthMeters;

        public ShipMetadata(String mmsi, String shipType, Double lengthMeters) {
            this.mmsi = mmsi;
            this.shipType = shipType;
            this.lengthMeters = lengthMeters;
        }
        public String getMmsi() { return mmsi; }
        public String getShipType() { return shipType; }
        public Double getLengthMeters() { return lengthMeters; }
    }
}
