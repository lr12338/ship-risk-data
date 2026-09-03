package com.hifleet.sts.service;

import com.hifleet.sts.domain.StsIdentityRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads STS seed identity CSV and provides lookup by IMO or MMSI.
 *
 * Phase 1 — in-memory catalog only. No DB tables.
 */
@Service
public class StsSeedCatalog implements StsSeedLookup {

    private static final Logger log = LoggerFactory.getLogger(StsSeedCatalog.class);

    private final List<StsIdentityRecord> allRecords = new ArrayList<StsIdentityRecord>();
    private final Map<String, List<StsIdentityRecord>> byImo = new LinkedHashMap<String, List<StsIdentityRecord>>();
    private final Map<String, String> mmsiToImo = new HashMap<String, String>();
    private boolean loaded = false;
    private String sourcePath;

    @PostConstruct
    public void init() {
        log.info("StsSeedCatalog ready — load via loadFromFile(path)");
    }

    public synchronized void loadFromFile(String path) {
        if (loaded && path.equals(sourcePath)) {
            log.info("StsSeedCatalog already loaded from {}", path);
            return;
        }
        try {
            FileInputStream in = new FileInputStream(path);
            loadFromStream(in, path);
        } catch (Exception e) {
            log.error("StsSeedCatalog load failed from {}: {}", path, e.getMessage(), e);
            throw new RuntimeException("failed to load STS seed: " + path, e);
        }
    }

    public synchronized void loadFromStream(InputStream in, String sourceLabel) {
        allRecords.clear();
        byImo.clear();
        mmsiToImo.clear();

        int lineNo = 0;
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line = reader.readLine();
            lineNo++;
            if (line == null) {
                reader.close();
                throw new IllegalStateException("empty seed file: " + sourceLabel);
            }
            String[] headers = line.split(",");
            Map<String, Integer> idx = new HashMap<String, Integer>();
            for (int i = 0; i < headers.length; i++) {
                idx.put(headers[i].trim(), i);
            }
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (StringUtils.isBlank(line)) continue;
                String[] cols = line.split(",", -1);
                StsIdentityRecord r = new StsIdentityRecord();
                r.setImo(get(cols, idx, "imo"));
                r.setMmsi(get(cols, idx, "mmsi"));
                r.setFirstSeen(get(cols, idx, "first_seen"));
                r.setLastSeen(get(cols, idx, "last_seen"));
                r.setName(get(cols, idx, "name"));
                r.setCallsign(get(cols, idx, "callsign"));
                r.setLength(parseInt(get(cols, idx, "length")));
                r.setWidth(parseInt(get(cols, idx, "width")));
                r.setType(get(cols, idx, "type"));
                r.setMinotype(get(cols, idx, "minotype"));
                r.setIdentityMode(get(cols, idx, "identity_mode"));

                if (StringUtils.isBlank(r.getImo()) || StringUtils.isBlank(r.getMmsi())) {
                    log.warn("StsSeedCatalog skipping line {}: missing imo/mmsi", lineNo);
                    continue;
                }
                allRecords.add(r);
                if (!byImo.containsKey(r.getImo())) {
                    byImo.put(r.getImo(), new ArrayList<StsIdentityRecord>());
                }
                byImo.get(r.getImo()).add(r);
                mmsiToImo.put(r.getMmsi(), r.getImo());
            }
            reader.close();
            loaded = true;
            sourcePath = sourceLabel;
            log.info("StsSeedCatalog loaded: {} records, {} IMOs, {} MMSIs from {}",
                    allRecords.size(), byImo.size(), mmsiToImo.size(), sourceLabel);
        } catch (Exception e) {
            log.error("StsSeedCatalog load failed at line {} from {}: {}", lineNo, sourceLabel, e.getMessage(), e);
            throw new RuntimeException("failed to load STS seed: " + sourceLabel, e);
        }
    }

    public boolean isLoaded() { return loaded; }
    public int getTotalImos() { return byImo.size(); }
    public int getTotalMmsis() { return mmsiToImo.size(); }

    public List<StsIdentityRecord> getByIdentity(String imo) {
        List<StsIdentityRecord> records = byImo.get(imo);
        return records != null ? records : Collections.<StsIdentityRecord>emptyList();
    }

    public Set<String> getIdentityMmsis(String imo) {
        List<StsIdentityRecord> records = byImo.get(imo);
        if (records == null) return Collections.emptySet();
        Set<String> mmsis = new HashSet<String>();
        for (StsIdentityRecord r : records) {
            mmsis.add(r.getMmsi());
        }
        return mmsis;
    }

    public String getIdentityMode(String imo) {
        List<StsIdentityRecord> records = byImo.get(imo);
        if (records == null || records.isEmpty()) return "UNKNOWN";
        return records.get(0).getIdentityMode();
    }

    public String findImoByMmsi(String mmsi) {
        return mmsiToImo.get(mmsi);
    }

    public List<String> getAllImos() {
        return new ArrayList<String>(byImo.keySet());
    }

    public int getSingleMmsiImoCount() {
        int count = 0;
        for (List<StsIdentityRecord> records : byImo.values()) {
            if (records.size() == 1) count++;
        }
        return count;
    }

    public int getDualMmsiImoCount() {
        int count = 0;
        for (List<StsIdentityRecord> records : byImo.values()) {
            if (records.size() == 2) count++;
        }
        return count;
    }

    private String get(String[] cols, Map<String, Integer> idx, String name) {
        Integer i = idx.get(name);
        if (i == null || i >= cols.length) return "";
        return cols[i].trim();
    }

    private int parseInt(String s) {
        if (StringUtils.isBlank(s)) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
