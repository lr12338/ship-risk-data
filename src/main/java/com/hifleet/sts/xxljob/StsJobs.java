package com.hifleet.sts.xxljob;

import com.hifleet.sts.domain.StsJobParams;
import com.hifleet.sts.service.StsBaselineService;
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
}
