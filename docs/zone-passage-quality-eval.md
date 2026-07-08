# 区域通行数据质量评估 Job 说明

## 1. 概述

`zonePassageCompletedQualityJobHandler` 是对 **已计算完成** 的区域通行数据做质量评估的独立 XXL-Job，与补算 Job `zonePassageHistory2025JobHandler` **完全分离**，可并行运行。

| 项 | 说明 |
|---|---|
| JobHandler | `zonePassageCompletedQualityJobHandler`（兼容旧名 `zonePassageQualityJobHandler`） |
| 实现类 | `com.hifleet.xxljob.ZonePassageQualityJob` |
| 核心服务 | `com.hifleet.service.ZonePassageQualityService` |
| 建表 SQL | `src/main/resources/sql/ship_zone_passage_quality_report.sql` |

### 1.1 评估原则

- **只读**源数据，不重算全量轨迹
- **不修改** `ship_zone_crossing_event`
- 结果写入 5 张评估表
- 已评估批次默认跳过（`force=true` 可强制重评）

### 1.2 数据来源（补算 Job 产出）

| 表 | 作用 |
|---|---|
| `dataanalysis.ship_zone_passage_status` | 计算进度：每船 DONE/FAILED + `event_count` |
| `dataanalysis.ship_zone_crossing_event` | 各区域进出事件：ENTER/EXIT + 时间 + 坐标 |

### 1.3 覆盖的区域类型（13 种）

| event_category | 含义 | 数据来源 |
|---|---|---|
| STRAIT | 海峡 | MySQL alertzones |
| SEA_AREA | 海区 | MySQL world_sea |
| BRIDGE | 桥区 | MySQL alertzones |
| WIND_FARM | 风电区 | MySQL alertzones |
| STORM_WAVE | 大浪区 | PostgreSQL regions_wave |
| STORM_WIND | 大风区 | PostgreSQL regions_wind |
| HIGH_RISK | 高风险警示区 | MySQL alertzones |
| ICE | 冰区 | MySQL alertzones |
| MARINE_PROTECTED | 海洋保护区 | MySQL alertzones |
| AQUACULTURE | 养殖区 | MySQL alertzones |
| EMISSION_CONTROL | 排放控制区 | MySQL alertzones |
| MARPOL | MARPOL 区域 | MySQL alertzones |
| JWC | JWC 区域 | MySQL alertzones |

---

## 2. 配置项

`application.properties` / `deploy/application.properties`：

```properties
# 补算日期范围（评估 Job 默认沿用）
zone.passage.backfill.from-date=2025-01-01
zone.passage.backfill.to-date=2026-07-01

# 质量评估（独立 Job）
zone.passage.quality.samples-per-stratum=2      # 每个分层抽取 2 艘
zone.passage.quality.max-replay-ships=60        # 检查9 最多 60 艘
zone.passage.quality.max-spatial-ships=30       # 检查10 最多 30 艘
zone.passage.quality.max-spatial-events=60       # 检查10 最多 60 条 event
```

---

## 3. Job 参数

| 参数 | 说明 | 示例 |
|---|---|---|
| 留空 | 评估 backfill 范围内所有 **已完成且未评** 的批次 | — |
| `week=YYYY-MM-DD` | 只评指定周（周一日期） | `week=2025-12-17` |
| `fromDate` / `toDate` | 限定评估日期范围 | `fromDate=2025-01-01&toDate=2025-07-01` |
| `force=true` | 删除旧报告，强制重评 | `week=2025-12-17&force=true` |
| `replay=false` | 关闭抽样轨迹回放（更快） | `replay=false&spatial=false` |
| `spatial=false` | 关闭坐标空间抽检 | 同上 |

JSON 参数示例：

```json
{
  "week": "2025-12-17",
  "force": false,
  "replay": true,
  "spatial": true
}
```

---

## 4. 批次筛选逻辑（评估前）

Job 从 `ship_zone_passage_status` 按 `(batch_month, start_time, end_time)` 分组找候选批次，再逐条过滤：

```
┌─────────────────────────────────────────────────────────┐
│  候选批次（status 表有 DONE 记录的周）                    │
└───────────────────────────┬─────────────────────────────┘
                            ▼
              ┌─────────────────────────────┐
              │ 评估报告表已有记录？         │
              │ （force=true 则删旧重评）    │
              └─────────────┬───────────────┘
                     是 ↓ 跳过
                     否 ↓
              ┌─────────────────────────────┐
              │ 该周有 FAILED 船舶？          │
              └─────────────┬───────────────┘
                     是 ↓ 跳过
                     否 ↓
              ┌─────────────────────────────┐
              │ 批量模式：DONE >= 当前全船数？ │
              │ week= 模式：跳过此检查        │
              └─────────────┬───────────────┘
                     否 ↓ 跳过（进行中）
                     是 ↓
              ┌─────────────────────────────┐
              │  执行 10 项质量检查           │
              └─────────────────────────────┘
```

