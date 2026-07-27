# 船舶徘徊 V3 XXL-JOB 批量测试链路

## 1. 目标与边界

本文说明 `shipLoiteringV3BatchJobHandler` 的配置、运行和数据验证方式。
该任务用于小规模测试、历史回溯和生产增量验证，不替代或修改以下逻辑：

- `LoiteringV3EpisodeBuilder`、`LoiteringV3FeatureExtractor`、
  `LoiteringV3RuleEngine`、`LoiteringV3EventReconciler`；
- 现有 `shipLoiteringJobHandler` / `shipLoiteringBatchJobHandler` V2 链路；
- Alongside 计算链路；
- `AIS41GeneralQueryRepository` 的共享查询接口。

## 2. 当前项目链路

### 2.1 XXL-JOB 注册与参数

项目通过 `XxlJobConfiguration` 创建 `XxlJobSpringExecutor`，读取
`xxljob.adminAddresses`、`xxljob.appname`、`xxljob.ip`、`xxljob.port`、
`xxljob.accessToken`、`xxljob.logPath` 和 `xxljob.logRetentionDays`。

JobHandler 使用 Spring `@Component` 加 `@XxlJob("handlerName")` 注册。
XXL-JOB 的任务参数以字符串传入 Handler，本任务要求 JSON。运行日志同时写入：

- 应用日志（SLF4J）；
- XXL-JOB 执行日志（`XxlJobLogger`）。

现有参考任务：

| 任务 | Handler | 主要调用 |
| --- | --- | --- |
| AIS 航行时间和里程分析 | `AisTrajectoryAnalysis` | `AlertJob -> AisAnalysisService` |
| 船舶搭靠测试/批量 | `shipAlongsideJobHandler` / `shipAlongsideBatchJobHandler` | `ShipBehaviorJob -> ShipBehaviorAnalysisService` |
| 船舶事件分析 | `shipLoiteringJobHandler` / `shipLoiteringBatchJobHandler` | `ShipBehaviorJob -> ShipBehaviorAnalysisService` |

### 2.2 V3 新链路

```text
XXL-JOB: shipLoiteringV3BatchJobHandler
  -> ShipLoiteringV3BatchJob
  -> ShipLoiteringV3BatchService
  -> ais41.recentships 分页选船
  -> LoiteringV3HBaseTrajectorySource（未抽稀、分片读取）
  -> LoiteringV3AnalyzerService
  -> Loitering V3 episode / feature / rule / reconcile
  -> AnalyzedEvent
  -> ShipBehaviorEvent / ShipBehaviorEventDetail
  -> ship_behavior_event / ship_behavior_event_detail
  -> ship_behavior_loitering_batch_status
```

### 2.3 V3 接口现状确认

- V3 核心入口：`LoiteringV3AnalyzerService.analyze(runTime)`。
- 核心入口原生扫描 `runTime` 前 24 小时，并由 Provider 提供船舶、轨迹和场景。
- 核心本身没有对外暴露单 MMSI 方法；新 Batch Service 每次向 Provider 传一艘船，
  因而形成单船、单时间窗调用。
- 新 Batch Service 提供批量入口 `execute(params)`，单次只读取一页船舶。
- test/history 的范围由参数确定；超过 24 小时时按配置切窗。
- increment 从 V3 成功进度的最大 `window_end_time` 继续计算；首次运行回退
  到 `now-hours`。
- 核心分析器本身不写数据库。新 Batch Service 在每窗分析成功后自动替换 V3
  摘要和明细并记录进度。
- 同一页、同一时间范围可以重复计算；写入前会删除该 MMSI、V3 规则版本和窗口内
  的旧记录，避免重跑累积重复事件。

## 3. 船舶筛选与分页

专用分页 SQL 保持 `AIS41GeneralQueryRepository.selectRecentShips()` 的既有业务条件：

- 排除渔船、游艇、工程及服务船、客船、拖带船、拖轮、专用船、其他、
  未知类型船舶和近海作业船；
