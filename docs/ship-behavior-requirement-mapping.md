# 船舶行为模块接入与实现总览

## 1. 文档目的

本文档面向后续接手 `ship-risk-data` 项目的开发人员，目标是快速说明：

- 船舶徘徊、船舶搭靠为什么放在当前项目里实现
- 这两个模块如何复用原项目的 HBase、数据库、XXL-Job 和部署链路
- 当前代码链路、配置项、落库表和测试方式分别在哪
- 后续如果只想优化徘徊规则或搭靠规则，应该从哪一层改起

如果资料中出现“船舶搭建”，按笔误理解为“船舶搭靠”。

## 2. 当前实现边界

当前已经完成并验证成功的内容：

- 船舶徘徊任务接入 XXL-Job，并完成 V2-lite 单船服务器测试
- 船舶搭靠任务接入 XXL-Job，并完成服务器测试
- 抽出公共轨迹支撑层，避免每个业务各自读取、解析 HBase 轨迹
- 结果摘要落库到 `dataanalysis.ship_behavior_event`
- 事件轨迹快照落库到 `dataanalysis.ship_behavior_event_detail`
- 结果表、明细表支持自动建表，事件摘要表支持 V2-lite 字段自动补列
- XXL-Job 日志支持输出 V2 徘徊任务摘要和每条事件摘要

本轮没有做的事情：

- 不做全量两两搭靠匹配
- 不做最终版业务规则固化
- 不接入港口、锚地、天气、制裁区、STS 热点等外部场景数据
- 不做年度 WLI 汇总表落库
- 不做长期归档清理策略
- 不新增独立服务或新仓库

## 3. 为什么接入当前项目

当前项目本质是“任务驱动的数据计算服务”，已有稳定链路：

```text
XXL-Job Handler
-> AnalysisService
-> HBase / MySQL / PostgreSQL 读取
-> Java 计算
-> Repository 写回 dataanalysis
```

船舶徘徊、船舶搭靠都符合这条链路，因此直接接入当前项目最稳，原因有三点：

1. 已有 HBase 轨迹读取能力，不需要重复造读取层
2. 已有多数据源和 `newDataanalysisJdbcTemplate`，可直接落库
3. 已有 XXL-Job 执行器、打包脚本、服务器部署方式，便于延续现有运维模式

## 4. 当前模块清单

### 4.1 任务入口

- [ShipBehaviorJob.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/xxljob/ShipBehaviorJob.java:1)
  - `shipLoiteringJobHandler`
  - `shipAlongsideJobHandler`

职责：

- 接收 XXL-Job 参数
- 调用行为分析 service
- 返回 `processed / failed / events` 执行摘要

### 4.2 业务服务层

- [ShipBehaviorAnalysisService.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/job/services/ShipBehaviorAnalysisService.java:1)

职责：

- 解析 JSON 或 key-value 任务参数
- 决定默认时间窗
- 读取轨迹
- 调用徘徊 / 搭靠 detector
- 写事件摘要表
- 写事件轨迹快照明细表
- 输出 XXL-Job 事件摘要日志

### 4.3 公共轨迹支撑层

目录：

```text
src/main/java/com/hifleet/trajectory
```

关键类：

- `TrajectoryPoint`
- `TrajectoryLoaderService`
- `TrajectorySegment`
- `TrajectoryUtils`

作用：

- 统一把 HBase 轨迹转换成业务可用的点模型
- 避免 detector 直接依赖 HBase 原始字符串对象

### 4.4 规则检测层

- [ShipLoiteringDetector.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ShipLoiteringDetector.java:1)
- [ShipAlongsideDetector.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ShipAlongsideDetector.java:1)

当前职责边界：

- detector 负责“识别事件”
- service 负责“参数处理、日志、落库、批次追踪”

这是后续继续演进规则时最重要的分层约定。

### 4.5 结果落库层

- [ShipBehaviorEventRepository.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/repository/ShipBehaviorEventRepository.java:1)
  - 写事件摘要表 `ship_behavior_event`
- [ShipBehaviorEventDetailRepository.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/repository/ShipBehaviorEventDetailRepository.java:1)
  - 写事件轨迹快照表 `ship_behavior_event_detail`
- [ShipBehaviorTableInitializer.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/repository/ShipBehaviorTableInitializer.java:1)
  - 自动检查并创建两张表
  - 自动检查并补齐 `ship_behavior_event` 的 V2-lite 必需字段

