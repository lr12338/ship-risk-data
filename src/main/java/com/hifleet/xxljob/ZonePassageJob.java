package com.hifleet.xxljob;

import com.hifleet.job.ZonePassagePeriod;
import com.hifleet.job.services.ZonePassageAnalysisService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 区域通行分析定时任务（参考 AlertJob 结构）。
 * <p>
 * 按年 / 月 / 日 / 周拆分为 4 个独立 Job，每个 Job 根据当前时间自动计算轨迹查询起止时间。
 * <p>
 * Job 参数仅支持 mmsi：留空=全船，或传 413123456 / mmsi=413123456 / {"mmsi":"413123456"}。
 */
@Component
@Slf4j
public class ZonePassageJob {

    @Autowired
    private ZonePassageAnalysisService zonePassageAnalysisService;

    /**
     * 年任务：查最近 1 年轨迹（now - 1year ~ now）。
     */
    @XxlJob("zonePassageYearJobHandler")
    public ReturnT<String> zonePassageYearJobHandler(String params) {
        return execute(ZonePassagePeriod.YEAR, params);
    }

    /**
     * 月任务：查最近 1 个月轨迹（now - 1month ~ now）。
     */
    @XxlJob("zonePassageMonthJobHandler")
    public ReturnT<String> zonePassageMonthJobHandler(String params) {
        return execute(ZonePassagePeriod.MONTH, params);
    }

    /**
     * 日任务：查最近 1 天轨迹（now - 1day ~ now）。
     */
    @XxlJob("zonePassageDayJobHandler")
    public ReturnT<String> zonePassageDayJobHandler(String params) {
        return execute(ZonePassagePeriod.DAY, params);
    }

    /**
     * 周任务：查最近 1 周轨迹（now - 1week ~ now）。
     */
    @XxlJob("zonePassageWeekJobHandler")
    public ReturnT<String> zonePassageWeekJobHandler(String params) {
        return execute(ZonePassagePeriod.WEEK, params);
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
