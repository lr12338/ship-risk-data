package com.hifleet.sts.service;

import com.hifleet.sts.domain.StsFleetShipIdentity;
import com.hifleet.sts.domain.StsIdentityRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seed catalog backed by StsFleetShipIdentity list from target ship table.
 *
 * Identity key = physicalShipKey (IMO:xxx for IMO-based, MMSI:xxx for MMSI-only).
 * This ensures every physical ship has a unique identity key.
 *
 * Implements StsSeedLookup for reuse with StsBaselineService.
 */
public class StsBackfillFleetCatalog implements StsSeedLookup {

    private final Map<String, List<StsIdentityRecord>> byKey =
            new LinkedHashMap<String, List<StsIdentityRecord>>();
    private final Map<String, String> mmsiToKey = new HashMap<String, String>();
    private final Map<String, String> keyToImo = new HashMap<String, String>();

    public StsBackfillFleetCatalog(List<StsFleetShipIdentity> identities) {
        for (StsFleetShipIdentity identity : identities) {
            String key = identity.getPhysicalShipKey();
            List<StsIdentityRecord> records = new ArrayList<StsIdentityRecord>();
            for (String mmsi : identity.getMmsis()) {
                StsIdentityRecord rec = new StsIdentityRecord();
                rec.setImo(identity.getImo());
                rec.setMmsi(mmsi);
                rec.setName(identity.getShipName());
                rec.setType(identity.getShipType());
                rec.setIdentityMode(identity.getIdentitySource());
                records.add(rec);
                mmsiToKey.put(mmsi, key);
            }
            byKey.put(key, records);
            keyToImo.put(key, identity.getImo());
        }
    }

    @Override
    public List<StsIdentityRecord> getByIdentity(String identityKey) {
        List<StsIdentityRecord> list = byKey.get(identityKey);
        return list != null ? list : Collections.<StsIdentityRecord>emptyList();
    }

    @Override
    public Set<String> getIdentityMmsis(String identityKey) {
        List<StsIdentityRecord> records = byKey.get(identityKey);
        if (records == null) return Collections.emptySet();
        java.util.Set<String> s = new java.util.HashSet<String>();
        for (StsIdentityRecord r : records) s.add(r.getMmsi());
        return s;
    }

    @Override
    public String getIdentityMode(String identityKey) {
        List<StsIdentityRecord> records = byKey.get(identityKey);
        if (records == null || records.isEmpty()) return "UNKNOWN";
        return records.get(0).getIdentityMode();
    }

    @Override
    public String findImoByMmsi(String mmsi) {
        return mmsiToKey.get(mmsi);
    }

    public String getActualImo(String identityKey) {
        return keyToImo.get(identityKey);
    }

    public int getTotalShips() { return byKey.size(); }

    public java.util.Set<String> getAllKeys() {
        return Collections.unmodifiableSet(byKey.keySet());
    }
}