### 4.6 中间 DTO

- [ShipBehaviorTrace.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/domain/ShipBehaviorTrace.java:1)

作用：

- 把“事件摘要 + 命中的轨迹点快照”一起从 detector 传到 service
- 避免 repository 再去回推事件对应的轨迹点

## 5. 原项目接入路径

当前两类行为任务都走同一条主路径：

```text
shipLoiteringJobHandler / shipAlongsideJobHandler
-> ShipBehaviorAnalysisService
-> TrajectoryLoaderService
-> ShipLoiteringDetector / ShipAlongsideDetector
-> ShipBehaviorEventRepository
-> ShipBehaviorEventDetailRepository
-> dataanalysis.ship_behavior_event / ship_behavior_event_detail
```

其中复用的原项目能力主要有：

- HBase 轨迹读取：`GetTrajectoryFromHbase`
- 最近船舶列表读取：`AIS41GeneralQueryRepository`
- 多数据源：`DataSourceConfig`
- MySQL dataanalysis 写库：`newDataanalysisJdbcTemplate`
- XXL-Job 执行器注册、部署、服务重启脚本

## 6. 数据落库设计

### 6.1 事件摘要表

- SQL： [ship_behavior_event.sql](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/sql/ship_behavior_event.sql:1)
- 表名：`dataanalysis.ship_behavior_event`

主要字段：

- `behavior_type`
- `mmsi`
- `target_mmsi`
- `start_time`
- `end_time`
- `duration_minutes`
- `min_distance_nm`
- `max_distance_nm`
- `avg_speed`
- `point_count`
- `rule_version`
- `batch_no`
- `event_sub_type`
- `severity_level`
- `event_score`
- `confidence_level`
- `exclusion_flag`
- `exclusion_reason`
- `ais_gap_count`
- `max_ais_gap_minutes`
- `track_coverage_ratio`
- `turn_count_per_hour`
- `cog_change_per_hour`
- `tortuosity`
- `center_lon`
- `center_lat`
- `radius_nm`
- `score_detail_json`

用途：

- 保存“事件级结果摘要”
- 便于按 `batch_no`、`mmsi`、时间窗查询命中的事件
- 徘徊 V2-lite 中保存子类型、置信度、评分、AIS gap、覆盖率、转向和半径等指标

### 6.2 事件轨迹快照明细表

- SQL： [ship_behavior_event_detail.sql](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/sql/ship_behavior_event_detail.sql:1)
- 表名：`dataanalysis.ship_behavior_event_detail`

主要字段：

- `behavior_type`
- `mmsi`
- `target_mmsi`
- `point_role`
- `event_start_time`
- `event_end_time`
- `point_time`
- `point_index`
- `lon`
- `lat`
- `speed`
- `course`
- `nav_status`
- `batch_no`
- `rule_version`

用途：

- 保存构成事件的轨迹点快照
- 便于按 `batch_no + 事件时间窗` 追踪每一个点
- 搭靠任务中通过 `point_role=OWN/TARGET` 区分主船点和目标船点

### 6.3 自动建表与补列机制

- 入口类： [ShipBehaviorTableInitializer.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/repository/ShipBehaviorTableInitializer.java:1)

当前行为：

- 应用启动时检查 `ship_behavior_event`
- 应用启动时检查 `ship_behavior_event_detail`
- 如果表不存在，则从 classpath SQL 资源读取 DDL 自动建表
- 如果 `ship_behavior_event` 表已存在，则继续逐列检查 V2-lite 必需字段
- 缺失字段会逐列执行 `ALTER TABLE ... ADD COLUMN`
- 任一必需字段补齐失败，应用启动失败，避免后续任务写库时报运行时错误
- `ship_behavior_event_detail` 本轮只保持自动建表，不做补列

启动日志关键字：

```text
ship_behavior_event exists, skip ddl
ship_behavior_event column missing, applying migration: event_sub_type
ship_behavior_event column ready: event_sub_type
ship_behavior_event schema ready
```

## 7. 当前配置项

配置位置：

- [application.properties](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/application.properties:129)

注意：

- 源码里的 `src/main/resources/application.properties` 只是默认配置
- 线上以服务器 `/data/zhengyi/application.properties` 为准
- 本模块排查数据库时，必须先确认服务器运行时配置，不要直接假定源码默认值就是线上值