- `length > 70`；
- `updatetime > '2025-01-01 00:00:00'`；
- MMSI 为 9 位有效数值范围；
- 按 MMSI 稳定排序。

查询始终包含 `LIMIT ? OFFSET ?`。默认页大小 500，最大页大小 500，因此不会把
约 11 万条 `recentships` 一次加载到 JVM。

history 回溯下一页示例：

```json
{
  "mode": "history",
  "startTime": "2025-01-01 00:00:00",
  "endTime": "2025-12-31 23:59:59",
  "limit": 500,
  "offset": 500
}
```

## 4. 配置

应用配置：

```properties
ship.behavior.loitering.v3.batch.test-ship-limit=100
ship.behavior.loitering.v3.batch.page-size=500
ship.behavior.loitering.v3.batch.max-page-size=500
ship.behavior.loitering.v3.batch.window-hours=24
ship.behavior.loitering.v3.batch.trajectory-load-slice-hours=6
ship.behavior.loitering.v3.batch.max-points-per-window=100000
```

| 配置 | 含义 |
| --- | --- |
| `test-ship-limit` | test 模式未传 `limit` 时的默认船数 |
| `page-size` | history/increment 默认页大小 |
| `max-page-size` | 任意模式允许的单页最大船数 |
| `window-hours` | 长时间范围的外层计算切窗 |
| `trajectory-load-slice-hours` | 单窗内部读取 HBase 的分片小时数 |
| `max-points-per-window` | 单船单窗最大原始点数，超出后该船失败隔离 |

## 5. 参数

### 5.1 test：小规模验证

```json
{
  "mode": "test",
  "limit": 100,
  "days": 30,
  "offset": 0
}
```

- `limit` 默认 100；
- `days` 默认 30；
- 时间为 `[now-days, now]`；
- 省略全部参数时默认进入 test 模式，避免误触发大规模历史任务。

### 5.2 history：历史回溯

```json
{
  "mode": "history",
  "startTime": "2025-01-01 00:00:00",
  "endTime": "2025-12-31 23:59:59",
  "limit": 500,
  "offset": 0
}
```

- `startTime`、`endTime` 必填，格式为 `yyyy-MM-dd HH:mm:ss`；
- `startTime` 必须早于 `endTime`；
- 每次最多处理 500 艘；后续页将 `offset` 依次设为 500、1000、1500。

### 5.3 increment：生产增量

```json
{
  "mode": "increment",
  "hours": 24,
  "limit": 500,
  "offset": 0
}
```

- 只选择 `updatetime >= now-hours` 的最近更新船舶；
- 每船优先从 `ship_behavior_loitering_batch_status` 中
  `batch_no LIKE 'loitering-v3-%'` 的最后成功结束时间继续；
- 没有成功记录时，从 `now-hours` 开始；
- 最近更新船超过 500 艘时，应按 offset 配置多个不重叠分页任务，或人工逐页执行。

所有模式都可选传 `batchNo`；最大长度 64。未传时自动生成，increment 使用稳定
批次名 `loitering-v3-increment`。

## 6. XXL-JOB 配置与启动

### 6.1 执行器

服务器外置配置至少包含：

```properties
xxljob.enabled=true
xxljob.adminAddresses=http://<xxl-job-admin>/xxl-job-admin
xxljob.appname=<executor-app-name>
xxljob.ip=<executor-ip>
xxljob.port=<executor-port>
xxljob.accessToken=<token>
xxljob.logPath=<log-directory>
xxljob.logRetentionDays=30
```

不要直接使用仓库内的生产数据源示例做本地验证；本地应使用独立测试数据库、
测试 HBase 和测试 XXL-JOB Admin 的外置配置。

### 6.2 编译和测试

