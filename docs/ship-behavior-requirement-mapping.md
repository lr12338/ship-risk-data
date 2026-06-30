# 船舶行为模块接入与实现总览

## 1. 文档目的

本文档面向后续接手 `ship-risk-data` 项目的开发人员，目标是快速说明：

- 船舶徘徊、船舶搭靠为什么放在当前项目里实现
- 这两个模块如何复用原项目的 HBase、数据库、XXL-Job 和部署链路
- 当前代码链路、配置项、落库表和测试方式分别在哪
- 后续如果只想优化徘徊规则或搭靠规则，应该从哪一层改起

如果资料中出现“船舶搭建”，按笔误理解为“船舶搭靠”。

## 2. 当前实现边界

本轮已经完成并验证成功的内容：

- 船舶徘徊任务接入 XXL-Job，并完成服务器测试
- 船舶搭靠任务接入 XXL-Job，并完成服务器测试
- 抽出公共轨迹支撑层，避免每个业务各自读取、解析 HBase 轨迹
- 结果摘要落库到 `dataanalysis.ship_behavior_event`
- 事件轨迹快照落库到 `dataanalysis.ship_behavior_event_detail`
- 结果表、明细表支持自动建表
- XXL-Job 日志支持输出每条事件摘要

本轮没有做的事情：

- 不做全量两两搭靠匹配
- 不做最终版业务规则固化
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

用途：

- 保存“事件级结果摘要”
- 便于按 `batch_no`、`mmsi`、时间窗查询命中的事件

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

### 6.3 自动建表机制

- 入口类： [ShipBehaviorTableInitializer.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/repository/ShipBehaviorTableInitializer.java:1)

当前行为：

- 应用启动时检查 `ship_behavior_event`
- 应用启动时检查 `ship_behavior_event_detail`
- 如果表不存在，则从 classpath SQL 资源读取 DDL 自动建表
- 如果表已存在，则跳过

## 7. 当前配置项

配置位置：

- [application.properties](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/application.properties:129)

### 7.1 公共开关

- `ship.behavior.store.enabled=true`
- `ship.behavior.store.batch-size=200`
- `ship.behavior.trace.store.enabled=true`
- `ship.behavior.trace.log-max-events=20`
- `ship.behavior.trajectory.simplify-zoom=15`
- `ship.behavior.default-hours=6`

### 7.2 徘徊规则参数

- `ship.behavior.loitering.max-speed=3.0`
- `ship.behavior.loitering.min-duration-minutes=30`
- `ship.behavior.loitering.max-radius-nm=1.0`
- `ship.behavior.loitering.min-points=3`

### 7.3 搭靠规则参数

- `ship.behavior.alongside.max-distance-nm=0.1`
- `ship.behavior.alongside.max-speed=2.0`
- `ship.behavior.alongside.max-time-gap-minutes=10`
- `ship.behavior.alongside.min-duration-minutes=20`
- `ship.behavior.alongside.min-points=3`

## 8. 当前验证结论

### 8.1 徘徊测试成功样例

任务参数：

```json
{"mmsi":"413193000","startTime":"2026-06-25 04:00:00","endTime":"2026-06-30 10:30:00","batchNo":"loitering-test-20260630-01"}
```

已验证结果：

- 任务成功执行
- `points=115`
- `events=2`
- 输出了 2 条 `event[i]` 摘要日志

### 8.2 搭靠测试成功样例

任务参数：

```json
{"mmsi":"413433610","targetMmsi":"413193000","startTime":"2026-06-28 18:00:00","endTime":"2026-06-30 09:40:00","batchNo":"alongside-test-20260629-01"}
```

已验证结果：

- 任务成功执行
- `ownPoints=42`
- `targetPoints=46`
- `events=5`
- 输出了 5 条 `event[i]` 摘要日志

## 9. 开发人员接手建议

### 9.1 哪些类属于公共层

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

### 9.2 哪些类属于单模块业务层

只影响徘徊：

- `ShipLoiteringDetector`

只影响搭靠：

- `ShipAlongsideDetector`

### 9.3 优先从哪层改规则

如果只是调整规则阈值或识别条件：

- 优先改 detector

如果是调整：

- 参数格式
- 日志输出
- `batch_no` 追踪方式
- 结果表 / 明细表写入方式

则优先改 service 或 repository。

### 9.4 需要特别避免的改法

- 不要在 detector 中直接写库
- 不要让 detector 直接依赖 HBase 原始对象
- 不要把搭靠扩展成全量两两匹配后直接上线
- 不要把明细表当成长期原始轨迹归档表使用

## 10. 推荐测试与排查入口

### 10.1 XXL-Job 入口

- 徘徊：`shipLoiteringJobHandler`
- 搭靠：`shipAlongsideJobHandler`

### 10.2 推荐查询 SQL

事件摘要：

```sql
SELECT *
FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'loitering-test-20260630-01'
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

## 11. 后续迭代建议

- V1 已完成：任务入口、轨迹读取、候选检测、结果落库、明细落库、自动建表、服务器小范围验证
- V2：结合业务样本调整徘徊和搭靠规则阈值
- V3：为搭靠设计“候选目标船筛选”，替代人工指定 `targetMmsi`
- V4：如果需要长期保留明细数据，再单独设计清理和归档策略
