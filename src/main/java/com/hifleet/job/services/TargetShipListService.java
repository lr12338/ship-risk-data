package com.hifleet.job.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.TargetShip;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.repository.TargetShipRepository;
import com.hifleet.service.TargetShipSelector;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TargetShipListService {

    @Autowired
    private AIS41GeneralQueryRepository sourceRepository;

    @Autowired
    private TargetShipRepository targetRepository;

    public String create(String rawParams) {
        JSONObject json = JSON.parseObject(rawParams);
        String listBatchNo = json == null ? null
                : StringUtils.trimToNull(json.getString("listBatchNo"));
        if (listBatchNo == null || listBatchNo.length() > 64) {
            throw new IllegalArgumentException("valid listBatchNo is required");
        }
        String sourceMode = StringUtils.defaultIfBlank(json.getString("sourceMode"), "AIS41_RECENT_SHIPS");
        if ("EXISTING_5000_TABLE".equals(sourceMode)) {
            String sourceListBatchNo = StringUtils.trimToNull(json.getString("sourceListBatchNo"));
            int targetCount = json.containsKey("targetCount") ? json.getIntValue("targetCount") : 5000;
            targetRepository.copyExisting5000Immutable(sourceListBatchNo, listBatchNo, targetCount);
            return "approved target list ready listBatchNo=" + listBatchNo + " count=" + targetCount;
        }
        if (!"AIS41_RECENT_SHIPS".equals(sourceMode)) {
            throw new IllegalArgumentException("unsupported sourceMode");
        }
        String updatedAfter = StringUtils.defaultIfBlank(
                json.getString("sourceUpdatedAfter"), "2026-07-01 00:00:00");
        int targetCount = json.containsKey("targetCount")
                ? json.getIntValue("targetCount") : 50000;
        if (targetCount <= 0) {
            throw new IllegalArgumentException("targetCount must be positive");
        }
        int existing = targetRepository.countAll(listBatchNo);
        if (existing > 0) {
            targetRepository.validate(listBatchNo, targetCount);
            return "target list already ready listBatchNo=" + listBatchNo
                    + " count=" + targetCount;
        }
        List<TargetShip> selected = TargetShipSelector.select(listBatchNo,
                sourceRepository.selectEligibleTargetShips(updatedAfter), targetCount);
        targetRepository.insertImmutable(listBatchNo, selected);
        targetRepository.validate(listBatchNo, targetCount);
        return "target list created listBatchNo=" + listBatchNo
                + " count=" + targetCount;
    }
}