### 4.1 「已完成」判定

| 模式 | 条件 |
|---|---|
| **批量（参数留空）** | `FAILED = 0` 且 `DONE 数 >= 当前 recentships 总数` |
| **指定 week** | `FAILED = 0`，`expected = 该周 status 表 DONE + FAILED` |

### 4.2 典型场景

| 场景 | Job 行为 |
|---|---|
| `2025-12-24-W` 算到 200/116308 | 批量模式 **跳过**（未完成） |
| `2025-12-24-W` 116308 全部 DONE | 批量模式 **纳入评估** |
| `2025-12-17-W` 72920 艘时算完，现全船 116308 | 批量模式 **跳过**；用 `week=2025-12-17` 单独评 |
| 已评过的周 | **跳过**（除非 `force=true`） |

---

## 5. 十项质量检查

每个批次按时间范围 `[start_time, end_time)` 执行以下检查：

### 检查 1：任务完整性 `TASK_COMPLETENESS`

- **查什么**：status 表 DONE/FAILED 数、完成率
- **公式**：`完成率 = DONE / expected × 100%`
- **ERROR 条件**：`FAILED > 0` 或 `DONE < expected`
- **issue 类型**：`SHIP_FAILED`（逐条记录失败 mmsi）

### 检查 2：事件数量对账 `EVENT_COUNT_RECONCILE`

- **查什么**：每条 DONE 船的 `status.event_count` vs event 表实际条数
- **ERROR 条件**：两者不一致
- **issue 类型**：`EVENT_COUNT_MISMATCH`

```sql
-- 逻辑示意
SELECT s.mmsi, s.event_count, COUNT(e.id)
FROM ship_zone_passage_status s
LEFT JOIN ship_zone_crossing_event e
  ON e.mmsi = s.mmsi AND e.event_time >= s.start_time AND e.event_time < s.end_time
WHERE s.status = 'DONE' AND s.batch_month = ?
GROUP BY s.mmsi, s.event_count
HAVING s.event_count <> COUNT(e.id);
```

### 检查 3：事件时间范围 `EVENT_TIME_RANGE`

- **查什么**：该批次 DONE 船舶的事件中，是否存在时间落在批次 `[start_time, end_time)` 之外的数据
- **SQL 逻辑**：`status` 表 JOIN `event` 表，仅统计本批次 DONE 船且 `event_time` 超出该周窗口的记录
- **ERROR 条件**：存在此类记录
- **issue 类型**：`OUT_OF_RANGE`

### 检查 4：必填字段 `REQUIRED_FIELDS`

- **查什么**：`mmsi / lon / lat / event_category / event_type` 是否为空
- **ERROR 条件**：存在空值
- **issue 类型**：`NULL_FIELD`

### 检查 5：ENTER/EXIT 成对 `ENTER_EXIT_BALANCE`

- **查什么**：按 `mmsi + zone_id + event_category` 分组，ENTER 数是否等于 EXIT 数
- **WARN 条件**：不相等（周边界船已在区内/仍在区内，属常见现象）
- **issue 类型**：`UNBALANCED_PAIR`

### 检查 6：ENTER 早于 EXIT `ENTER_BEFORE_EXIT`

- **查什么**：同一通行对，EXIT 时间是否晚于 ENTER
- **ERROR 条件**：EXIT 时间 <= ENTER 时间
- **issue 类型**：`ENTER_AFTER_EXIT`

### 检查 7：重复事件 `DUPLICATE_EVENT`

- **查什么**：`(mmsi, zone_id, category, type, event_time)` 完全相同的记录
- **ERROR 条件**：存在重复（Job 重跑可能导致）
- **issue 类型**：`DUPLICATE_EVENT`

### 检查 8：合法区域类型 `INVALID_CATEGORY`

- **查什么**：`event_category` 是否在 13 种合法值内
- **ERROR 条件**：存在未知类型
- **issue 类型**：`INVALID_CATEGORY`

### 检查 9：抽样轨迹回放 `REPLAY_SAMPLE`（可选）

- **抽样策略**：**分层抽样**，每个分层默认 **2 艘**，总量不超过 **60 艘**
- **分层维度**（三维组合）：

