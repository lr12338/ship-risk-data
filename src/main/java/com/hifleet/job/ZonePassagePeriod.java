package com.hifleet.job;

/**
 * 区域通行分析任务周期类型。
 */
public enum ZonePassagePeriod {

    /** 年任务：当前时间往前推 1 年 */
    YEAR,
    /** 月任务：当前时间往前推 1 月 */
    MONTH,
    /** 日任务：历史补算截止后按自然日续算（含漏跑检查） */
    DAY,
    /** 周任务：当前时间往前推 1 周 */
    WEEK
}
