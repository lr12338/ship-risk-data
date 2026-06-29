# 船舶徘徊与船舶搭靠：需求到实现映射文档

## 1. 本轮范围和前提

本轮按“船舶搭靠”和“船舶徘徊”理解需求；如果资料中出现“船舶搭建”，按笔误处理，真实含义记录为“船舶搭靠”。

本轮目标不是把最终算法规则一次写死，而是在当前 `ship-risk-data` 项目里建立可复用的轨迹计算框架：

- 复用现有 HBase 轨迹读取
- 复用现有多数据源和 `newDataanalysisJdbcTemplate` 落库
- 复用现有 XXL-Job 调度模式
- 新增徘徊、搭靠两个独立计算模块
- 抽出最小公共轨迹支撑层，避免后续更多轨迹类业务重复读 HBase、重复解析点位

## 2. 当前项目已经实现的能力

当前项目本质是“任务驱动的数据计算服务”，不是普通 CRUD 后台。

主链路可以理解为：

```text
XXL-Job handler
-> Spring Service
-> HBase / MySQL / PostgreSQL 读取源数据
-> Java 计算器处理轨迹或区域
-> Repository / StoreService 写回 dataanalysis
```

已存在的关键能力：

- `GetTrajectoryFromHbase`：从 HBase 表读取 AIS 历史轨迹。
- `AIS41GeneralQueryRepository`：从 `ais41.recentships` 读取需要参与计算的船舶列表。
- `DataSourceConfig` / `PostgreSQLDataSourceConfig`：配置 MySQL、PostgreSQL 等多数据源。
- `ZonePassageJob` / `ZoneCrossingStatsJob`：已有 XXL-Job 入口示例。
- `ZonePassageAnalysisService`：已有“轨迹 -> 区域计算 -> 落库 -> 进度记录”的完整服务链路。
- `ZoneCrossingStoreService` / `ZonePassageStatusRepository`：已有批量写库、断点状态记录写法。

这些能力说明：新增徘徊和搭靠不应该另起新程序，而应该放进当前 Spring Boot 项目，沿用已有 job / service / repository 模式。

## 3. 新增模块设计

### 3.1 公共轨迹支撑层

新增包：

```text
com.hifleet.trajectory
```

关键类：

- `TrajectoryPoint`：把 HBase 返回的 `NewShipForm` 字符串字段转换成统一轨迹点，包含 MMSI、时间、经纬度、航速、航向。
- `TrajectoryLoaderService`：统一读取 HBase 轨迹并转换为 `List<TrajectoryPoint>`。
- `TrajectorySegment`：表示连续轨迹片段，提供开始点、结束点、持续时间。
- `TrajectoryUtils`：提供时间格式化、两点距离、平均速度等小工具。

这样做的原因：

- 徘徊和搭靠都依赖轨迹点，不应各自解析 `NewShipForm`。
- 后续如果 HBase 读取规则变化，只需要改 `TrajectoryLoaderService`。
- 当前 `getOriginalVoffersFromHbase` 虽然名字像原始轨迹，但内部过滤了 `speed > 3` 的点；徘徊和搭靠会关注低速点，所以新行为计算默认使用 `getVoffersFromHbase(..., zoom=15)`。

### 3.2 船舶徘徊模块

新增类：

```text
com.hifleet.service.ShipLoiteringDetector
```

输入：

- 单船 MMSI
- 单船时间窗内轨迹点
- 徘徊规则参数

当前首版候选规则：

- 轨迹点航速不超过 `ship.behavior.loitering.max-speed`
- 连续低速片段持续时间不小于 `ship.behavior.loitering.min-duration-minutes`
- 片段活动半径不超过 `ship.behavior.loitering.max-radius-nm`
- 轨迹点数量不小于 `ship.behavior.loitering.min-points`

输出：

- `ShipBehaviorEvent`，`behavior_type=LOITERING`

### 3.3 船舶搭靠模块

新增类：

```text
com.hifleet.service.ShipAlongsideDetector
```

输入：

- 主船 MMSI
- 目标船 `targetMmsi`
- 两船同一时间窗内轨迹点
- 搭靠规则参数

当前首版候选规则：

