# 0731 液货船/集装箱船搭靠与徘徊测试维护手册

最后更新：2026-07-31 15:02 CST

状态来源：当前分支代码、XXL-JOB 日志、线上进程和事件数据库只读快照。

本文档是当前液货船/集装箱船搭靠、徘徊测试的唯一入口。旧的5万商船测试、
OpenSea 瓦片和 reviewed dataset 均不是当前任务口径；历史材料只用于追溯。

## 1. 当前结论

### 1.1 运行环境

| 项目 | 当前值 |
|---|---|
| 服务器 | `ecs-user@39.96.38.242:1222`，服务目录 `/data/zhengyi` |
| 运行 JAR | `ship-risk-data-0.0.1-SNAPSHOT.jar` |
| 当前 JAR SHA-256 | `4b6a45a6d1231d9eab1eedee5e18e9b73f7802c7b6be7aa3cd513c747e679f00` |
| Java | `-Xms512m -Xmx2g`，`Asia/Shanghai` |
| HTTP / XXL | `8090 / 30008`，均已监听 |
| 当前名单 | `liquid-container-5000-2025h2-2026h1-v1`，5,000个唯一 MMSI |
| 名单构成 | 液货船2,500艘、集装箱船2,500艘，`seq_no=1..5000` 交替排列 |
| 搭靠规则 | `ALONGSIDE_LIQUID_CONTAINER_V2` |
| 徘徊规则 | `LOITERING_LIQUID_CONTAINER_V2` |
| 排除数据指纹 | `7470be70e955bc79064bc402586c592a8a8da7a241e00c61997b1fa1bc0df50f` |
| 排除快照 | `AVAILABLE` |
| 本地测试基线 | 188 tests，0 failure，0 error，0 skipped |

### 1.2 2026上半年正式范围任务

两个任务均于 2026-07-31 11:06 启动，允许并行；每个任务内部仍为单线程顺序
处理。XXL 日志：

- 搭靠：`/data/zhengyi/logs/job/jobhandler/2026-07-31/47068868.log`
- 徘徊：`/data/zhengyi/logs/job/jobhandler/2026-07-31/47068882.log`

2026-07-31 最终只读验收：

| 行为 | 完成时间 | 月份 | 窗口 | 候选 | 正式事件 | 结论 |
|---|---|---:|---:|---:|---:|---|
| 搭靠 | 11:48:41 | 6/6 | 5,800 | 1,635 | 0 | 技术完成，需继续分析零事件原因 |
| 徘徊 | 11:44:39 | 6/6 | 36,200 | 163 | 8 | 技术完成，3艘船，建议地图复核 |

两个任务均为 `segmentsCompleted=6`、`segmentsFailed=0`、`ReturnT code=200`，
状态表无失败窗口，状态事件数与正式事件表一致。8条徘徊事件全部通过正式事件
硬门禁，无重复事件和降级候选。该结果只表示程序和数据门禁验收通过，不替代对
弱形态事件及空间覆盖缺口的人工业务复核。

### 1.3 5,000艘年度任务运行快照

5,000艘名单任务于 `14:32:18` 完成，返回 `code=200`，名单总数、MMSI唯一性、
类型分布和交替顺序均已通过数据库验收。年度范围为
`2025-07-01 00:00:00` 至 `2026-07-01 00:00:00`，两个行为任务均从首个
自然月开始计算。

线上日志：

- 名单：`/data/zhengyi/logs/job/jobhandler/2026-07-31/47076153.log`
- 搭靠：`/data/zhengyi/logs/job/jobhandler/2026-07-31/47076873.log`
- 徘徊：`/data/zhengyi/logs/job/jobhandler/2026-07-31/47076889.log`

`2026-07-31 15:02 CST` 运行中快照：

| 行为 | 当前子批次 | 已处理船 | DONE窗口 | FAILED窗口 | 候选 | 正式事件 |
|---|---|---:|---:|---:|---:|---:|
| 搭靠 | `alongside-lc-s5000-202507010000-202508010000-v3` | 700 | 3,499 | 1 | 0 | 0 |
| 徘徊 | `loitering-lc-s5000-202507010000-202508010000-v3` | 750 | 23,250 | 0 | 74 | 1 |

两项任务的排除快照均为 `AVAILABLE`，指纹与任务参数一致。徘徊仍在正常运行。
搭靠的唯一失败窗口是 MMSI `215880000`、`2025-07-22 00:00:00` 至
`2025-07-29 00:00:00`，错误为 `replay failed count=1`；它属于轨迹回放
失败，不是空间来源、名单或指纹异常。

