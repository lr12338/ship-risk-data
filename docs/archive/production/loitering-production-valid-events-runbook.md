# 徘徊正式事件生产运行手册

> 归档说明：本文档的独立 1k/5k 批次策略已被同批次
> `1,000 → 5,000 → 50,000` 续跑策略替代，不得用于当前调度。当前参数和门禁见
> [0731液货船/集装箱船测试维护手册](../../liquid-container-behavior-test-20260731.md)。

## 运行前检查

- XXL-JOB Handler：`shipLoiteringProductionTestJobHandler`
- 路由策略：FIRST
- 阻塞策略：单机串行
- 超时时间：0
- 调度失败重试：0
- Cron：停用，只允许人工触发
- 不得与搭靠生产任务同时运行
- 保持 `ship.behavior.loitering.production.scene-ready=false`，直到 V3 场景验收完成

应用启动会自动补充 `ship_behavior_loitering_batch_status` 的候选统计字段。
全量前在数据库低峰期检查并人工执行
`sql/ship_behavior_event_production_index.sql` 中的组合索引。

## 1,000 船 V2 正式事件对照

```json
{
  "mode": "production_test",
  "listBatchNo": "merchant-50k-2026h1-v1",
  "expectedShipCount": 50000,
  "shipLimit": 1000,
  "startTime": "2026-01-01 00:00:00",
  "endTime": "2026-07-01 00:00:00",
  "engines": ["V2"],
  "pageSize": 200,
  "parallelism": 1,
  "trajectorySliceHours": 6,
  "eventFilterMode": "VALID_NON_WEAK",
  "storeDetails": false,
  "resume": true,
  "retryCount": 2,
  "logIntervalShips": 50,
  "batchNo": "loiter-prod-2026h1-1k-v2-valid-v1"
}
```

不得复用 `loiter-prod-2026h1-1k-v2-smoke-v1`。历史源未变化时，新批次应等于
旧批次中 `VALID_RISK_EVENT` 且非 `WEAK_LOITERING_CANDIDATE` 的严格子集。

## 5,000 船 V2 门禁

沿用上述参数，只修改：

```json
{
  "shipLimit": 5000,
  "parallelism": 2,
  "batchNo": "loiter-prod-2026h1-5k-v2-valid-v1"
}
```

门禁：FAILED 补跑清零、detail 为 0、NO_DATA 不高于 3%、随机 100 个事件
回溯指纹全部 MATCH，并人工检查高事件船、热点区域和全部 L3。

## 双引擎验证与全量

V3 港区、锚地、泊位、STS 和数据完整性门禁验收后，才允许把
`ship.behavior.loitering.production.scene-ready` 设置为 `true`。

1,000 船双引擎批次：

```json
{
  "mode": "production_test",
  "listBatchNo": "merchant-50k-2026h1-v1",
  "expectedShipCount": 50000,
  "shipLimit": 1000,
  "startTime": "2026-01-01 00:00:00",
  "endTime": "2026-07-01 00:00:00",
  "engines": ["V2", "V3"],
  "pageSize": 200,
  "parallelism": 2,
  "trajectorySliceHours": 6,
  "eventFilterMode": "VALID_NON_WEAK",
  "storeDetails": false,
  "resume": true,
  "retryCount": 2,
  "logIntervalShips": 50,
  "batchNo": "loiter-prod-2026h1-1k-v2v3-valid-v1"
}
```

通过后，全量仅修改：

```json
{
  "shipLimit": 50000,
  "batchNo": "loiter-prod-2026h1-50k-v2v3-valid-v1"
}
```

## 验收 SQL

```sql
SELECT status, result_code, COUNT(*) ships,
       SUM(point_count) points,
       SUM(candidate_count) candidates,
       SUM(weak_count) weak_candidates,
       SUM(downgraded_count) downgraded_candidates,
       SUM(valid_count) valid_candidates,
       SUM(stored_event_count) stored_events,
       ROUND(SUM(elapsed_ms) / 1000, 0) ship_elapsed_seconds
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no = '<batchNo>'
GROUP BY status, result_code;

SELECT COUNT(*) invalid_events
FROM dataanalysis.ship_behavior_event
WHERE batch_no = '<batchNo>'
  AND behavior_type = 'LOITERING'
  AND (exclusion_flag <> 'VALID_RISK_EVENT'
       OR event_sub_type = 'WEAK_LOITERING_CANDIDATE');

SELECT COUNT(*) missing_policy
FROM dataanalysis.ship_behavior_event
WHERE batch_no = '<batchNo>'
  AND behavior_type = 'LOITERING'
  AND score_detail_json NOT LIKE '%"persistPolicy":"VALID_NON_WEAK"%';

SELECT COUNT(*) details
FROM dataanalysis.ship_behavior_event_detail
WHERE batch_no = '<batchNo>'
  AND behavior_type = 'LOITERING';
```
