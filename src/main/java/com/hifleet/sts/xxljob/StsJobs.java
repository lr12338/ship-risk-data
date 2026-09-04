package com.hifleet.sts.xxljob;

import com.hifleet.sts.domain.StsFleetBackfillJobParams;
import com.hifleet.sts.domain.StsJobParams;
import com.hifleet.sts.domain.StsRegionSetJobParams;
import com.hifleet.sts.service.StsBaselineService;
import com.hifleet.sts.service.StsFleetBackfillService;
import com.hifleet.sts.service.StsRegionShadowService;
import com.hifleet.sts.service.StsRegionSetShadowService;
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

    @Autowired
    private StsRegionSetShadowService stsRegionSetShadowService;

    @Autowired
    private StsFleetBackfillService stsFleetBackfillService;

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
     * STS Phase 1.3 — Region Set Shadow Batch Job.
     *
     * Uses DB-backed seed identity + DB-backed region sets for batch runs.
     * mode/writeMode/parallelism are hardcoded to safe defaults.
     *
     * Parameters (JSON):
     * {
     *   "seedBatchNo": "sts-crude-identity-20260902-v1",
     *   "expectedShipCount": 786,
     *   "shipOffset": 0,
     *   "shipLimit": 100,
     *   "regionSetCode": "GLOBAL_STS_TEST_REGION_SET_V1",
     *   "from": "2026-08-01 00:00:00",
     *   "to": "2026-08-08 00:00:00",
     *   "batchNo": "sts-global-20260801-001"
     * }
     *
     * Safety constraints:
     *   - expectedShipCount must match DB count (fail closed on mismatch)
     *   - shipLimit must be 1-100 (no fleet-wide runs)
     *   - time span <= 7 days
     *   - writeMode hardcoded to SHADOW_ONLY
     *   - parallelism hardcoded to 1
     */
    @XxlJob("shipStsHistoryRegionSetShadowJobHandler")
    public ReturnT<String> shipStsHistoryRegionSetShadowJobHandler(String params) {
        try {
            StsRegionSetJobParams jobParams = StsRegionSetJobParams.parse(params);
            StsRegionSetShadowService.RegionSetShadowRunResult result =
                    stsRegionSetShadowService.runRegionSetShadow(jobParams);
            ReturnT<String> ret = result.isSuccess() ? ReturnT.SUCCESS : ReturnT.FAIL;
            ret.setMsg(result.getSummary());
            XxlJobLogger.log(result.getSummary());
            return ret;
        } catch (Exception ex) {
            log.error("shipStsHistoryRegionSetShadowJobHandler failed", ex);
            XxlJobLogger.log(ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    /**
     * STS Phase 1.4 — Fleet Historical Backfill Job.
     *
     * Uses ship_behavior_target_ship as fleet source, DB-backed region sets,
     * multi-year time range auto-split into <=7-day tasks, region-first
     * preselection, and resumable batch tracking.
     *
     * Each XXL invocation executes exactly ONE slice task.
     * Writes ONLY to dataanalysis.ship_sts_shadow_event + backfill batch/task tables.
     *
     * Parameters (JSON, single line):
     * {
     *   "targetListBatchNo": "merchant-50k-2026h1-v1",
     *   "expectedTargetCount": 44760,
     *   "shipOffset": 0,
     *   "shipCount": 100,
     *   "regionSetCode": "STS_HOTSPOT_HIGH_YIELD_V2",
     *   "from": "2024-01-01 00:00:00",
     *   "to": "2024-02-01 00:00:00",
     *   "sliceDays": 7,
     *   "batchNo": "sts-backfill-test-001"
     * }
     *
     * Safety constraints:
     *   - shipCount max 5000 per job
     *   - sliceDays 1-7
     *   - max parent span 5 years / 261 slices
     *   - expectedTargetCount must match DB physical ship count
     *   - target list / region set fingerprint change = fail closed on resume
     *   - shadow only, no production event writes
     */
    @XxlJob("shipStsHistoricalFleetBackfillJobHandler")
    public ReturnT<String> shipStsHistoricalFleetBackfillJobHandler(String params) {
        try {
            StsFleetBackfillJobParams jobParams = StsFleetBackfillJobParams.parse(params);
            StsFleetBackfillService.BackfillRunResult result =
                    stsFleetBackfillService.runOneInvocation(jobParams);
            ReturnT<String> ret = result.success ? ReturnT.SUCCESS : ReturnT.FAIL;
            ret.setMsg(result.getSummary());
            XxlJobLogger.log(result.getSummary());
            return ret;
        } catch (Exception ex) {
            log.error("shipStsHistoricalFleetBackfillJobHandler failed", ex);
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
