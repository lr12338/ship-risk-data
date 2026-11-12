package com.hifleet.sts.service;

import com.hifleet.sts.domain.StsIdentityRecord;

import java.util.List;
import java.util.Set;

public interface StsSeedLookup {
    List<StsIdentityRecord> getByIdentity(String imo);
    Set<String> getIdentityMmsis(String imo);
    String getIdentityMode(String imo);
    String findImoByMmsi(String mmsi);
}
