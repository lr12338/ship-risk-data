package com.hifleet.sts.xxljob;

import com.hifleet.sts.domain.StsJobParams;
import com.hifleet.sts.service.StsBaselineService;
import com.hifleet.sts.service.StsRegionShadowService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * STS Phase 1 baseline job handler.
 *
 * Parameters (JSON):
 * {
 *   "mode": "STS_PHASE1_BASELINE",
 *   "seedFile": "/data/zhengyi/sts/sts-crude-identity-20260902.csv",
 *   "from": "2026-08-01 00:00:00",
 *   "to": "2026-08-08 00:00:00",
 *   "shipLimit": 20,
 *   "windowHours": 24,
 *   "activityMinSpeedKn": 0.0,
 *   "activityMaxSpeedKn": 4.0,
 *   "parallelism": 1,
 *   "writeMode": "LOG_ONLY"
 * }
 *
 * Safety constraints (enforced by StsBaselineService):
 *   - mode must be STS_PHASE1_BASELINE
 *   - parallelism must be 1
 *   - writeMode must be LOG_ONLY
 *   - time span must be <= 7 days
 *   - from and to are required (no auto-from-seed)
 */
@Component
public class StsJobs {

    private static final Logger log = LoggerFactory.getLogger(StsJobs.class);

    @Autowired
    private StsBaselineService stsBaselineService;

    @Autowired
    private StsRegionShadowService stsRegionShadowService;

    @XxlJob("shipStsHistoryBaselineJobHandler")
    public ReturnT<String> shipStsHistoryBaselineJobHandler(String params) {
        try {
            StsJobParams jobParams = StsJobParams.parse(params);
            StsBaselineService.StsRunResult result = stsBaselineService.runBaseline(jobParams);
            ReturnT<String> ret = result.isSuccess() ? ReturnT.SUCCESS : ReturnT.FAIL;
            ret.setMsg(result.getSummary());
            XxlJobLogger.log(result.getSummary());
            return ret;
        } catch (Exception ex) {
            log.error("shipStsHistoryBaselineJobHandler failed", ex);
            XxlJobLogger.log(ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    /**
     * STS Phase 1.2 — Region Shadow Baseline Job.
     *
     * Applies analysis region gate before replay, attributes events to
     * core region, and persists candidates to the experimental shadow
     * table. Writes ONLY to dataanalysis.ship_sts_shadow_event.
     *
     * Parameters (JSON):
     * {
     *   "mode": "STS_PHASE1_REGION_SHADOW",
     *   "seedFile": "/data/zhengyi/sts/sts-crude-identity-20260902.csv",
     *   "imos": ["9000001"],
     *   "from": "2026-08-01 00:00:00",
     *   "to": "2026-08-02 00:00:00",
     *   "windowHours": 24,
     *   "activityMinSpeedKn": 0.0,
     *   "activityMaxSpeedKn": 4.0,
     *   "regionId": "PERSIAN_GULF_TEST_V1",
     *   "regionVersion": "20260902-v1",
     *   "regionWkt": "POLYGON((47.5 23.5,56.5 23.5,56.5 30.5,47.5 30.5,47.5 23.5))",
     *   "analysisBufferNm": 10.0,
     *   "batchNo": "sts-pg-test-20260902-01",
     *   "parallelism": 1,
     *   "writeMode": "SHADOW_ONLY"
     * }
     *
     * Safety constraints (enforced by StsRegionShadowService):
     *   - mode must be STS_PHASE1_REGION_SHADOW
     *   - imos must be explicitly provided (no fleet-wide)
     *   - parallelism must be 1
     *   - writeMode must be SHADOW_ONLY
     *   - time span must be <= 7 days
     *   - regionId / regionVersion / regionWkt / batchNo required
     *   - analysisBufferNm must be 0–30 nm
     */
    @XxlJob("shipStsHistoryRegionShadowJobHandler")
    public ReturnT<String> shipStsHistoryRegionShadowJobHandler(String params) {
        try {
            StsJobParams jobParams = StsJobParams.parse(params);
            StsRegionShadowService.RegionShadowRunResult result = stsRegionShadowService.runRegionShadow(jobParams);
            ReturnT<String> ret = result.isSuccess() ? ReturnT.SUCCESS : ReturnT.FAIL;
            ret.setMsg(result.getSummary());
            XxlJobLogger.log(result.getSummary());
            return ret;
        } catch (Exception ex) {
            log.error("shipStsHistoryRegionShadowJobHandler failed", ex);
            XxlJobLogger.log(ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }
}