当前不得重复触发正在运行的任务。搭靠完成首月扫描后预计会因该失败窗口停止后续
月份；待 XXL 返回后，复制原年度 JSON 人工执行一次。`resume=true` 会跳过
DONE窗口并重算 FAILED/无状态窗口。若相同窗口再次失败，应检查该 MMSI、该时间段
的服务器端回放/HBase链路，不得手工改状态或无限重试。

## 2. 当前事件语义和隔离边界

当前规则只证明候选轨迹未进入已经成功加载的港区、锚地或泊位，不证明全球开放
海域、远岸、非内河或非航道。因此：

- 不使用瓦片、candidate/reviewed dataset、`coverage_complete` 或
  `sceneDatasetVersion`。
- 不读取或写入 `ship_behavior_maritime_scene_zone`。
- 正式事件固定写 `near_port=0`、`near_anchor=0`、`open_sea=NULL`。
- 正式事件固定写 `exclusion_flag='VALID_RISK_EVENT'` 和
  `exclusion_reason='OUTSIDE_PORT_ANCHORAGE_BERTH'`。
- `score_detail_json.ruleFeatures.exclusionEvidence` 保存三类命中结果、七来源
  可用数和排除数据指纹。
- AIS 静态资料、HBase 轨迹和七个空间来源全部只读。
- 新逻辑只由专用 Handler 人工触发，不修改旧搭靠、徘徊 Handler 的行为。

## 3. 船型门禁

### 3.1 允许类型

- `TANKER_OIL`
- `TANKER_CHEMICAL`
- `TANKER_GAS`
- `TANKER_UNSPECIFIED`
- `CONTAINER`

液货船子类型之间出现明确冲突时归为 `TANKER_UNSPECIFIED`。液货船与集装箱船、
拖轮或其他大类之间冲突时拒绝。空资料、未知类型、静态资料查询失败、拖轮、拖带
船及其他船型全部 fail-closed。

徘徊只检查主船。搭靠的主船与目标船必须分别通过同一白名单；目标船不能因为 AIS
80–89 广义 tanker 代码单独放行。

## 4. 空间排除和健康状态

### 4.1 七个来源

| 类型 | 来源 | 当前可用数 | 处理 |
|---|---|---:|---|
| 港区 | `dataanalysis.zones_with_port_id` | 13,236 | Polygon/MultiPolygon |
| 港区 | `dataanalysis.portsboundary` | 15,429 | Polygon/MultiPolygon |
| 锚地 | `dataanalysis.port_anchorages` | 3,423 | Polygon/MultiPolygon |
| 锚地 | `ais41.anchor_boundary` | 135 | Polygon/MultiPolygon |
| 锚地 | `ais41.alertzones` 命名锚地 | 2,894 | 过滤非面对象 |
| 泊位 | `dataanalysis.berth_sts_with_port_id` | 75 | Polygon/MultiPolygon |
| 泊位 | `dataanalysis.berth_with_port_id` | 39,813 | Point 外扩0.5海里 |

主船候选轨迹的任一点或相邻线段命中即排除；搭靠还要对目标船候选轨迹执行相同
检查。所有可用几何建立 JTS `STRtree` 和 `PreparedGeometry` 索引。

当前快照共修复135个无效面；`alertzones` 正常过滤731个非面对象，1个无法安全
修复的几何采用包络隔离，无法解析数为0。

### 4.2 健康状态

| 状态 | 运行行为 |
|---|---|
| `AVAILABLE` | 未命中正常排除区或隔离包络的可信事件可写正式表；隔离包络命中只写复核表 |
| `DEGRADED` | 继续计算，但正式事件表零写入，所有通过行为门禁的候选写复核表 |
| `UNAVAILABLE` | 七来源任一查询失败、无可用几何、索引初始化失败等，处理窗口前整批停止 |

正式任务还必须精确匹配 `expectedExclusionFingerprint`。指纹变化通常说明七来源数据
发生变化；不得把新指纹直接填入正在续跑的旧批次。应重新 dry-run、完成对账，并用
新的 `batchVersion` 启动新批次。

## 5. 搭靠 V2 规则

规则版本：`ALONGSIDE_LIQUID_CONTAINER_V2`

行为候选必须同时满足：