```bash
mvn -DskipTests=false -Dsurefire.skipTests=false \
  -Dtest=ShipLoiteringV3BatchServiceTest,LoiteringV3AnalyzerServiceTest,\
LoiteringV3EpisodeBuilderTest,LoiteringV3RuleEngineTest,\
LoiteringV3EventReconcilerTest,LoiteringV3HBaseTrajectorySourceTest test
```

### 6.3 启动

```bash
mvn clean package
java -jar target/ship-risk-data-0.0.1-SNAPSHOT.jar \
  --spring.config.location=/absolute/path/application-test.properties
```

启动后在 XXL-JOB Admin 新建任务：

| 项目 | 值 |
| --- | --- |
| JobHandler | `shipLoiteringV3BatchJobHandler` |
| 任务描述 | 船舶徘徊V3测试计算 |
| Cron | `0 0 * * * ?`（生产增量每小时） |
| 路由策略 | FIRST |
| 阻塞处理策略 | 单机串行 |
| 初期运行方式 | 停用自动 Cron，先手动执行 test 参数 |

建议验证顺序：

1. `{"mode":"test","limit":10,"days":3}`；
2. `{"mode":"test","limit":100,"days":30}`；
3. 小时间段 history；
4. 分页 history；
5. 最后启用 increment Cron。

## 7. 日志

任务至少输出：

```text
Loitering V3 Batch Start mode=history ... ship count=500
MMSI=412xxxxxx startTime=... endTime=... trajectory points=2350 events=3 status=SUCCESS
MMSI=412xxxxxx trajectory points=2350 events=3 status=DONE
Loitering V3 Batch Summary ... success=498 failed=2 ... duration=120s
```

单船/单窗失败会记录 MMSI、时间窗和错误，并继续下一艘船。任一船失败时
XXL-JOB 返回失败，便于告警和重试；成功船已写入的数据不会回滚。

应用日志示例：

```bash
grep -n "Loitering V3 Batch\\|MMSI=" /path/to/application.log | tail -200
```

XXL-JOB Admin 可在该次执行的“执行日志”中查看相同摘要。

## 8. 数据写入与幂等

V3 继续使用标准行为类型：

```text
behavior_type = LOITERING
rule_version  = LOITERING_V3_SHADOW
```

`LOITERING_V3` 不是 `behavior_type` 的值；V3 身份由 `rule_version` 区分。
摘要写入 `ship_behavior_event`，轨迹点写入 `ship_behavior_event_detail`。

每个成功窗口在一个 dataanalysis 数据库事务内：

1. 删除相同 `behavior_type + mmsi + rule_version` 且事件开始时间落在该窗口的旧明细；
2. 删除对应旧摘要；
3. 写入本次摘要；
4. 写入本次轨迹明细；
5. 事务成功后将进度标记为 DONE。

因此相同 MMSI、事件类型和开始时间重复运行后只保留一份当前结果。XXL-JOB
必须使用单机串行阻塞策略，避免两个相同分页任务并发替换同一窗口。

## 9. 数据验证 SQL

### 9.1 最近 V3 事件

```sql
SELECT
  id,
  mmsi,
  behavior_type,
  rule_version,
  start_time,
  end_time,
  event_sub_type,
  event_score,
  confidence_level,
  exclusion_flag,
  exclusion_reason,
  batch_no
FROM dataanalysis.ship_behavior_event
WHERE behavior_type = 'LOITERING'
  AND rule_version = 'LOITERING_V3_SHADOW'
ORDER BY id DESC
LIMIT 100;
```

### 9.2 随机/人工选择 10 艘有结果船

```sql
SELECT
  mmsi,
  COUNT(*) AS event_count,
  MIN(start_time) AS first_event_time,
  MAX(end_time) AS last_event_time
FROM dataanalysis.ship_behavior_event
WHERE behavior_type = 'LOITERING'
  AND rule_version = 'LOITERING_V3_SHADOW'
GROUP BY mmsi
ORDER BY MAX(id) DESC
LIMIT 10;
```

### 9.3 摘要与明细

