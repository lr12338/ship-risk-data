# 船舶徘徊模块开发文档

## 1. 模块目标

船舶徘徊模块用于识别“单船在一定时间窗内，低速、持续时间较长、活动半径较小”的轨迹片段，并输出为候选行为事件。

当前实现目标不是给出最终业务口径，而是先把：

- 任务入口
- 公共轨迹读取
- 规则检测
- 事件摘要落库
- 事件点快照落库
- 日志与排查链路

完整接进现有项目。

## 2. 任务入口与参数

XXL-Job Handler：

- `shipLoiteringJobHandler`

代码位置：

- [ShipBehaviorJob.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/xxljob/ShipBehaviorJob.java:19)

当前 JSON 参数格式：

```json
{
  "mmsi": "413193000",
  "startTime": "2026-06-25 04:00:00",
  "endTime": "2026-06-30 10:30:00",
  "batchNo": "loitering-test-20260630-01"
}
```

字段说明：

- `mmsi`：待分析船舶
- `startTime`：分析开始时间
- `endTime`：分析结束时间
- `batchNo`：本次任务批次号，后续查日志、查表、回滚都依赖它

也支持 key-value 形式：

```text
mmsi=413193000;startTime=2026-06-25 04:00:00;endTime=2026-06-30 10:30:00;batchNo=loitering-test-20260630-01
```

## 3. 当前代码链路

主链路如下：

```text
shipLoiteringJobHandler
-> ShipBehaviorAnalysisService.analysisLoitering
-> TrajectoryLoaderService.load
-> ShipLoiteringDetector.detectWithTrace
-> ShipBehaviorEventRepository.saveEvents
-> ShipBehaviorEventDetailRepository.saveDetails
```

关键类：

- [ShipBehaviorAnalysisService.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/job/services/ShipBehaviorAnalysisService.java:86)
- [TrajectoryLoaderService.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/trajectory/TrajectoryLoaderService.java:1)
- [ShipLoiteringDetector.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ShipLoiteringDetector.java:1)

职责边界：

- job handler 只负责接参数、返回执行结果
- service 负责轨迹读取、日志、落库、批次追踪
- detector 负责识别徘徊片段

## 4. 当前规则说明

配置项位置：

- [application.properties](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/application.properties:132)

当前规则参数：

- `ship.behavior.loitering.max-speed=3.0`
- `ship.behavior.loitering.min-duration-minutes=30`
- `ship.behavior.loitering.max-radius-nm=1.0`
- `ship.behavior.loitering.min-points=3`

当前判定逻辑：

1. 从轨迹中找连续低速点片段
2. 片段点数必须达到最小值
3. 片段持续时间必须达到最小值
4. 片段相对起点的最大活动半径不能超过阈值
5. 满足条件则生成 `LOITERING` 事件

当前实现位置：

- [ShipLoiteringDetector.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ShipLoiteringDetector.java:27)

## 5. 当前输出与溯源方式

### 5.1 XXL-Job 摘要日志

示例：

```text
徘徊 mmsi=413193000 points=115 events=2
```

含义：

- `points`：本次时间窗读到的轨迹点数
- `events`：命中的徘徊事件数

### 5.2 事件摘要日志

示例：

```text
event[1] type=LOITERING start=2026-06-25 04:00:00 end=2026-06-28 13:07:00 duration=4867 radiusNm=0.18986956962533177 avgSpeed=0.43863636363636355 pointCount=44 batchNo=loitering-test-20260630-01
```

这类日志用于快速判断：

- 事件起止时间是否合理
- 持续时间是否异常偏长或偏短
- 半径和平均速度是否符合预期
- 参与识别的点数是否偏少

### 5.3 事件摘要表

- 表：`dataanalysis.ship_behavior_event`
- SQL： [ship_behavior_event.sql](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/sql/ship_behavior_event.sql:1)

用于保存每条徘徊事件的摘要。

