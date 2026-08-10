# XXL-Job 配置、参数与运行手册

本手册用于人工配置测试任务。所有参数均为单行 JSON；不要在 XXL-Job “任务参数”框中插入换行、注释、Handler 名称或外层 `params` 字段。

## 一、运行前提

1. 测试服务器已经部署本次 JAR，服务启动日志没有数据源、WORLD_SEA、港区/锚地 Scene 或 XXL-Job 注册错误。
2. `application.properties` 至少满足：

```properties
ship.behavior.world-sea-mask.dataset-version=WORLD_SEA_102_V1R1
ship.behavior.world-sea-mask.required=true
ship.behavior.world-sea-mask.fail-closed=true
ship.behavior.scene.port-approach-distance-nm=10.0
ship.behavior.loitering.rule-version=LOITERING_V2_INSURANCE_SCENE_V1
ship.behavior.loitering.min-speed=1.0
ship.behavior.loitering.split-below-min-speed=true
ship.behavior.loitering.port-approach.required=false
ship.behavior.loitering.offshore.enabled=true
ship.behavior.loitering.weak.store-enabled=false
ship.behavior.loitering.scene.fail-closed=true
ship.behavior.loitering.scene-data.available=true
ship.behavior.alongside.rule-version=ALONGSIDE_V3_INSURANCE_SCENE_V2
ship.behavior.alongside.scene-data.available=true
ship.behavior.alongside.replay.bbox-buffer-nm=1.0
```

本轮 Loitering 只使用 `engines:["V2"]`，`ship.behavior.loitering.production.scene-ready` 可以继续为 false；该开关只保护 V3 production 链路。

3. 在 `dataanalysis` 执行 `query.sql` 的固定名单预检，确认 `total_rows=5000`、`enabled_rows=5000`、`distinct_mmsi=5000`。
4. 所有任务均人工触发。先 20，再 100，再 500，最后才允许 5000；Loitering 与 Alongside 不得并发。

## 二、XXL-Job 控制台字段

为每个规模和行为建立独立任务，不要反复修改同一个任务参数。

| 控制台字段 | 配置值 | 说明 |
| --- | --- | --- |
| 执行器 | `ship-risk-data` | 必须与 `xxljob.appname` 一致。 |
| 运行模式 | `BEAN` | 使用代码内 `@XxlJob` Handler。 |
| JobHandler | 见下表 | Loitering 与 Alongside 使用不同 Handler。 |
| 调度类型 | `无`/手动 | 不配置 Cron，不允许自动周期运行。 |
| 路由策略 | `第一个`或测试服唯一实例 | 确保只命中 242 上的一个实例。 |
| 阻塞处理策略 | `单机串行` | 上一个任务结束前不得启动下一个。 |
| 任务超时时间 | `0` 或平台允许的最长值 | 500/5000 可能长时间运行，不使用短超时强杀。 |
| 失败重试次数 | `0` | 应用参数已有 `retryCount=2`；不要由调度中心整任务重复执行。 |
| 负责人/报警 | 填实际负责人 | 失败后先查询状态表，不要立即换 batchNo。 |
| 任务参数 | 下文单行 JSON | 只复制 `{...}`，保持一行。 |

| 行为 | JobHandler |
| --- | --- |
| Loitering 徘徊 | `shipLoiteringProductionTestJobHandler` |
| Alongside 搭靠 | `shipAlongsideProductionTestJobHandler` |

## 三、可直接复制的单行参数

### 1. Loitering 20 船 dry-run

批次：`loitering-merchant20-2025-insurance-scene-v1`；不写正式事件。

```json
{"mode":"production_test","listBatchNo":"liquid-container-5000-2025h2-2026h1-v1","expectedShipCount":5000,"shipLimit":20,"pageSize":20,"parallelism":1,"startTime":"2025-01-01 00:00:00","endTime":"2026-01-01 00:00:00","windowDays":30,"trajectorySliceHours":6,"engines":["V2"],"resume":true,"retryCount":2,"logIntervalShips":10,"batchNo":"loitering-merchant20-2025-insurance-scene-v1","enableInsert":false,"storeDetails":false}
```

### 2. Alongside 20 船 dry-run

批次：`alongside-merchant20-2025-insurance-scene-v1`；不写正式事件。

```json
{"mode":"production_test","listBatchNo":"liquid-container-5000-2025h2-2026h1-v1","expectedShipCount":5000,"shipLimit":20,"pageSize":20,"parallelism":1,"startTime":"2025-01-01 00:00:00","endTime":"2026-01-01 00:00:00","windowDays":30,"trajectorySliceHours":6,"resume":true,"retryCount":2,"logIntervalShips":10,"batchNo":"alongside-merchant20-2025-insurance-scene-v1","enableInsert":false,"storeDetails":false}
```

### 3. Loitering 100 船 dry-run

```json
{"mode":"production_test","listBatchNo":"liquid-container-5000-2025h2-2026h1-v1","expectedShipCount":5000,"shipLimit":100,"pageSize":50,"parallelism":1,"startTime":"2025-01-01 00:00:00","endTime":"2026-01-01 00:00:00","windowDays":30,"trajectorySliceHours":6,"engines":["V2"],"resume":true,"retryCount":2,"logIntervalShips":10,"batchNo":"loitering-merchant100-2025-insurance-scene-v1","enableInsert":false,"storeDetails":false}
```

