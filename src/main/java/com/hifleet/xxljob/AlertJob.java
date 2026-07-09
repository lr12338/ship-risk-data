package com.hifleet.xxljob;

import com.hifleet.job.services.AisAnalysisService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AlertJob {

    @Autowired
    AisAnalysisService aisAnalysisService;

    /******/
    @XxlJob(value = "AisTrajectoryAnalysis")
    public ReturnT<String> AisTrajectoryAnalysis(String params) {
        try {
            boolean result= aisAnalysisService.analysisShipRecent(params);
            if(result) {
                return ReturnT.SUCCESS;
            }else{
                return ReturnT.FAIL;
            }
        }catch (Exception ex) {
            XxlJobLogger.log(ex);
        }
        return ReturnT.FAIL;
    }
    /******/
    @XxlJob(value = "analysisAbbormalShip")
    public ReturnT<String> analysisAbbormalShip(String params) {
        try {
            boolean result= aisAnalysisService.analysisAbbormalShip();
            if(result) {
                return ReturnT.SUCCESS;
            }else{
                return ReturnT.FAIL;
            }
        }catch (Exception ex) {
            XxlJobLogger.log(ex);
        }
        return ReturnT.FAIL;
    }
}