| 门禁 | 默认值 |
|---|---:|
| 持续时间 | `>=45` 分钟 |
| 双方有效点 | 各 `>=10` |
| 双方速度范围 | `1.0–3.0` 节 |
| 最近点同步误差 | `<=3` 分钟 |
| 同步点覆盖率 | `>=0.75` |
| 双方速度命中比例 | `>=0.75` |
| 相对速度 P90 | `<=0.5` 节 |
| 航向差 P90 | `<=15°` |
| 舷侧相对方位 | `45°–135°` |
| 舷侧比例 | `>=0.75` |
| 左/右舷方位稳定性 | `>=0.75` |
| 距离稳定性 | `>=0.75` |
| 默认最大距离 | `0.30` 海里 |
| 动态距离范围 | `0.10–0.35` 海里 |
| 动态距离系数 | 双船总长度海里数的 `0.90` |
| 纵向偏移 P90 | 不超过动态最大距离的 `0.75` |

距离、相对速度、航向和舷侧形态任何一项不满足都会拒绝。纵向偏移过大按跟随或
追越形态拒绝。正式搭靠事件的目标船类型必须仍属于白名单。

## 6. 徘徊 V2 规则

规则版本：`LOITERING_LIQUID_CONTAINER_V2`

基础门禁：

| 门禁 | 默认值 |
|---|---:|
| 持续时间 | `>=120` 分钟 |
| 有效点 | `>=24` |
| 速度范围 | `1.0–3.0` 节 |
| 速度命中比例 | `>=0.75` |
| 轨迹覆盖率 | `>=0.75` |
| 覆盖率估算间隔 | 30分钟 |
| 最大活动半径证据 | `<=3.0` 海里 |

以下八项形态证据至少满足两项：

1. 起终点距离 `<=0.75` 海里。
2. 实际航程/净位移 `>=2.5`。
3. 累计转向 `>=270°`。
4. 至少1次 `>=150°` 的航向反转。
5. 闭环分数 `>=0.70`。
6. 回溯比例 `>=0.40`。
7. 净位移速度不超过平均速度的 `0.35`。
8. 活动半径 `<=3.0` 海里。

实际航程/净位移小于1.5、无航向反转且累计转向不足270°时，按直线低速通过拒绝。

## 7. XXL-JOB 配置

两项任务统一配置：

| 配置 | 值 |
|---|---|
| 执行器 | `ship-risk-data` |
| 运行模式 | `BEAN` |
| 路由 | `FIRST` |
| 阻塞策略 | 单机串行 |
| 任务超时 | `0` |
| XXL失败重试 | `0` |
| 状态 | 停止 |
| 触发 | 只允许人工“执行一次” |

搭靠 Handler：

```text
shipLiquidContainerAlongsideRangeJobHandler
```

徘徊 Handler：

```text
shipLiquidContainerLoiteringRangeJobHandler
```

以下是已经完成并保留作回归基线的200艘半年参数，不得用于当前5,000艘年度任务：

```json
{
  "mode": "liquid_container_exclusion_range",
  "listBatchNo": "liquid-container-2026h1-v1",
  "shipLimit": 200,
  "startTime": "2026-01-01 00:00:00",
  "endTime": "2026-07-01 00:00:00",
  "enableInsert": true,
  "storeDetails": false,
  "resume": true,
  "parallelism": 1,
  "retryCount": 1,
  "logIntervalShips": 10,
  "expectedExclusionFingerprint": "7470be70e955bc79064bc402586c592a8a8da7a241e00c61997b1fa1bc0df50f",
  "batchVersion": "v2"
}
```

范围 Handler 自动补充：

- 搭靠：`windowDays=7`、`ruleVersion=ALONGSIDE_LIQUID_CONTAINER_V2`
- 徘徊：`windowHours=24`、`ruleVersion=LOITERING_LIQUID_CONTAINER_V2`

### 7.1 自动子批次

搭靠：

```text
alongside-lc-s200-202601010000-202602010000-v2
alongside-lc-s200-202602010000-202603010000-v2
alongside-lc-s200-202603010000-202604010000-v2
alongside-lc-s200-202604010000-202605010000-v2
alongside-lc-s200-202605010000-202606010000-v2
alongside-lc-s200-202606010000-202607010000-v2
```

徘徊：

