package com.hifleet.xxljob;

import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.job.services.ShipBehaviorAnalysisService.AnalysisResult;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ShipBehaviorJob {

    @Autowired
    private ShipBehaviorAnalysisService shipBehaviorAnalysisService;

    @XxlJob("shipLoiteringJobHandler")
    public ReturnT<String> shipLoiteringJobHandler(String params) {
        try {
            AnalysisResult result = shipBehaviorAnalysisService.analysisLoitering(params);
            ReturnT<String> ret = result.isSuccess() ? ReturnT.SUCCESS : ReturnT.FAIL;
            ret.setMsg(result.getSummary());
            return ret;
        } catch (Exception ex) {
            log.error("ship loitering job failed", ex);
            XxlJobLogger.log(ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    @XxlJob("shipAlongsideJobHandler")
    public ReturnT<String> shipAlongsideJobHandler(String params) {
        try {
            AnalysisResult result = shipBehaviorAnalysisService.analysisAlongside(params);
            ReturnT<String> ret = result.isSuccess() ? ReturnT.SUCCESS : ReturnT.FAIL;
            ret.setMsg(result.getSummary());
            return ret;
        } catch (Exception ex) {
            log.error("ship alongside job failed", ex);
            XxlJobLogger.log(ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }
}
