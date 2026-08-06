package com.hifleet.xxljob;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.regionbehavior.RegionBehaviorRunResult;
import com.hifleet.regionbehavior.RegionBehaviorRunService;
import com.hifleet.regionbehavior.RegionBehaviorStatus;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RegionBehaviorJobs {
    private final RegionBehaviorRunService runService;

    @Autowired
    public RegionBehaviorJobs(RegionBehaviorRunService runService) {
        this.runService = runService;
    }

    @XxlJob("shipBehaviorRegionPreflightJobHandler")
    public ReturnT<String> preflight(String rawParams) {
        try {
            JSONObject result = runService.preflight(rawParams);
            boolean pass = RegionBehaviorStatus.PASS.name().equals(result.getString("status"));
            return new ReturnT<String>(pass ? ReturnT.SUCCESS_CODE : ReturnT.FAIL_CODE,
                    result.toJSONString());
        } catch (Exception ex) {
            log.error("region behavior preflight failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    @XxlJob("shipBehaviorRegionDryRunJobHandler")
    public ReturnT<String> dryRun(String rawParams) {
        try {
            RegionBehaviorRunResult result = runService.run(rawParams);
            return new ReturnT<String>(result.isSuccess() ? ReturnT.SUCCESS_CODE : ReturnT.FAIL_CODE,
                    result.summaryJson().toJSONString());
        } catch (Exception ex) {
            log.error("region behavior dry-run failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    @XxlJob("shipBehaviorRegionRunJobHandler")
    public ReturnT<String> formalRun(String rawParams) {
        try {
            RegionBehaviorRunResult result = runService.run(rawParams);
            return new ReturnT<String>(result.isSuccess() ? ReturnT.SUCCESS_CODE : ReturnT.FAIL_CODE,
                    result.summaryJson().toJSONString());
        } catch (Exception ex) {
            log.error("region behavior formal run failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }
}
