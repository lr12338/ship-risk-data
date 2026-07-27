package com.hifleet.xxljob;

import com.hifleet.job.services.ShipLoiteringV3BatchService;
import com.hifleet.job.services.ShipLoiteringV3BatchService.BatchResult;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ShipLoiteringV3BatchJob {

    @Autowired
    private ShipLoiteringV3BatchService batchService;

    @XxlJob("shipLoiteringV3BatchJobHandler")
    public ReturnT<String> shipLoiteringV3BatchJobHandler(String params) {
        try {
            BatchResult result = batchService.execute(params);
            ReturnT<String> response = result.isSuccess()
                    ? ReturnT.SUCCESS : ReturnT.FAIL;
            response.setMsg(result.getSummary());
            return response;
        } catch (Exception ex) {
            log.error("Loitering V3 batch job failed", ex);
            XxlJobLogger.log(ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }
}