### 7.1 公共开关

- `ship.behavior.store.enabled=true`
- `ship.behavior.store.batch-size=200`
- `ship.behavior.trace.store.enabled=true`
- `ship.behavior.trace.log-max-events=20`
- `ship.behavior.trajectory.simplify-zoom=15`
- `ship.behavior.default-hours=6`

### 7.2 徘徊规则参数

- `ship.behavior.loitering.rule-version=LOITERING_V2_INSURANCE`
- `ship.behavior.loitering.max-gap-minutes=30`
- `ship.behavior.loitering.event-split-gap-minutes=120`
- `ship.behavior.loitering.max-valid-speed=80.0`
- `ship.behavior.loitering.slow.max-speed=2.0`
- `ship.behavior.loitering.slow.min-duration-minutes=240`
- `ship.behavior.loitering.slow.max-radius-nm=3.0`
- `ship.behavior.loitering.slow.min-points=12`
- `ship.behavior.loitering.maneuver.min-speed=0.5`
- `ship.behavior.loitering.maneuver.max-speed=5.0`
- `ship.behavior.loitering.maneuver.min-duration-minutes=120`
- `ship.behavior.loitering.maneuver.max-radius-nm=5.0`
- `ship.behavior.loitering.maneuver.min-turn-count-per-hour=3`
- `ship.behavior.loitering.maneuver.min-cog-change-per-hour=120`
- `ship.behavior.loitering.maneuver.min-tortuosity=3.0`
- `ship.behavior.loitering.high-risk.enabled=false`

### 7.3 搭靠规则参数

当前搭靠版本为 `ALONGSIDE_V2_INSURANCE`。保留 V1 兼容配置，同时新增 candidate、confirm、heading、merge、severity cap 等保险化配置。

兼容旧配置：

- `ship.behavior.alongside.max-distance-nm=0.1`
- `ship.behavior.alongside.max-speed=2.0`
- `ship.behavior.alongside.max-time-gap-minutes=10`
- `ship.behavior.alongside.min-duration-minutes=20`
- `ship.behavior.alongside.min-points=3`

V2 配置：

- `ship.behavior.alongside.rule-version=ALONGSIDE_V2_INSURANCE`
- `ship.behavior.alongside.mode.auto.enabled=true`
- `ship.behavior.alongside.candidate.max-distance-nm=0.5`
- `ship.behavior.alongside.candidate.max-speed=4.0`
- `ship.behavior.alongside.candidate.time-bucket-minutes=5`
- `ship.behavior.alongside.candidate.min-points=3`
- `ship.behavior.alongside.candidate.min-duration-minutes=20`
- `ship.behavior.alongside.candidate.split-gap-minutes=30`
- `ship.behavior.alongside.confirm.max-distance-nm=0.15`
- `ship.behavior.alongside.confirm.max-speed=2.0`
- `ship.behavior.alongside.confirm.max-relative-speed=1.0`
- `ship.behavior.alongside.confirm.max-time-gap-minutes=10`
- `ship.behavior.alongside.confirm.high-confidence-time-gap-minutes=5`
- `ship.behavior.alongside.confirm.min-duration-minutes=30`
- `ship.behavior.alongside.confirm.min-points=6`
- `ship.behavior.alongside.heading.enabled=true`
- `ship.behavior.alongside.heading.parallel-threshold-degree=30`
- `ship.behavior.alongside.merge.enabled=true`
- `ship.behavior.alongside.merge.max-gap-minutes=30`
- `ship.behavior.alongside.merge.max-center-distance-nm=1.0`
- `ship.behavior.alongside.score.weight=0.20`
- `ship.behavior.alongside.severity-cap.no-scene-data.max-level=L2`
- `ship.behavior.alongside.scene-data.available=false`
- `ship.behavior.alongside.counterparty-risk.available=false`
- `ship.behavior.alongside.auto-l4-enabled=false`

## 8. 服务器运行时数据库说明

### 8.1 关键结论

当前船舶徘徊、船舶搭靠结果写库使用的是：

- `newDataanalysisJdbcTemplate`
- 对应服务器运行时配置：`spring.datasource.newdataanalysis.*`

这点非常重要，因为服务器运行时配置与源码默认值不同。

