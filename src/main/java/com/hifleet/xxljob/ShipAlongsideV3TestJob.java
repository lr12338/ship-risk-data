package com.hifleet.xxljob;

import com.hifleet.job.services.AlongsideV3HistoryTestService;
import com.hifleet.job.services.AlongsideV3HistoryTestService.HistoryTestResult;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ShipAlongsideV3TestJob {

    @Autowired
    private AlongsideV3HistoryTestService historyTestService;

    @XxlJob("shipAlongsideV3TestJobHandler")
    public ReturnT<String> shipAlongsideV3TestJobHandler(String params) {
        try {
            HistoryTestResult result = historyTestService.run(params);
            ReturnT<String> ret = result.isSuccess() ? ReturnT.SUCCESS : ReturnT.FAIL;
            ret.setMsg(result.getSummary());
            return ret;
        } catch (Exception ex) {
            log.error("ship alongside V3 history test job failed", ex);
            XxlJobLogger.log(ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }
}
