package com.hifleet.xxljob;

import com.hifleet.job.services.TargetShipListService;
import com.hifleet.job.services.AlongsideProductionTestService;
import com.hifleet.job.services.LoiteringProductionTestService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ShipBehaviorProductionJobs {

    @Autowired
    private TargetShipListService targetShipListService;

    @Autowired
    private AlongsideProductionTestService alongsideProductionTestService;

    @Autowired
    private LoiteringProductionTestService loiteringProductionTestService;

    @XxlJob("shipBehaviorTargetShipListJobHandler")
    public ReturnT<String> shipBehaviorTargetShipListJobHandler(String params) {
        try {
            return new ReturnT<String>(ReturnT.SUCCESS_CODE,
                    targetShipListService.create(params));
        } catch (Exception ex) {
            log.error("create ship behavior target list failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    @XxlJob("shipAlongsideProductionTestJobHandler")
    public ReturnT<String> shipAlongsideProductionTestJobHandler(String params) {
        try {
            AlongsideProductionTestService.Result result =
                    alongsideProductionTestService.run(params);
            return new ReturnT<String>(result.isSuccess()
                    ? ReturnT.SUCCESS_CODE : ReturnT.FAIL_CODE, result.summary());
        } catch (Exception ex) {
            log.error("ship alongside production test failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    @XxlJob("shipLoiteringProductionTestJobHandler")
    public ReturnT<String> shipLoiteringProductionTestJobHandler(String params) {
        try {
            LoiteringProductionTestService.Result result =
                    loiteringProductionTestService.run(params);
            return new ReturnT<String>(result.isSuccess()
                    ? ReturnT.SUCCESS_CODE : ReturnT.FAIL_CODE, result.summary());
        } catch (Exception ex) {
            log.error("ship loitering production test failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }
}
