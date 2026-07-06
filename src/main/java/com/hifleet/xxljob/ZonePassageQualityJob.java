package com.hifleet.xxljob;

import com.hifleet.service.ZonePassageQualityService;
import com.hifleet.service.ZonePassageQualityService.EvaluateResult;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 对已计算完成的区域通行数据做质量评估（独立 Job，与补算 Job 分离）。
 * <p>
 * 数据来源：ship_zone_passage_status + ship_zone_crossing_event
 * 结果写入：ship_zone_passage_quality_report 等 5 张表
 * <p>
 * 默认只评估「全船 DONE、无 FAILED、无进行中」的批次；已评估批次自动跳过。
 * <p>
 * 参数示例：
 * <ul>
 *   <li>留空 — 评估 backfill 范围内所有已完成且未评批次</li>
 *   <li>{@code week=2025-12-17} — 只评该周（按该批次实际 DONE 数判定完整性）</li>
 *   <li>{@code force=true} — 强制重评</li>
 *   <li>{@code replay=false&spatial=false} — 跳过抽样回放/空间抽检（更快）</li>
 * </ul>
 */
@Component
@Slf4j
public class ZonePassageQualityJob {

    @Autowired
    private ZonePassageQualityService zonePassageQualityService;

    @XxlJob("zonePassageCompletedQualityJobHandler")
    public ReturnT<String> zonePassageCompletedQualityJobHandler(String params) {
        return runEvaluate(params);
    }

    /** @deprecated 请使用 {@link #zonePassageCompletedQualityJobHandler} */
    @XxlJob("zonePassageQualityJobHandler")
    public ReturnT<String> zonePassageQualityJobHandler(String params) {
        return runEvaluate(params);
    }

    private ReturnT<String> runEvaluate(String params) {
        try {
            EvaluateResult result = zonePassageQualityService.evaluateCompletedData(params);
            ReturnT<String> ret = result.isSuccess() ? ReturnT.SUCCESS : ReturnT.FAIL;
            ret.setMsg(result.getMessage());
            return ret;
        } catch (Exception ex) {
            log.error("zonePassage completed quality job failed", ex);
            XxlJobLogger.log(ex);
            return new ReturnT<>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }
}