### 4. Alongside 100 船 dry-run

```json
{"mode":"production_test","listBatchNo":"liquid-container-5000-2025h2-2026h1-v1","expectedShipCount":5000,"shipLimit":100,"pageSize":50,"parallelism":1,"startTime":"2025-01-01 00:00:00","endTime":"2026-01-01 00:00:00","windowDays":30,"trajectorySliceHours":6,"resume":true,"retryCount":2,"logIntervalShips":10,"batchNo":"alongside-merchant100-2025-insurance-scene-v1","enableInsert":false,"storeDetails":false}
```

### 5. Loitering 500 船 dry-run

```json
{"mode":"production_test","listBatchNo":"liquid-container-5000-2025h2-2026h1-v1","expectedShipCount":5000,"shipLimit":500,"pageSize":50,"parallelism":1,"startTime":"2025-01-01 00:00:00","endTime":"2026-01-01 00:00:00","windowDays":30,"trajectorySliceHours":6,"engines":["V2"],"resume":true,"retryCount":2,"logIntervalShips":10,"batchNo":"loitering-merchant500-2025-insurance-scene-v1","enableInsert":false,"storeDetails":false}
```

### 6. Alongside 500 船 dry-run

```json
{"mode":"production_test","listBatchNo":"liquid-container-5000-2025h2-2026h1-v1","expectedShipCount":5000,"shipLimit":500,"pageSize":50,"parallelism":1,"startTime":"2025-01-01 00:00:00","endTime":"2026-01-01 00:00:00","windowDays":30,"trajectorySliceHours":6,"resume":true,"retryCount":2,"logIntervalShips":10,"batchNo":"alongside-merchant500-2025-insurance-scene-v1","enableInsert":false,"storeDetails":false}
```

### 7. Loitering 5000 船正式写库

仅在 20/100/500 两个行为全部通过、人工审核完成并明确授权后使用。`enableInsert=true` 会写正式事件。

```json
{"mode":"production_test","listBatchNo":"liquid-container-5000-2025h2-2026h1-v1","expectedShipCount":5000,"shipLimit":5000,"pageSize":50,"parallelism":1,"startTime":"2025-01-01 00:00:00","endTime":"2026-01-01 00:00:00","windowDays":30,"trajectorySliceHours":6,"engines":["V2"],"resume":true,"retryCount":2,"logIntervalShips":10,"batchNo":"loitering-merchant5000-2025-insurance-scene-v1","enableInsert":true,"storeDetails":false}
```

### 8. Alongside 5000 船正式写库

必须等待 Loitering 5000 完成并验收，禁止两个 5000 任务并发。

```json
{"mode":"production_test","listBatchNo":"liquid-container-5000-2025h2-2026h1-v1","expectedShipCount":5000,"shipLimit":5000,"pageSize":50,"parallelism":1,"startTime":"2025-01-01 00:00:00","endTime":"2026-01-01 00:00:00","windowDays":30,"trajectorySliceHours":6,"resume":true,"retryCount":2,"logIntervalShips":10,"batchNo":"alongside-merchant5000-2025-insurance-scene-v1","enableInsert":true,"storeDetails":false}
```

## 四、参数参考定义

| 参数 | 定义与约束 |
| --- | --- |
| `mode` | 固定 `production_test`，否则参数解析直接拒绝。 |
| `listBatchNo` | 固定名单身份，对应 `dataanalysis.ship_behavior_target_ship` 的 5000 商船名单。 |
| `expectedShipCount` | 校验固定名单应有 5000 船；不是本次执行上限。 |
| `shipLimit` | 本次从固定名单前部处理的船数：20/100/500/5000。不得大于 `expectedShipCount`。 |
| `pageSize` | 每次从名单表分页读取的船数；最大 1000。本手册使用 20 或 50。 |
| `parallelism` | 船级并行数；代码最多允许 2，本轮固定为 1，避免 HBase、Replay 和数据库瞬时压力。 |
| `startTime`/`endTime` | AIS 业务时间范围，左闭右开理解；必须 start < end，跨度不得超过 366 天。 |
| `windowDays` | 每个状态窗口 30 天；Loitering 与 Alongside 均强制为 30。 |
| `trajectorySliceHours` | 轨迹加载切片大小；Loitering 强制 6 小时。 |
| `engines` | Loitering 固定 `["V2"]`，使用保险 Scene 版本链路；Alongside 不需要该字段。 |
| `resume` | `true` 时跳过状态表中已完成窗口。续跑必须保持原参数和原 batchNo。 |
| `retryCount` | 单窗口内部失败重试次数，本轮为 2；不同于 XXL 调度中心整任务重试。 |
| `logIntervalShips` | 每处理多少艘船打印一次进度，本轮为 10。 |
| `batchNo` | 不可变运行身份，最大 64 字符。同 batchNo 改名单、时间、规模、写入模式、规则版本或 configHash 会拒绝 resume。 |
| `enableInsert` | `false` 为 dry-run；`true` 为正式事件写库。只有两条 5000 参数允许为 true。 |
| `storeDetails` | 是否保存额外事件轨迹详情。本轮全部为 false；Alongside 生产任务强制 event-only。 |

