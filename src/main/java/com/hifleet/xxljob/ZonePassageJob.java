package com.hifleet.xxljob;

import com.hifleet.job.ZonePassagePeriod;
import com.hifleet.job.services.ZonePassageAnalysisService;
import com.hifleet.job.services.ZonePassageAnalysisService.BackfillResult;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 区域通行分析定时任务。
 */
@Component
@Slf4j
public class ZonePassageJob {

    @Autowired
    private ZonePassageAnalysisService zonePassageAnalysisService;

    @XxlJob("zonePassageYearJobHandler")
    public ReturnT<String> zonePassageYearJobHandler(String params) {
        return execute(ZonePassagePeriod.YEAR, params);
    }

    @XxlJob("zonePassageMonthJobHandler")
    public ReturnT<String> zonePassageMonthJobHandler(String params) {
        return execute(ZonePassagePeriod.MONTH, params);
    }

    @XxlJob("zonePassageDayJobHandler")
    public ReturnT<String> zonePassageDayJobHandler(String params) {
        return execute(ZonePassagePeriod.DAY, params);
    }

    @XxlJob("zonePassageWeekJobHandler")
    public ReturnT<String> zonePassageWeekJobHandler(String params) {
        return execute(ZonePassagePeriod.WEEK, params);
    }

    /**
     * 历史补算：2025-01-01～2026-06，单次启动内按周循环（每段 7 天），断点自动续跑。
     * <p>
     * 参数留空即可。从第一个未完成周开始，依次跑完所有周；中途停止后下次从断点周继续。
     */
    @XxlJob("zonePassageHistory2025JobHandler")
    public ReturnT<String> zonePassageHistory2025JobHandler(String params) {
        return runWeeklyBackfill(params);
    }

    private ReturnT<String> runWeeklyBackfill(String params) {
        try {
            BackfillResult result = zonePassageAnalysisService.analysisZonePassageWeeklyBackfill(params);
            ReturnT<String> ret = result.isSuccess() ? ReturnT.SUCCESS : ReturnT.FAIL;
            ret.setMsg(result.getSummary());
            return ret;
        } catch (Exception ex) {
            log.error("zonePassage weekly backfill job failed", ex);
            XxlJobLogger.log(ex);
            return new ReturnT<>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    private ReturnT<String> execute(ZonePassagePeriod period, String params) {
        try {
            boolean result = zonePassageAnalysisService.analysisZonePassageByPeriod(period, params);
            return result ? ReturnT.SUCCESS : ReturnT.FAIL;
        } catch (Exception ex) {
            log.error("zonePassage job failed, period={}", period, ex);
            XxlJobLogger.log(ex);
            return new ReturnT<>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }
}