### 5.4 事件点明细表

- 表：`dataanalysis.ship_behavior_event_detail`
- SQL： [ship_behavior_event_detail.sql](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/resources/sql/ship_behavior_event_detail.sql:1)

用于保存构成事件的轨迹点快照。

徘徊场景下：

- `point_role` 只有 `OWN`

## 6. 已验证测试样例

任务参数：

```json
{"mmsi":"413193000","startTime":"2026-06-25 04:00:00","endTime":"2026-06-30 10:30:00","batchNo":"loitering-test-20260630-01"}
```

已验证结果：

- 任务成功
- `points=115`
- `events=2`
- 输出了 2 条 `event[i]` 摘要日志

对应日志样例：

```text
event[1] type=LOITERING start=2026-06-25 04:00:00 end=2026-06-28 13:07:00 duration=4867 radiusNm=0.18986956962533177 avgSpeed=0.43863636363636355 pointCount=44 batchNo=loitering-test-20260630-01
event[2] type=LOITERING start=2026-06-28 17:51:21 end=2026-06-30 10:29:59 duration=2438 radiusNm=0.1690104578377085 avgSpeed=0.15853658536585366 pointCount=41 batchNo=loitering-test-20260630-01
```

## 7. 常见排查路径

### 7.1 `points=0`

优先排查：

- `mmsi` 是否写错
- 时间窗内 HBase 是否确实有点
- `TrajectoryLoaderService` 是否读到了空结果

### 7.2 `events=0`

优先排查：

- 是否有足够低速点
- 低速片段是否连续
- 持续时间是否不够
- 半径是否超出阈值
- 点数是否不足

### 7.3 `duration / radius` 不符合预期

优先看：

- `event[i]` 摘要日志
- `ship_behavior_event` 中对应记录
- `ship_behavior_event_detail` 中该事件的 `point_index`、`point_time`

### 7.4 明细表有数据但事件看起来不合理

建议按以下顺序看：

1. 先按 `batch_no` 找到事件摘要
2. 再按 `event_start_time + event_end_time` 找点明细
3. 人工检查每个点的：
   - `speed`
   - `point_time`
   - `lon/lat`
4. 再回头检查 detector 条件是否过松或过严

## 8. 后续优化规则时改哪里

如果只是优化徘徊识别逻辑，优先改：

- [ShipLoiteringDetector.java](/Users/raymondlu/LocalProject/船舶风险评估数据服务/ship-risk-data/src/main/java/com/hifleet/service/ShipLoiteringDetector.java:1)

如果要改：

- 日志输出
- `batchNo` 组织方式
- 结果表字段
- 明细表结构
- 自动建表逻辑

则分别改：

- `ShipBehaviorAnalysisService`
- `ShipBehaviorEventRepository`
- `ShipBehaviorEventDetailRepository`
- `ShipBehaviorTableInitializer`

## 9. 推荐测试 SQL 与操作步骤

### 9.1 XXL-Job 操作

在任务调度中心：

- 执行器：`船舶事件分析`
- JobHandler：`shipLoiteringJobHandler`
- 任务参数填入上面的 JSON

### 9.2 查询事件摘要

```sql
SELECT *
FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'loitering-test-20260630-01'
  AND mmsi = '413193000'
  AND behavior_type = 'LOITERING'
ORDER BY start_time;
```

### 9.3 查询事件点明细

```sql
SELECT *
FROM dataanalysis.ship_behavior_event_detail
WHERE batch_no = 'loitering-test-20260630-01'
  AND mmsi = '413193000'
ORDER BY event_start_time, point_index;
```

### 9.4 删除本批次测试数据

```sql
DELETE FROM dataanalysis.ship_behavior_event_detail
WHERE batch_no = 'loitering-test-20260630-01';

DELETE FROM dataanalysis.ship_behavior_event
WHERE batch_no = 'loitering-test-20260630-01';
```