## 五、数据来源与写入位置

| 数据/结果 | 来源或目标 | 用途 |
| --- | --- | --- |
| 固定船舶名单 | `dataanalysis.ship_behavior_target_ship`，`list_batch_no=liquid-container-5000-2025h2-2026h1-v1` | 决定船舶顺序、MMSI 与 20/100/500/5000 范围。 |
| 主船历史 AIS | HBase `histyinfo`，由轨迹加载服务按时间切片读取 | Loitering 检测和 Alongside 主船低速窗口。 |
| Alongside 目标船 AIS | Spatial AIS Replay 服务 | 搜索同时间、同空间候选船并构造双船证据。 |
| 船型 | AIS41 船舶资料 | 主船商船资格和目标船角色分类。 |
| WORLD_SEA | `dataanalysis.ship_behavior_world_sea`，版本 `WORLD_SEA_102_V1R1` | 所有正式行为的命名海域 Gate，不代表法律公海。 |
| 港区 | `dataanalysis.zones_with_port_id` | `PORT_OPERATIONAL` 排除、最近港口与港界距离。 |
| 锚地 | `ais41.alertzones` | `ANCHORAGE` 排除。 |
| 批次清单 | `dataanalysis.ship_behavior_run_batch` | 保存规则版本、写入模式和 configHash，防止同名参数漂移。 |
| Loitering 状态 | `dataanalysis.ship_behavior_loitering_batch_status` | 每船每窗口 DONE/FAILED、点数与候选事件数。 |
| Alongside 状态 | `dataanalysis.ship_behavior_alongside_batch_status` | 每船每窗口、候选目标、Replay、失败与候选事件数。 |
| 正式事件 | `dataanalysis.ship_behavior_event` | dry-run 必须 0；5000 `enableInsert=true` 才写入。 |
| WORLD_SEA 审计 | `dataanalysis.ship_behavior_world_sea_audit` | strict pass/reject、Replay 失败与截断审计。 |

## 六、执行与判断

1. 在 XXL 控制台确认执行器在线，打开对应任务，核对 Handler、阻塞策略和单行参数，再点击一次“执行一次”。
   - 任务参数必须从左花括号 `{` 开始、右花括号 `}` 结束。
   - 不要粘贴 Markdown 代码围栏、`json` 标记、`Param:`、Handler 名称或外层 `params`。
   - 控制台参数框里不得换行；当前八条参数长度为 408–428 字符。
2. 日志出现 `Production Start` 后观察 `batchNo`、`shipLimit`、`parallelism`、`enableInsert` 是否与计划一致；不一致立即终止。
3. 成功日志应以 `Loitering Production Summary` 或 `Alongside Production Summary` 结束，且 `shipsFailed=0`、`windowsFailed=0`。Alongside 还要求 replay failure/truncated 为 0。
4. XXL 显示成功后仍必须执行 `query.sql`。dry-run 的候选事件数看状态表 `event_count`，`ship_behavior_event` 中该 batchNo 必须为 0。
5. 任何 FAILED、`SCENE_DATA_UNAVAILABLE`、`LOITERING_PORT_SCENE_UNAVAILABLE`、WORLD_SEA 加载失败、Replay 失败/截断、configHash 冲突都必须停止升级规模。

## 七、失败续跑

- 先用 `query.sql` 查询 FAILED 窗口和 `error_message`，修复数据、网络或配置根因。
- 使用原任务、原单行参数、原 `batchNo`、`resume=true` 再执行；已完成窗口会跳过。
- 不得改 batchNo 掩盖失败，不得把 dry-run 改成正式写库后继续原批次，不得同时启动另一行为任务。
- 若部署 JAR/配置错误，按 `manual-deployment.md` 回滚；不要删除状态表、审计表或既有事件。
- 出现 `LOITERING_PORT_SCENE_UNAVAILABLE` 时先停止任务，核对新 JAR SHA、港口参考数9,251、锚地2,945、港区8,764以及几何 PREVIEW/APPLY结果；不要通过修改 batchNo 绕过。

### 现有100船正式批次的受控恢复

`alongside-merchant100-2025-insurance-scene-v1` 已经以 `enableInsert=true` 建立不可变清单，因此本次只能用原参数续跑；这是历史批次恢复特例，不改变新建20/100/500测试必须 dry-run 的规则。
为避免将这条历史特例误当成新建任务模板，完整单行续跑参数只放在 `manual-deployment.md` 的当前批次恢复段落。

正常续跑应输出 `windowsSkipped=1296`、`windowsDone=4`、`windowsFailed=0`，状态表应为 `DONE=1300`、`FAILED=0`。如果出现 `replay_split_terminal` 且日志显示1海里范围内的10分钟查询仍达50,000点，保持该窗口 FAILED 并停止；不得使用部分轨迹写事件。
