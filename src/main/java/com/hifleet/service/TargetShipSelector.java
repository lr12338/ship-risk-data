package com.hifleet.service;

import com.hifleet.domain.TargetShip;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

public final class TargetShipSelector {

    private TargetShipSelector() {
    }

    public static List<TargetShip> select(final String listBatchNo,
                                          List<TargetShip> candidates,
                                          int targetCount) {
        if (listBatchNo == null || listBatchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("listBatchNo must not be blank");
        }
        if (targetCount <= 0) {
            throw new IllegalArgumentException("targetCount must be positive");
        }
        List<TargetShip> valid = new ArrayList<TargetShip>();
        Set<String> seen = new HashSet<String>();
        if (candidates != null) {
            for (TargetShip ship : candidates) {
                if (ship != null && ship.getMmsi() != null
                        && ship.getMmsi().matches("\\d{9}")
                        && seen.add(ship.getMmsi())) {
                    valid.add(ship);
                }
            }
        }
        if (valid.size() < targetCount) {
            throw new IllegalStateException("eligible ship count " + valid.size()
                    + " is below target count " + targetCount);
        }
        Collections.sort(valid, new Comparator<TargetShip>() {
            @Override
            public int compare(TargetShip left, TargetShip right) {
                int hashCompare = Long.compare(hash(listBatchNo, left.getMmsi()),
                        hash(listBatchNo, right.getMmsi()));
                return hashCompare != 0 ? hashCompare
                        : left.getMmsi().compareTo(right.getMmsi());
            }
        });
        List<TargetShip> result = new ArrayList<TargetShip>(valid.subList(0, targetCount));
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setSeqNo(i + 1);
        }
        return result;
    }

    private static long hash(String batchNo, String mmsi) {
        CRC32 crc32 = new CRC32();
        crc32.update((batchNo + mmsi).getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }
}