- 两船轨迹点时间差不超过 `ship.behavior.alongside.max-time-gap-minutes`
- 两船距离不超过 `ship.behavior.alongside.max-distance-nm`
- 两船航速都不超过 `ship.behavior.alongside.max-speed`
- 连续片段持续时间不小于 `ship.behavior.alongside.min-duration-minutes`

输出：

- `ShipBehaviorEvent`，`behavior_type=ALONGSIDE`

首轮搭靠验证要求传入 `mmsi` 和 `targetMmsi`，不做全量两两匹配，避免服务器压力失控。

## 4. 接入位置

新增服务：

```text
com.hifleet.job.services.ShipBehaviorAnalysisService
```

职责：

- 解析 XXL-Job 参数
- 决定默认时间窗
- 调用 `TrajectoryLoaderService` 读取轨迹
- 调用徘徊或搭靠 detector
- 调用 `ShipBehaviorEventRepository` 落库
- 输出 XXL-Job 日志

新增 XXL-Job 入口：

```text
com.hifleet.xxljob.ShipBehaviorJob
```

handler：

- `shipLoiteringJobHandler`
- `shipAlongsideJobHandler`

新增落库表：

```text
dataanalysis.ship_behavior_event
```

建表脚本：

```text
src/main/resources/sql/ship_behavior_event.sql
```

## 5. 参数示例

徘徊首轮单船短时间窗：

```json
{"mmsi":"413123456","startTime":"2026-06-01 00:00:00","endTime":"2026-06-01 06:00:00","batchNo":"loitering-test-20260601"}
```

搭靠首轮两船短时间窗：

```json
{"mmsi":"413123456","targetMmsi":"413654321","startTime":"2026-06-01 00:00:00","endTime":"2026-06-01 06:00:00","batchNo":"alongside-test-20260601"}
```

也支持 key-value 参数：

```text
mmsi=413123456;startTime=2026-06-01 00:00:00;endTime=2026-06-01 06:00:00;batchNo=loitering-test-20260601
```

## 6. 本地验证方式

本地主要验证：

- Java 编译是否通过
- Maven 打包是否通过
- `target/ship-risk-data-0.0.1-SNAPSHOT.jar` 是否生成
- `deploy/package.sh` 是否能生成部署目录

本地不作为真实功能联调主路径，因为真实数据依赖 HBase、多套 MySQL、PostgreSQL 和 XXL-Job。

## 7. 服务器测试方案

首轮服务器验证必须小范围执行：

1. 在数据库执行 `src/main/resources/sql/ship_behavior_event.sql` 建表。
2. 本地打包生成 jar。
3. 上传 jar 到服务器 `/data/zhengyi`。
4. 保留服务器原 `application.properties`，必要时追加 `ship.behavior.*` 配置。
5. 重启服务。
6. 在 XXL-Job 新增或选择 handler：
   - 徘徊：`shipLoiteringJobHandler`
   - 搭靠：`shipAlongsideJobHandler`
7. 只用单船、6 小时以内时间窗触发。
8. 查看 XXL-Job 日志中的 `points=`、`events=`。
9. 查询 `dataanalysis.ship_behavior_event` 的对应 `batch_no`。

推荐 SQL：

```sql
SELECT behavior_type, mmsi, target_mmsi, start_time, end_time, duration_minutes, point_count, rule_version
FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'loitering-test-20260601'
ORDER BY start_time;
```

## 8. 回滚策略

代码回滚：

- 恢复上一版 jar，重启服务。
- XXL-Job handler 不触发即可停止新增计算。

数据回滚：

```sql
DELETE FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'loitering-test-20260601';
```

如果建表后暂时不用，也可以保留空表，不影响旧功能。

## 9. 后续迭代建议

建议按小版本推进：

- V1：完成当前候选链路，小范围验证 HBase 读取、计算、落库、XXL-Job 日志。
- V2：结合业务样本调整徘徊阈值，例如速度、半径、持续时间。
- V3：为搭靠补充候选目标船来源，避免人工传 `targetMmsi`。
- V4：如果需要全量搭靠，先设计空间索引或候选船筛选，不能直接全船两两匹配。
- V5：按业务最终定义拆分正式结果表或增加结果审核状态。