### 8.2 当前服务器运行时配置

部署目录：

- `/data/zhengyi`

运行时配置文件：

- `/data/zhengyi/application.properties`

已确认的运行时写库地址：

- JDBC URL：
  - `jdbc:mysql://rm-2zeen2o6509ka50l7.mysql.rds.aliyuncs.com:3306/dataanalysis?...`
- 用户名：
  - `db55o2pytj4vx12b`
- 密码：
  - 以服务器运行时配置为准

同时服务器还存在另一套：

- `spring.datasource.dataanalysis`

但本模块当前结果写入的是：

- `spring.datasource.newdataanalysis`

### 8.3 为什么本地源码里的库地址不能直接当成线上地址

源码默认值里 `newdataanalysis` 指向的是另一套 MySQL。  
但服务器 `/data/zhengyi/application.properties` 已覆盖为新的 RDS 地址。

因此：

- 本地看源码配置，只能作为默认参考
- 真正查库时，必须以服务器运行时配置为准

### 8.4 如何在 DBeaver 连接运行时数据库

推荐使用 `MySQL + SSH Tunnel`：

1. 数据库连接信息：
   - Host: `rm-2zeen2o6509ka50l7.mysql.rds.aliyuncs.com`
   - Port: `3306`
   - Database: `dataanalysis`
   - Username: `db55o2pytj4vx12b`
   - Password: 服务器运行时配置中的密码
2. SSH Tunnel 信息：
   - SSH Host: `39.96.38.242`
   - SSH Port: `1222`
   - SSH User: `ecs-user`
   - SSH Password: 服务器登录密码

如果本机直连超时，通常说明：

- 该 RDS 不能直接从本机访问
- 需要通过部署服务器做 SSH 隧道转发

### 8.5 如何确认自己没有连错库

连接成功后先执行：

```sql
SHOW TABLES LIKE 'ship_behavior%';
```

正常应看到：

- `ship_behavior_event`
- `ship_behavior_event_detail`

如果没有这两张表：

- 先不要继续查业务数据
- 优先确认当前 DBeaver 连接的 host / port 是否就是服务器运行时 `newdataanalysis`

## 9. 当前验证结论

### 9.1 徘徊 V2-lite 测试成功样例

任务参数：

```json
{"mmsi":"413193000","startTime":"2026-06-25 04:00:00","endTime":"2026-06-30 10:30:00","batchNo":"loitering-test-20260630-01"}
```

已验证结果：

- 任务成功执行
- `points=115`
- `cleanPoints=115`
- `segments=23`
- `candidates=1`
- `events=1`
- `failed=0`
- 输出了 1 条 V2 `event[i]` 摘要日志

对应日志样例：

```text
LOITERING_V2 start mmsi=413193000 startTime=2026-06-25 04:00:00 endTime=2026-06-30 10:30:00 batchNo=loitering-test-20260630-01 ruleVersion=LOITERING_V2_INSURANCE
event[1] type=LOITERING subType=SLOW_LOITERING severity=L1 confidence=MEDIUM start=2026-06-29 19:46:08 end=2026-06-30 04:39:15 duration=533 radiusNm=0.17389501702977303 avgSpeed=0.2076923076923077 pointCount=13 maxGap=114 coverage=0.7222222222222222 score=24.0 exclusion=DOWNGRADED_NORMAL_LIKELY reason=NAV_STATUS_ANCHORED_OR_MOORED batchNo=loitering-test-20260630-01
LOITERING_V2 summary mmsi=413193000 points=115 cleanPoints=115 segments=23 candidates=1 events=1 excludedEvents=1 lowConfidenceEvents=0 batchNo=loitering-test-20260630-01
```

本次判断说明：

- 命中 `SLOW_LOITERING`
- `maxGap=114` 未超过切分阈值 `120`，但超过高置信阈值 `30`
- `coverage=0.7222`，因此置信度为 `MEDIUM`
- `nav_status` 命中锚泊/系泊，降权为 `DOWNGRADED_NORMAL_LIKELY`
- 最终 `event_score=24.0`，等级 `L1`
- V2 没有把多天稀疏 AIS 点合并成高置信超长事件

### 9.2 搭靠测试成功样例

任务参数：

```json
{"mmsi":"413433610","targetMmsi":"413193000","startTime":"2026-06-28 18:00:00","endTime":"2026-06-30 09:40:00","batchNo":"alongside-test-20260629-01"}
```