```text
loitering-lc-s200-202601010000-202602010000-v2
loitering-lc-s200-202602010000-202603010000-v2
loitering-lc-s200-202603010000-202604010000-v2
loitering-lc-s200-202604010000-202605010000-v2
loitering-lc-s200-202605010000-202606010000-v2
loitering-lc-s200-202606010000-202607010000-v2
```

范围任务总跨度不设上限，只要求 `startTime < endTime`。首尾保留准确时间，每个
自然月子批次不超过31天。子批次失败后停止后续月份。

### 7.2 重跑不变量

失败重跑必须复制原任务完整 JSON，不得修改：

- 起止时间、名单、`shipLimit`
- `expectedExclusionFingerprint`
- `batchVersion`
- `enableInsert=true`
- `resume=true`
- `parallelism=1`
- `retryCount=1`

已完成的窗口由状态表跳过，`FAILED` 或无状态窗口按相同口径重算。不得清空状态表
“重新开始”，也不得在同一批次中修改规则或空间数据口径。

### 7.3 当前5,000艘完整年度批次

本轮扩大测试使用新的不可变名单，不修改200艘历史名单。名单元数据从只读
`ais41.recentships` 分页筛选；行为轨迹仍只能由服务器端
`TrajectoryLoaderService` 调用 HBase，本机不直连 HBase。

名单 Handler：

```text
shipLiquidContainerTargetListTestJobHandler
```

名单参数：

```json
{
  "mode": "liquid_container_exclusion_list",
  "listBatchNo": "liquid-container-5000-2025h2-2026h1-v1",
  "sourceUpdatedAfter": "2026-01-01 00:00:00",
  "liquidCargoCount": 2500,
  "containerCount": 2500
}
```

搭靠和徘徊范围任务分别使用
`shipLiquidContainerAlongsideRangeJobHandler` 和
`shipLiquidContainerLoiteringRangeJobHandler`，两者参数完全相同：

```json
{
  "mode": "liquid_container_exclusion_range",
  "listBatchNo": "liquid-container-5000-2025h2-2026h1-v1",
  "shipLimit": 5000,
  "startTime": "2025-07-01 00:00:00",
  "endTime": "2026-07-01 00:00:00",
  "enableInsert": true,
  "storeDetails": false,
  "resume": true,
  "parallelism": 1,
  "retryCount": 1,
  "logIntervalShips": 50,
  "expectedExclusionFingerprint": "7470be70e955bc79064bc402586c592a8a8da7a241e00c61997b1fa1bc0df50f",
  "batchVersion": "v3"
}
```

范围 Handler 自动生成12个自然月子批次。搭靠预计产生295,000个状态窗口，
徘徊预计产生1,825,000个日状态窗口。续跑时每次只查询当前50艘船对应的DONE
窗口，避免把整月已完成状态一次加载进 JVM。

XXL配置仍为 BEAN、FIRST、单机串行、任务超时0、失败重试0、停止状态和人工
执行一次。先触发徘徊，首批窗口正常且服务器资源稳定约5分钟后触发搭靠；两个
Handler 随后并行，但各自内部保持 `parallelism=1`。

正式执行前必须重新确认日志中的排除指纹仍等于参数值。若指纹变化，旧参数应在
处理窗口前失败；不得直接替换指纹继续 `v3`，必须先验证新快照并使用新的
`batchVersion`。

中断后复制原JSON重新执行。范围任务会从第一个月份开始检查状态，但每个子批次
只跳过DONE窗口，FAILED和无状态窗口按相同口径重算。名单、时间、船数、指纹和
`batchVersion` 任一变化都会形成不同计算口径，不能用于原批次续跑。

## 8. 日志检查和事件排查

### 8.1 正常检查顺序

1. 查找 `range segment start`，确认当前自然月和自动批次名。
2. 查找 `exclusion fingerprint`，确认指纹和 `health=AVAILABLE`。
3. 查找月度 `batch summary`。
4. 依次检查 `shipsFailed`、`windowsFailed`、`candidates`、
   `eventsDetected`、`formalEventsStored`、`degradedCandidatesStored`。
5. 月份结束后确认下一条 `range segment start`。
6. 范围结束后确认 `range summary` 和 `ReturnT code=200`；年度批次必须为
   `segmentsCompleted=12 segmentsFailed=0`。

服务器只读命令：

```bash
cd /data/zhengyi
grep -E 'range segment start|batch summary|range summary|ReturnT' \
  logs/job/jobhandler/2026-07-31/47076873.log | tail -100

grep -E 'range segment start|batch summary|range summary|ReturnT' \
  logs/job/jobhandler/2026-07-31/47076889.log | tail -100
```

