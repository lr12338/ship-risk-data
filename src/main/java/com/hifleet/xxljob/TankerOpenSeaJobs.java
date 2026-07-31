package com.hifleet.xxljob;

import com.hifleet.tankeropensea.TankerOpenSeaAlongsideService;
import com.hifleet.tankeropensea.TankerOpenSeaBatchResult;
import com.hifleet.tankeropensea.TankerOpenSeaLoiteringService;
import com.hifleet.tankeropensea.TankerOpenSeaJobParams;
import com.hifleet.tankeropensea.TankerOpenSeaRangeResult;
import com.hifleet.tankeropensea.TankerOpenSeaRangeService;
import com.hifleet.tankeropensea.TankerOpenSeaTargetListService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TankerOpenSeaJobs {
    @Autowired
    private TankerOpenSeaTargetListService targetListService;
    @Autowired
    private TankerOpenSeaAlongsideService alongsideService;
    @Autowired
    private TankerOpenSeaLoiteringService loiteringService;
    @Autowired
    private TankerOpenSeaRangeService rangeService;

    @XxlJob("shipLiquidContainerTargetListTestJobHandler")
    public ReturnT<String> targetList(String params) {
        try {
            return new ReturnT<String>(ReturnT.SUCCESS_CODE,
                    targetListService.create(params));
        } catch (Exception ex) {
            log.error("LIQUID_CONTAINER target list failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    @XxlJob("shipLiquidContainerAlongsideTestJobHandler")
    public ReturnT<String> alongside(String params) {
        try {
            TankerOpenSeaBatchResult result = alongsideService.run(params);
            return new ReturnT<String>(result.isSuccess()
                    ? ReturnT.SUCCESS_CODE : ReturnT.FAIL_CODE, result.summary());
        } catch (Exception ex) {
            log.error("LIQUID_CONTAINER ALONGSIDE failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    @XxlJob("shipLiquidContainerLoiteringTestJobHandler")
    public ReturnT<String> loitering(String params) {
        try {
            TankerOpenSeaBatchResult result = loiteringService.run(params);
            return new ReturnT<String>(result.isSuccess()
                    ? ReturnT.SUCCESS_CODE : ReturnT.FAIL_CODE, result.summary());
        } catch (Exception ex) {
            log.error("LIQUID_CONTAINER LOITERING failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    @XxlJob("shipLiquidContainerAlongsideRangeJobHandler")
    public ReturnT<String> alongsideRange(String params) {
        return range(params, TankerOpenSeaJobParams.Behavior.ALONGSIDE);
    }

    @XxlJob("shipLiquidContainerLoiteringRangeJobHandler")
    public ReturnT<String> loiteringRange(String params) {
        return range(params, TankerOpenSeaJobParams.Behavior.LOITERING);
    }

    private ReturnT<String> range(
            String params, TankerOpenSeaJobParams.Behavior behavior) {
        try {
            TankerOpenSeaRangeResult result = rangeService.run(params, behavior);
            return new ReturnT<String>(result.isSuccess()
                    ? ReturnT.SUCCESS_CODE : ReturnT.FAIL_CODE, result.summary());
        } catch (Exception ex) {
            log.error("LIQUID_CONTAINER " + behavior + " range failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }
}