| 维度 | 规则 |
|---|---|
| 航行范围 | **国内**：MMSI 412/413/414/416 开头；**国际**：其他 |
| 船型 | `recentships.type`（散货船、油船、集装箱船等） |
| 船长档 | **短** ≤120m；**中** 120~225m；**长** >225m |

- **示例分层**：`国内|散货船|长(290m)`、`国际|油船|中(180m)` — 每组最多抽 2 艘
- **算法**：先按分层键分组 → 每组抽 2 艘 → 总量达 60 上限停止
- **做法**：HBase 重算 vs event 表对比（总条数 + 各 category 条数）
- **detail 示例**：`分层覆盖=18组 [国内|散货船|长×2, 国际|油船|中×2, ...] | 413xxx[国内/散货船/长(225m)]; ...`
- **关闭**：参数 `replay=false` 或 `max-replay-ships=0`

### 检查 10：坐标空间抽检 `SPATIAL_SPOT_CHECK`（可选）

- **抽样策略**：与检查 9 **相同分层规则**，每层 2 艘，最多 **30 艘船**、**60 条 event**
- **做法**：在选中船舶的事件中分配坐标点（每艘至少 1 条），做点-in-面验证
- **关闭**：参数 `spatial=false` 或 `max-spatial-events=0`

---

## 6. 结论与评分

### 6.1 批次结论

| quality_status | 条件 |
|---|---|
| **FAIL** | 任意检查项 severity = ERROR 且 issue_count > 0 |
| **WARN** | 无 ERROR，但有 WARN |
| **PASS** | 全部 PASS |

### 6.2 质量分（quality_score，0~100）

```
起始 100 分
- 完成率不足：扣 (100 - 完成率) / 2
- 每个 ERROR 问题：扣 10
- 每个 WARN 问题：扣 2
- FAIL 批次上限 59，WARN 批次上限 79
```

---

## 7. 结果表结构

### 7.1 总报告 `ship_zone_passage_quality_report`

每个批次（周）一条，核心字段：

| 字段 | 说明 |
|---|---|
| batch_month | 批次标识，如 `2025-12-17-W` |
| start_time / end_time | 评估时间范围 |
| quality_status | PASS / WARN / FAIL |
| quality_score | 0~100 |
| completion_rate | 完成率 % |
| total_ships_expected / done / failed | 船舶统计 |
| total_events_in_status / actual | status 计数 vs event 表实际 |
| event_count_mismatch_ships | 对账不一致船数 |
| unbalanced_pair_groups | ENTER/EXIT 不成对组合数 |
| duplicate_event_groups | 重复事件组数 |
| enter_after_exit_pairs | 时间逆序通行对数 |
| replay_sample_ships / replay_mismatch_ships | 回放抽样结果 |
| spatial_check_events / spatial_mismatch_events | 空间抽检结果 |
| check_results_json | 10 项检查 JSON 摘要 |
| issue_summary | 问题文字摘要 |

### 7.2 检查项 `ship_zone_passage_quality_check`

每个 report_id 对应 10 行（每项检查一行）：

| 字段 | 说明 |
|---|---|
| check_code | 如 `EVENT_COUNT_RECONCILE` |
| check_name | 中文名称 |
| severity | PASS / ERROR / WARN |
| issue_count | 问题数量 |
| detail | 检查详情 |

### 7.3 按类型统计 `ship_zone_passage_quality_category_stat`

按 `event_category` 汇总：

| 字段 | 说明 |
|---|---|
| event_count / ship_count | 事件数、涉及船舶数 |
| enter_count / exit_count | ENTER/EXIT 数 |
| passage_pairs | 完整通行对数 |
| unbalanced_groups | 不成对组合数 |

### 7.4 按区域统计 `ship_zone_passage_quality_zone_stat`

按 `zone_id + event_category` 汇总（TOP 300）：

| 字段 | 说明 |
|---|---|
| zone_id / zone_name | 区域 |
| event_count / ship_count | 通行量 |

### 7.5 问题明细 `ship_zone_passage_quality_issue`

可追溯的具体问题（每类最多 200~500 条）：

| 字段 | 说明 |
|---|---|
| check_code / issue_type | 关联检查项 |
| severity | ERROR / WARN |
| mmsi / zone_id / zone_name | 定位信息 |
| detail | 问题描述 |

---

## 8. 示例数据

### 8.1 总报告示例

```
batch_month:        2025-12-17-W
quality_status:     WARN
quality_score:      76
completion_rate:    100.00
total_ships_done:   72920
total_events_actual: 442188
issue_summary:      不成对=128(WARN); 回放不一致=1(WARN)
```

### 8.2 检查项示例

