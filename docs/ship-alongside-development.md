# 船舶搭靠模块开发文档

## 1. 模块目标

船舶搭靠模块用于识别“两条船在时间邻近、空间邻近、且都处于低速状态时形成的连续片段”，并将其输出为候选搭靠事件。

当前实现目标是先建立一条可测试、可落库、可追溯的链路，不追求一次性覆盖所有真实业务场景。

## 2. 任务入口与参数

XXL-Job Handler：

- `shipAlongsideJobHandler`

代码位置：

- [ShipBehaviorJob.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/xxljob/ShipBehaviorJob.java:33)

当前 JSON 参数格式：

```json
{
  "mmsi": "413433610",
  "targetMmsi": "413193000",
  "startTime": "2026-06-28 18:00:00",
  "endTime": "2026-06-30 09:40:00",
  "batchNo": "alongside-test-20260629-01"
}
```

字段说明：

- `mmsi`：主船 MMSI
- `targetMmsi`：目标船 MMSI
- `startTime`：分析开始时间
- `endTime`：分析结束时间
- `batchNo`：本次任务批次号

当前版本要求显式传入 `targetMmsi`，不做自动候选船搜索。

## 3. 当前代码链路

主链路如下：

```text
shipAlongsideJobHandler
-> ShipBehaviorAnalysisService.analysisAlongside
-> TrajectoryLoaderService.load(主船)
-> TrajectoryLoaderService.load(目标船)
-> ShipAlongsideDetector.detectWithTrace
-> ShipBehaviorEventRepository.saveEvents
-> ShipBehaviorEventDetailRepository.saveDetails
```

关键类：

- [ShipBehaviorAnalysisService.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/job/services/ShipBehaviorAnalysisService.java:120)
- [TrajectoryLoaderService.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/trajectory/TrajectoryLoaderService.java:1)
- [ShipAlongsideDetector.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ShipAlongsideDetector.java:1)

职责边界：

- service 负责组织两条船的轨迹
- detector 负责找“可搭靠连续片段”
- repository 负责结果落库和轨迹点快照落库

## 4. 当前规则说明

配置项位置：

- [application.properties](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/application.properties:138)

当前规则参数：

- `ship.behavior.alongside.max-distance-nm=0.1`
- `ship.behavior.alongside.max-speed=2.0`
- `ship.behavior.alongside.max-time-gap-minutes=10`
- `ship.behavior.alongside.min-duration-minutes=20`
- `ship.behavior.alongside.min-points=3`

当前判定逻辑：

1. 对主船每个点，在目标船轨迹中找时间上足够接近的最近点
2. 两点时间差不能超过阈值
3. 两点距离不能超过阈值
4. 两条船当时航速都必须足够低
5. 连续命中的片段点数、持续时间都要满足最小要求
6. 满足条件则生成 `ALONGSIDE` 事件

当前实现位置：

- [ShipAlongsideDetector.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ShipAlongsideDetector.java:29)

## 5. 当前输出与溯源方式

### 5.1 XXL-Job 摘要日志

示例：

```text
搭靠 mmsi=413433610 targetMmsi=413193000 ownPoints=42 targetPoints=46 events=5
```

含义：

- `ownPoints`：主船读到的点数
- `targetPoints`：目标船读到的点数
- `events`：命中的搭靠事件数

### 5.2 事件摘要日志

示例：

```text
event[1] type=ALONGSIDE start=2026-06-28 18:15:32 end=2026-06-28 19:35:46 duration=80 minDistanceNm=0.034082754387242675 maxDistanceNm=0.06167709661200095 avgSpeed=0.4 pointCount=5 targetMmsi=413193000 batchNo=alongside-test-20260629-01
```

这类日志用于快速判断：

- 起止时间是否合理
- 两船距离是否明显过大或过小
- 平均速度是否符合“靠泊/搭靠”预期
- 命中点数是否足够支撑事件成立

### 5.3 事件摘要表

- 表：`dataanalysis.ship_behavior_event`
- SQL： [ship_behavior_event.sql](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/sql/ship_behavior_event.sql:1)

### 5.4 事件点明细表

- 表：`dataanalysis.ship_behavior_event_detail`
- SQL： [ship_behavior_event_detail.sql](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/sql/ship_behavior_event_detail.sql:1)

搭靠场景下：

- `point_role=OWN` 表示主船点
- `point_role=TARGET` 表示目标船点

这点是搭靠模块排查时最重要的辅助信息。

### 5.5 运行时数据库连接说明

当前搭靠结果写库使用的是服务器运行时配置中的：

- `spring.datasource.newdataanalysis`

当前服务器已确认的运行时连接来源：

