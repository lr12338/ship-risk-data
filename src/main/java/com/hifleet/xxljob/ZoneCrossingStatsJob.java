package com.hifleet.xxljob;

import com.hifleet.service.ZoneCrossingStatsService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 船舶穿过区域统计 XXL-Job 入口。
 */
@Component
public class ZoneCrossingStatsJob {

    @Autowired
    private ZoneCrossingStatsService zoneCrossingStatsService;

    @XxlJob("zoneCrossingStatsJobHandler")
    public ReturnT<String> zoneCrossingStatsJobHandler(String params) {
        try {
            zoneCrossingStatsService.calculateZoneCrossingStats(params);
            return ReturnT.SUCCESS;
        } catch (Exception ex) {
            XxlJobLogger.log(ex);
            return new ReturnT<>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }
}