```sql
SELECT COUNT(*) AS detail_points
FROM dataanalysis.ship_behavior_event_detail
WHERE behavior_type = 'LOITERING'
  AND rule_version = 'LOITERING_V3_SHADOW'
  AND mmsi = '<MMSI>'
  AND event_start_time = '<START_TIME>';
```

### 9.4 重复检查

```sql
SELECT
  mmsi,
  behavior_type,
  start_time,
  COUNT(*) AS duplicate_count
FROM dataanalysis.ship_behavior_event
WHERE behavior_type = 'LOITERING'
  AND rule_version = 'LOITERING_V3_SHADOW'
GROUP BY mmsi, behavior_type, start_time
HAVING COUNT(*) > 1;
```

期望返回 0 行。可用完全相同参数重跑一次后再次执行此 SQL。

### 9.5 进度与失败

```sql
SELECT
  batch_no,
  status,
  COUNT(*) AS window_count,
  SUM(point_count) AS point_count,
  SUM(event_count) AS event_count
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no LIKE 'loitering-v3-%'
GROUP BY batch_no, status
ORDER BY batch_no DESC;
```

```sql
SELECT mmsi, window_start_time, window_end_time, error_message
FROM dataanalysis.ship_behavior_loitering_batch_status
WHERE batch_no LIKE 'loitering-v3-%'
  AND status = 'FAILED'
ORDER BY updated_at DESC
LIMIT 100;
```

## 10. 三类典型案例

### 案例 1：明显低速绕圈

选择持续低速、轨迹闭合并有明显累计转向的船舶与时间段，使用 history 模式只跑
该船所在分页。V3 核心的模式名是 `CIRCULAR` / `ORBITAL`，不是 V2 的
`SLOW_LOITERING`。验收：

- `event_sub_type` 为 `CIRCULAR` 或 `ORBITAL`；
- `score_detail_json.trajectoryPattern` 与摘要一致；
- 有对应轨迹明细；
- 权威泊位数据不可用时，结果应为 REVIEW，而不是直接发布正式事件。

### 案例 2：港口附近等待/正常靠泊

当前仓库已有港区和锚地数据，但设计文档明确记录：尚无经过确认的泊位 polygon、
靠泊点和航道 Provider。新链路不会把锚地伪装成泊位，因此当前只能验证：

- 港区点能进入候选链路；
- 泊位数据不可用时 `score_detail_json.decisionStatus=REVIEW`；
- `exclusion_reason=SCENE_DATA_UNAVAILABLE`。

只有接入权威泊位数据后，才可把以下结果作为验收条件：

```text
decisionStatus = EXCLUDED
exclusion_reason = NORMAL_BERTHING 或 NORMAL_BERTHING_APPROACH
```

这属于场景 Provider 的后续工作，不应通过修改 V3 核心规则或复用 Alongside
中心点语义来绕过。

### 案例 3：正常航行

选择持续正常航速、方向效率高、没有长时间低速局部机动的轨迹。验收：

- 优先期望没有 V3 事件；
- 若进入候选，则应被判为 `STRAIGHT_LOW_SPEED_TRANSIT` 或其他非正式结果；
- 不应出现 `decisionStatus=FINAL` 的圆形、蛇形或往返事件。

## 11. 已知边界

- 本任务没有修改 V3 核心算法；泊位/靠泊点 Provider 仍未接入，因此当前生产验证
  重点是 AIS 读取、算法运行、REVIEW 输出、幂等入库和失败隔离闭环。
- history 长范围按 24 小时切窗。跨窗事件仍由现有 V3 输出键保留在
  `score_detail_json.eventKey`，但现有共享表尚无独立 `event_key` 列；本任务的
  重跑幂等以 `mmsi + behavior_type + start_time` 的窗口替换实现。
- 单次只处理一页。history 和大规模 increment 必须显式安排不重叠 offset，
  以控制 HBase、内存和数据库压力。