- 配置文件：`/data/zhengyi/application.properties`
- 运行时 RDS：
  - `rm-2zeen2o6509ka50l7.mysql.rds.aliyuncs.com:3306/dataanalysis`

推荐通过 `MySQL + SSH Tunnel` 连接：

- DB Host: `rm-2zeen2o6509ka50l7.mysql.rds.aliyuncs.com`
- DB Port: `3306`
- Database: `dataanalysis`
- DB User: `db55o2pytj4vx12b`
- SSH Host: `39.96.38.242`
- SSH Port: `1222`
- SSH User: `ecs-user`

如果本机直接连 RDS 超时，优先使用 SSH Tunnel，不要误判为数据库不存在。

## 6. 已验证测试样例

任务参数：

```json
{"mmsi":"413433610","targetMmsi":"413193000","startTime":"2026-06-28 18:00:00","endTime":"2026-06-30 09:40:00","batchNo":"alongside-test-20260629-01"}
```

已验证结果：

- 任务成功
- `ownPoints=42`
- `targetPoints=46`
- `events=5`
- 输出了 5 条 `event[i]` 摘要日志

示例日志：

```text
event[1] type=ALONGSIDE start=2026-06-28 18:15:32 end=2026-06-28 19:35:46 duration=80 minDistanceNm=0.034082754387242675 maxDistanceNm=0.06167709661200095 avgSpeed=0.4 pointCount=5 targetMmsi=413193000 batchNo=alongside-test-20260629-01
event[5] type=ALONGSIDE start=2026-06-29 21:38:18 end=2026-06-30 09:38:51 duration=720 minDistanceNm=0.016508198049456267 maxDistanceNm=0.025379694459744456 avgSpeed=0.16666666666666666 pointCount=9 targetMmsi=413193000 batchNo=alongside-test-20260629-01
```

## 7. 常见排查路径

### 7.1 一方点数不足

优先排查：

- 主船或目标船在该时间窗内是否本来就没有足够轨迹点
- 时间窗是否过短
- `TrajectoryLoaderService` 是否读空

### 7.2 `targetMmsi` 选错

如果目标船选错，典型表现是：

- `targetPoints` 很少甚至为 0
- `events=0`
- 明细表里只有主船点或根本没有明细

### 7.3 距离阈值过严

如果两船看起来已经非常接近但仍然没有命中，优先检查：

- `ship.behavior.alongside.max-distance-nm`

### 7.4 时间差过大导致无法配对

当前逻辑要求两船点之间时间差不能超过：

- `ship.behavior.alongside.max-time-gap-minutes`

如果两船 AIS 上报节奏不一致，容易因为时间差被过滤掉。

### 7.5 如何验证主船 / 目标船匹配

建议：

1. 先查事件摘要表，定位某个事件的时间窗
2. 再查明细表，按 `event_start_time + event_end_time` 查看
3. 按 `point_role` 分开看主船点和目标船点
4. 检查两边：
   - `point_time`
   - `lon/lat`
   - `speed`

## 8. 后续优化规则时改哪里

如果只是改搭靠识别规则，优先改：

- [ShipAlongsideDetector.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ShipAlongsideDetector.java:1)

如果要改：

- 日志输出
- 轨迹点快照内容
- `point_role` 落库方式
- 批次组织方式

则改：

- `ShipBehaviorAnalysisService`
- `ShipBehaviorEventDetailRepository`

如果想做“自动找候选目标船”：

- 这属于新需求
- 不应直接堆进当前 V1 的双船显式参数模式里
- 需要先设计候选船筛选策略，避免全量两两匹配

## 9. 推荐测试 SQL 与操作步骤

### 9.1 XXL-Job 操作

在任务调度中心：

- 执行器：`船舶事件分析`
- JobHandler：`shipAlongsideJobHandler`
- 任务参数填入上面的 JSON

### 9.2 查询事件摘要

```sql
SHOW TABLES LIKE 'ship_behavior%';

SELECT *
FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'alongside-test-20260629-01'
  AND mmsi = '413433610'
  AND behavior_type = 'ALONGSIDE'
ORDER BY start_time;
```

### 9.3 查询事件点明细

```sql
SELECT *
FROM dataanalysis.ship_behavior_event_detail
WHERE batch_no = 'alongside-test-20260629-01'
  AND mmsi = '413433610'
ORDER BY event_start_time, point_role, point_index;
```

如果这两张表查不到，先确认当前连接的是服务器运行时 `newdataanalysis`，而不是源码默认配置中的其他 `dataanalysis` 库。

### 9.4 删除本批次测试数据

```sql
DELETE FROM dataanalysis.ship_behavior_event_detail
WHERE batch_no = 'alongside-test-20260629-01';

DELETE FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'alongside-test-20260629-01';
```
