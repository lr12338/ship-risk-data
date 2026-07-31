package com.hifleet.tankeropensea;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TankerOpenSeaTargetListService {
    private static final int SOURCE_PAGE_SIZE = 5000;

    @Autowired
    private TankerOpenSeaTargetShipRepository repository;

    private final ShipEligibilityPolicy policy = new ShipEligibilityPolicy();

    public String create(String rawParams) {
        JSONObject json = JSON.parseObject(rawParams);
        if (json == null || !"liquid_container_exclusion_list".equals(
                json.getString("mode"))) {
            throw new IllegalArgumentException(
                    "mode must be liquid_container_exclusion_list");
        }
        String listBatchNo = StringUtils.trimToNull(json.getString("listBatchNo"));
        if (listBatchNo == null || !listBatchNo.startsWith("liquid-container-")
                || listBatchNo.length() > 64) {
            throw new IllegalArgumentException("dedicated listBatchNo is required");
        }
        String updatedAfter = StringUtils.trimToNull(json.getString("sourceUpdatedAfter"));
        if (updatedAfter == null) {
            throw new IllegalArgumentException("sourceUpdatedAfter is required");
        }
        int liquidCargoCount = json.getIntValue("liquidCargoCount");
        int containerCount = json.getIntValue("containerCount");
        int targetCount = liquidCargoCount + containerCount;
        if (liquidCargoCount <= 0 || containerCount <= 0
                || targetCount > 100000) {
            throw new IllegalArgumentException(
                    "liquidCargoCount and containerCount must be positive "
                            + "with total <=100000");
        }
        int existing = repository.count(listBatchNo);
        if (existing > 0) {
            repository.validate(listBatchNo, targetCount);
            repository.validateDistribution(
                    listBatchNo, liquidCargoCount, containerCount);
            return "LIQUID_CONTAINER list already exists listBatchNo="
                    + listBatchNo + " count=" + existing;
        }
        List<ListedTanker> liquidCargo = new ArrayList<ListedTanker>();
        List<ListedTanker> containers = new ArrayList<ListedTanker>();
        Set<String> seen = new HashSet<String>();
        int afterMmsi = 0;
        while (liquidCargo.size() < liquidCargoCount
                || containers.size() < containerCount) {
            List<ShipMetadata> page = repository.selectSourceCandidatesPage(
                    updatedAfter, afterMmsi, SOURCE_PAGE_SIZE);
            if (page.isEmpty()) break;
            int nextAfterMmsi = afterMmsi;
            for (ShipMetadata metadata : page) {
                int currentMmsi = parseMmsi(metadata.getMmsi());
                if (currentMmsi <= afterMmsi) {
                    throw new IllegalStateException(
                            "source candidate cursor did not advance mmsi="
                                    + metadata.getMmsi());
                }
                if (currentMmsi < nextAfterMmsi) {
                    throw new IllegalStateException(
                            "source candidate page is not ordered by mmsi");
                }
                nextAfterMmsi = currentMmsi;
                if (!seen.add(metadata.getMmsi())) continue;
                ShipEligibilityDecision decision = policy.evaluate(metadata);
                if (decision.isEligible()) {
                    ListedTanker listed = new ListedTanker(0, metadata, decision);
                    if (decision.getStandardShipType() == StandardShipType.CONTAINER
                            && containers.size() < containerCount) {
                        containers.add(listed);
                    } else if (decision.getStandardShipType()
                            != StandardShipType.CONTAINER
                            && liquidCargo.size() < liquidCargoCount) {
                        liquidCargo.add(listed);
                    }
                    if (liquidCargo.size() >= liquidCargoCount
                            && containers.size() >= containerCount) break;
                }
            }
            if (nextAfterMmsi <= afterMmsi) {
                throw new IllegalStateException(
                        "source candidate page cursor did not advance");
            }
            afterMmsi = nextAfterMmsi;
        }
        if (liquidCargo.size() < liquidCargoCount
                || containers.size() < containerCount) {
            throw new IllegalStateException("eligible source rows are insufficient "
                    + "liquidRequired=" + liquidCargoCount
                    + " liquidActual=" + liquidCargo.size()
                    + " containerRequired=" + containerCount
                    + " containerActual=" + containers.size());
        }
        List<ListedTanker> eligible = interleave(liquidCargo, containers);
        repository.insertImmutable(listBatchNo, eligible);
        repository.validate(listBatchNo, targetCount);
        repository.validateDistribution(
                listBatchNo, liquidCargoCount, containerCount);
        return "LIQUID_CONTAINER list created listBatchNo="
                + listBatchNo + " count=" + targetCount
                + " liquidCargo=" + liquidCargoCount
                + " container=" + containerCount;
    }

    private int parseMmsi(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "source candidate contains invalid MMSI: " + value, ex);
        }
    }

    private List<ListedTanker> interleave(List<ListedTanker> liquidCargo,
                                          List<ListedTanker> containers) {
        List<ListedTanker> result = new ArrayList<ListedTanker>();
        int liquidIndex = 0;
        int containerIndex = 0;
        while (liquidIndex < liquidCargo.size()
                || containerIndex < containers.size()) {
            if (liquidIndex < liquidCargo.size()) {
                ListedTanker value = liquidCargo.get(liquidIndex++);
                result.add(new ListedTanker(result.size() + 1,
                        value.getMetadata(), value.getEligibility()));
            }
            if (containerIndex < containers.size()) {
                ListedTanker value = containers.get(containerIndex++);
                result.add(new ListedTanker(result.size() + 1,
                        value.getMetadata(), value.getEligibility()));
            }
        }
        return result;
    }
}