### 8.2 指标解释

- `candidates>0, eventsDetected=0`：候选已经产生，但船型、空间或严格行为形态
  门禁未通过；只要失败数为0，这不是计算异常。
- `eventsDetected>0, formalEventsStored=0`：先看 `enableInsert`、快照健康和
  `degradedCandidatesStored`，不要直接重跑。
- `degradedCandidatesStored>0`：查询独立复核表，不能把候选当正式事件。
- `quarantineHits>0`：候选命中无法安全修复几何的保守包络，只能进入复核。
- `shipsFailed>0` 或 `windowsFailed>0`：先按状态表 `error_message` 分类，再决定
  是否用原参数续跑。

### 8.3 按 MMSI 定位

```bash
grep 'mmsi=<MMSI>' <XXL日志>
grep -E 'ship type rejected|exclusion rejected|REPLAY_|failed' <XXL日志> | tail -200
```

数据库中先查事件，再查覆盖事件时间的状态窗口和 `score_detail_json`。不要只根据
一条 `event saved=0` 判断整艘船没有事件。

### 8.4 常见异常

| 现象 | 处理 |
|---|---|
| 指纹不一致 | 停止旧批次；确认空间源变化，重新 dry-run，并使用新 `batchVersion` |
| 必需来源查询失败或为空 | 检查数据库连接、权限、SQL和源表，不得绕过为可用 |
| `DEGRADED` | 正式表必须零写入，检查复核表和不可解析来源 |
| 窗口失败 | 查状态表 `error_message` 和对应日志，原参数 `resume=true` 续跑 |
| 候选多但事件为0 | 查看行为门禁拒绝原因，不应通过降低阈值“制造事件” |
| 正式事件与状态数不一致 | 停止后续验收，检查事务、批次名和重复键 |

在 MySQL 5.7 中不要把 `stored` 用作统计列别名。DBeaver 中
`SUM(stored_event_count) stored` 可能报1064语法错误，应写成
`SUM(stored_event_count) stored_events`。这类客户端查询错误不代表任务失败。

## 9. 数据库验收

可直接执行的只读 SQL：

- [来源和名单预检](liquid-container-exclusion/source-preflight.sql)
- [范围任务与事件验收](liquid-container-exclusion/validation.sql)

关键表：

| 表 | 用途 |
|---|---|
| `ship_behavior_tanker_open_sea_target_ship` | 固定名单快照，保留历史表名 |
| `ship_behavior_alongside_batch_status` | 搭靠窗口状态和事件数 |
| `ship_behavior_loitering_batch_status` | 徘徊窗口状态、候选和落库数 |
| `ship_behavior_event` | 正式可信事件 |
| `ship_behavior_event_degraded_candidate` | 降级或隔离包络候选，不能参与正式统计 |

禁止在排查过程中：

- 修改 `ais41`、HBase 或七个空间来源。
- 更新历史状态为 DONE。
- 删除正式事件或复核候选后直接重跑。
- 修改旧批次的指纹、规则、时间或名单。
- 把 `open_sea=NULL` 解释为开放海域证据。

## 10. 后续维护规则

- 规则、名单、时间范围或空间指纹任一变化，都使用新的 `batchVersion` 或新名单批次。
- 修改规则前先补单元测试，保持旧专用 Handler 和现有生产 Handler 行为不变。
- 新 JAR 先执行 `mvn clean test`、`mvn clean package` 并记录 SHA-256。
- 上传时只替换 JAR，不覆盖 `/data/zhengyi/application.properties`。
- 普通回滚只恢复备份 JAR，不删除名单、状态、正式事件或复核数据。
- 每次运行状态变化都在本页追加带时间戳的快照；历史结论不得覆盖。
- 当前年度任务结束前只记录“运行中”快照；只有两个范围日志均出现
  `segmentsCompleted=12 segmentsFailed=0`、`ReturnT code=200`，并完成数据库
  对账后，才能标记年度任务完成。

## 11. 状态更新模板

| 时间 | 行为/月度批次 | 状态与指标 | 结论/下一步 |
|---|---|---|---|
| `YYYY-MM-DD HH:mm` | `<batchNo>` | DONE/FAILED、窗口、候选、正式/降级事件 | 验收结论与操作 |