历史服务器样例已验证结果：

- 任务成功执行
- `ownPoints=42`
- `targetPoints=46`
- `events=5`
- 输出了 5 条 `event[i]` 摘要日志

当前源码版本已升级为 `ALONGSIDE_V2_INSURANCE`：

- PAIR 模式仍支持上述参数，继续读取主船和目标船轨迹并落库
- 事件会额外写入 `event_sub_type`、`severity_level`、`event_score`、`confidence_level`、`exclusion_flag`、`exclusion_reason`、`score_detail_json`
- 日志升级为 `ALONGSIDE_V2 start / event[i] / summary`
- AUTO 模式支持不传 `targetMmsi`，当前会读取主船轨迹、提取低速窗口，并基于 `getRecentAis` 区域回放接口按 bbox 召回候选目标船
- AUTO 候选召回链路为 `ShipAlongsideReplayCandidateProvider -> SpatialAisReplayClient -> ReplayAisPointParser -> ShipAlongsideDetector`
- AUTO 会输出 `low_speed_summary`、`replay_query`、`candidate_summary` 和时间窗年度指标日志
- 如果 replay 接口返回空结果或没有目标船满足预过滤规则，`candidateTargets=0` 是正常结果，但不再是固定空候选

## 10. 开发人员接手建议

### 10.1 哪些类属于公共层

以下类会同时影响徘徊和搭靠：

- `TrajectoryLoaderService`
- `TrajectoryPoint`
- `TrajectorySegment`
- `TrajectoryUtils`
- `ShipBehaviorAnalysisService`
- `ShipBehaviorEventRepository`
- `ShipBehaviorEventDetailRepository`
- `ShipBehaviorTableInitializer`
- `ShipBehaviorTrace`

如果修改这些类，要意识到可能同时影响两个模块。

### 10.2 哪些类属于单模块业务层

只影响徘徊：

- `ShipLoiteringDetector`

只影响搭靠：

- `ShipAlongsideDetector`

### 10.3 优先从哪层改规则

如果只是调整规则阈值或识别条件：

- 优先改 detector

如果是调整：

- 参数格式
- 日志输出
- `batch_no` 追踪方式
- 结果表 / 明细表写入方式

则优先改 service 或 repository。

### 10.4 需要特别避免的改法

- 不要在 detector 中直接写库
- 不要让 detector 直接依赖 HBase 原始对象
- 不要把搭靠扩展成全量两两匹配后直接上线
- 不要把明细表当成长期原始轨迹归档表使用
- 不要把 `ship_behavior_event_v2_loitering_alter.sql` 整包作为启动 SQL 执行；程序内已经按列自动补齐，整包 ALTER 在部分字段已存在时会失败

## 11. 推荐测试与排查入口

### 11.1 XXL-Job 入口

- 徘徊：`shipLoiteringJobHandler`
- 搭靠：`shipAlongsideJobHandler`

### 11.2 推荐查询 SQL

事件摘要：

```sql
SELECT
  id,
  behavior_type,
  mmsi,
  start_time,
  end_time,
  duration_minutes,
  event_sub_type,
  severity_level,
  event_score,
  confidence_level,
  exclusion_flag,
  exclusion_reason,
  ais_gap_count,
  max_ais_gap_minutes,
  track_coverage_ratio,
  radius_nm,
  score_detail_json,
  batch_no
FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'loitering-test-20260630-01'
  AND behavior_type = 'LOITERING'
ORDER BY start_time;
```

事件点明细：

```sql
SELECT *
FROM dataanalysis.ship_behavior_event_detail
WHERE batch_no = 'loitering-test-20260630-01'
  AND mmsi = '413193000'
ORDER BY event_start_time, point_role, point_index;
```

## 12. 后续迭代建议

- V2-lite 已完成：任务入口、轨迹读取、清洗、AIS gap 切分、候选检测、置信度、评分、结果落库、明细落库、自动建表补列、服务器单船验证
- 后续 V2：结合业务样本调整阈值，接入锚地、港口、天气、制裁区、STS 热点等外部数据
- V3：为搭靠设计“候选目标船筛选”，替代人工指定 `targetMmsi`
- V4：如果需要长期保留明细数据，再单独设计清理和归档策略