| check_code | severity | issue_count | detail |
|---|---|---|---|
| TASK_COMPLETENESS | PASS | 0 | expected=72920 done=72920 rate=100% |
| EVENT_COUNT_RECONCILE | PASS | 0 | 不一致船舶=0 |
| ENTER_EXIT_BALANCE | WARN | 128 | 不成对组合=128（周边界常见） |
| REPLAY_SAMPLE | WARN | 1 | 抽样=5 不一致=1 |

### 8.3 按类型统计示例

| event_category | event_count | ship_count | enter_count | exit_count |
|---|---|---|---|---|
| SEA_AREA | 120000 | 35000 | 60000 | 60000 |
| STRAIT | 8520 | 4260 | 4260 | 4260 |
| STORM_WAVE | 3200 | 800 | 1600 | 1600 |

### 8.4 Job 日志示例

```
跳过已评估 2025-01-01-W
跳过未完成批次 2025-12-24-W done=200 currentTotal=116308
2025-12-17-W [2025-12-17 00:00:00~2025-12-24 00:00:00] WARN score=76 | ships=72920(failed=0) | events=442188 | err=0 warn=129 | 不成对=128(WARN)
评估完成：候选=78 新评=5 跳过=73 FAIL=0
```

---

## 9. 常用查询 SQL

```sql
-- 各批次质量概况
SELECT batch_month, quality_status, quality_score, completion_rate,
       total_ships_done, total_ships_failed, total_events_actual,
       error_count, warn_count, issue_summary, evaluated_at
FROM dataanalysis.ship_zone_passage_quality_report
ORDER BY start_time DESC;

-- 某批次 10 项检查（替换 report_id）
SELECT check_code, check_name, severity, issue_count, detail
FROM dataanalysis.ship_zone_passage_quality_check
WHERE report_id = 1
ORDER BY id;

-- 各区域类型通行量
SELECT event_category, event_count, ship_count, enter_count, exit_count,
       unbalanced_groups
FROM dataanalysis.ship_zone_passage_quality_category_stat
WHERE report_id = 1
ORDER BY event_count DESC;

-- 热点区域 TOP 20
SELECT zone_name, event_category, event_count, ship_count, unbalanced_groups
FROM dataanalysis.ship_zone_passage_quality_zone_stat
WHERE report_id = 1
ORDER BY event_count DESC
LIMIT 20;

-- ERROR 级问题明细
SELECT check_code, issue_type, mmsi, zone_name, event_category, detail
FROM dataanalysis.ship_zone_passage_quality_issue
WHERE report_id = 1 AND severity = 'ERROR';

-- 统计各结论批次数量
SELECT quality_status, COUNT(*) AS batch_cnt, AVG(quality_score) AS avg_score
FROM dataanalysis.ship_zone_passage_quality_report
GROUP BY quality_status;
```

---

## 10. 使用建议

### 10.1 与补算 Job 并行

```
补算 Job (zonePassageHistory2025JobHandler)  →  继续跑，别停
        ↓ 并行
评估 Job (zonePassageCompletedQualityJobHandler)  →  评已结束、未评过的周
```

### 10.2 首次全量评估

1. XXL-Job 注册 `zonePassageCompletedQualityJobHandler`
2. 参数建议：`replay=false&spatial=false`（更快）
3. 早期只有部分船舶的周，用 `week=YYYY-MM-DD` 逐周评

### 10.3 结果解读

| 结论 | 含义 | 建议 |
|---|---|---|
| PASS | 数据自洽，无明显问题 | 可放心使用 |
| WARN | 有周边界不成对、抽样偏差等 | 看 issue 明细，通常可接受 |
| FAIL | 对账不一致、重复、空字段等 | 需排查补算 Job 或重跑该周 |

### 10.4 评估边界（本 Job 不覆盖）

- 不验证全量轨迹算法精度（仅 5 艘抽样回放）
- 不检测 DP 抽稀 zoom=9 导致的漏检
- 不对 STORM_WAVE/WIND 做坐标点-in-面（算法是线面相交）
- 不验证业务上「进出时间是否合理」（如停留时长异常）

---

## 11. 相关代码文件

| 文件 | 说明 |
|---|---|
| `xxljob/ZonePassageQualityJob.java` | Job 入口 |
| `service/ZonePassageQualityService.java` | 评估引擎 |
| `repository/ZonePassageQualityRepository.java` | 数据访问 |
| `job/services/ZonePassageAnalysisService.java` | 提供抽样回放重算方法 |
| `sql/ship_zone_passage_quality_report.sql` | 建表 DDL |

---

*文档版本：2026-07-06*
