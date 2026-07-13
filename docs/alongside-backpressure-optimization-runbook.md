# Alongside Flink 反压与 Kafka 堆积优化运行手册

更新时间：2026-07-09

本文用于处理 `reliable-alongside-flink` 搭靠实时识别作业出现上游高反压、Kafka Consumer Group `Messages Behind` 持续增长、Checkpoint state 变大或 MySQL 明细写入压力升高时的短期调参和巡检。

## 1. 当前判断

`LIVE_RECENTSHIPS_DATA` 当前只有 1 个 Kafka partition，因此 Kafka Source 同一时刻只有 1 个 subtask 能真正消费输入。单纯继续提高 Source 并发不能直接提升读取吞吐，优化重点应放在 keyBy 后的下游并行处理能力、状态大小和 DB 写入压力。

搭靠链路主要压力来自：

```text
低速 AIS 点
  -> alongside-low-speed-spatial-buckets
  -> 3x3 空间桶扩散，单点最多扩成 9 条
  -> alongside-spatial-candidate-recall
  -> 遍历 bucket TTL 状态召回候选 pair
  -> alongside-pair-detection
  -> Kafka / JDBC sink
```

短期不改代码时，优先使用以下手段：

| 优先级 | 手段 | 作用 |
| --- | --- | --- |
| 1 | 提高 `flink.parallelism` 到 `8` 或 `12` | 增强 keyBy 后候选召回和 pair detection 的并行处理能力 |
| 2 | 关闭 DB 明细或限制明细点数 | 降低 pair state、Checkpoint 和 MySQL detail 表写入压力 |
| 3 | 批量写 DB | 减少 JDBC 往返次数 |
| 4 | 新 group + `latest` | 只观察新作业实时处理能力，不混入旧 offset |
| 5 | 经纬度或 MMSI 范围过滤 | 在业务允许时直接降低输入量，效果最明显 |

不建议短期随意收紧 `candidate.max-distance-nm`、`candidate.max-time-gap-minutes`、`bucket-size-degrees` 等召回口径；这些会改变业务识别结果，应进入单独规则评估。

如果目标从“稳定处理当前规则”变成“只识别高匹配度商船搭靠事件”，不要在本手册里临时试错阈值，先按 [commercial-alongside-high-match-rule-design.md](commercial-alongside-high-match-rule-design.md) 的商船高匹配 profile 评估规则口径。

## 2. 推荐参数模板

### 2.1 实时优先模板：跳过旧堆积，只看启动后的最新 AIS

适用场景：验证当前作业是否能稳定跟上实时流量，避免旧历史 offset 影响 lag 判断。

每次部署请替换：

- `<RUN_ID>`：建议使用 `YYYYMMDDHHmm`，例如 `202607071530`。
- `<JDBC_URL>`、`<JDBC_USERNAME>`、`<JDBC_PASSWORD>`：沿用当前环境值。

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=LIVE_RECENTSHIPS_DATA
--kafka.group.id=alongside-web-db-event-latest-<RUN_ID>
--kafka.client.id=alongside-web-db-event-latest-<RUN_ID>
--kafka.starting-offsets=latest
--stop-point.enabled=false
--alongside.enabled=true
--alongside.output.topic=RELIABLE_ALONGSIDE_EVENTS_TEST
--alongside.db-sink.enabled=true
--alongside.db-sink.detail-enabled=false
--alongside.db-sink.url=<JDBC_URL>
--alongside.db-sink.username=<JDBC_USERNAME>
--alongside.db-sink.password=<JDBC_PASSWORD>
--alongside.db-sink.batch-size=100
--alongside.db-sink.flush-interval-ms=5000
--alongside.db-sink.max-detail-points-per-event=0
--alongside.db-sink.table-event=ship_realtime_alongside_event
--alongside.db-sink.auto-create-table=true
--alongside.db-sink.allowed-severity-levels=L2
--flink.parallelism=8
--flink.checkpoint.interval-ms=180000
--flink.checkpoint.timeout-ms=600000
--flink.checkpoint.min-pause-ms=60000
```

验收标准：

- Flink Job 为 `RUNNING`。
- Checkpoints `Failed=0`，`Completed` 持续增长。
- 最新 checkpoint duration 稳定低于 60 秒。
- Kafka 新 group 的 `Messages Behind` 在 15 到 30 分钟窗口内可短时增长，但应能周期性回落或保持可控。
- MySQL 摘要表仍有 `L2` 事件写入。

### 2.2 追历史模板：沿用已提交位点继续处理旧堆积

适用场景：业务要求不跳过历史 AIS，需要从已有 group committed offset 继续追 backlog。

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=LIVE_RECENTSHIPS_DATA
--kafka.group.id=<EXISTING_GROUP_ID>
--kafka.client.id=alongside-web-db-catchup-<RUN_ID>
--kafka.starting-offsets=committed
--stop-point.enabled=false
--alongside.enabled=true
--alongside.output.topic=RELIABLE_ALONGSIDE_EVENTS_TEST
--alongside.db-sink.enabled=true
--alongside.db-sink.detail-enabled=false
--alongside.db-sink.url=<JDBC_URL>
--alongside.db-sink.username=<JDBC_USERNAME>
--alongside.db-sink.password=<JDBC_PASSWORD>
--alongside.db-sink.batch-size=100
--alongside.db-sink.flush-interval-ms=5000
--alongside.db-sink.max-detail-points-per-event=0
--alongside.db-sink.table-event=ship_realtime_alongside_event
--alongside.db-sink.auto-create-table=true
--alongside.db-sink.allowed-severity-levels=L2
--flink.parallelism=8
--flink.checkpoint.interval-ms=180000
--flink.checkpoint.timeout-ms=600000
--flink.checkpoint.min-pause-ms=60000
```

注意：该模板会继续消费旧 offset，Kafka UI lag 会真实反映历史积压追赶进度；DB 中新写入或更新的事件可能对应较早 AIS 轨迹时间。

### 2.3 明细保留模板：仅保留少量 evidence 点

适用场景：仍需要在 DB detail 表里查看证据点，但要避免 `max-detail-points-per-event=200` 带来的状态和写库放大。

```text
--alongside.db-sink.detail-enabled=true
--alongside.db-sink.max-detail-points-per-event=20
--alongside.db-sink.batch-size=100
--alongside.db-sink.flush-interval-ms=5000
```

一个 evidence 点会写 OWN 和 TARGET 两行，因此 `max-detail-points-per-event=20` 时，每个事件最多约 40 行明细。若 MySQL 慢查询、锁等待或连接错误明显，应立刻切回：

```text
--alongside.db-sink.detail-enabled=false
--alongside.db-sink.max-detail-points-per-event=0
```

### 2.4 小范围验证模板：用区域过滤直接降低输入量

适用场景：只验证某个海域、港区或目标船，业务允许暂时区域化运行。

```text
--pipeline.scope.mmsi-whitelist=<MMSI1>,<MMSI2>
--pipeline.scope.min-lon=<MIN_LON>
--pipeline.scope.max-lon=<MAX_LON>
--pipeline.scope.min-lat=<MIN_LAT>
--pipeline.scope.max-lat=<MAX_LAT>
```

这是不改代码时降低输入量最有效的方式。空值表示不过滤。

## 3. 反压巡检方法

### 3.1 Flink Web

| 页面 | 重点观察 | 健康标准 |
| --- | --- | --- |
| Overview | `Backpressured` / `Busy` / records in-out | `keyed-ais-preprocess`、`alongside-spatial-candidate-recall` 不应长期接近 90% 到 100% backpressured |
| Exceptions | Root Exception / Exception History | 为空 |
| Checkpoints | Completed / Failed / Duration / State Size | `Failed=0`，`Completed` 增长，duration 明显低于 timeout |
| Configuration | Parallelism / checkpoint interval / timeout | 与提交模板一致 |
| Timeline | vertex 状态变化 | 没有长期 `DEPLOYING`、`FAILED`、`RESTARTING` |

### 3.2 Kafka UI

重点看当前运行 group，不要用旧测试 group 的 `Messages Behind` 判断当前作业压力。

| 字段 | 含义 |
| --- | --- |
| `Members` | 当前 group 是否有消费者成员。`0` 通常表示没有活跃消费者 |
| `State` | `STABLE` 表示有活跃消费；`EMPTY/DEAD` 表示没有活跃成员 |
| `Messages Behind` | group committed offset 与 topic end offset 的差值 |
| `Current Offset` | 已提交消费位点，应持续前进 |

旧 group 的 `Messages Behind` 只是历史 committed offset 与当前 topic end offset 的差值，不代表它正在消耗 Flink、Kafka 网络或 CPU。

### 3.3 MySQL

检查：

- 摘要表 `ship_realtime_alongside_event` 是否持续 upsert `L2` 事件。
- 长期运行时 `ship_realtime_alongside_event_detail` 不应继续增长；只有 legacy 明细压测模板会写入该表。
- 是否存在慢查询、锁等待、连接耗尽、唯一键冲突异常。

## 4. 升级规则

| 现象 | 下一步 |
| --- | --- |
| 并行度 8 后 `spatial-candidate-recall` 仍长期 high backpressure | 固定调整为 `--flink.parallelism=12`，其他参数不变 |
| 并行度 12 后仍长期 high backpressure | 不再盲目加并行度，进入代码级优化 |
| DB 写入成为瓶颈 | 关闭 detail，只保留摘要表 |
| 事件量仍过大，但 Flink/DB 已稳定 | 进入商船高匹配规则评估，收紧船型、距离、速度、持续时长、轨迹质量和输出事件类型 |
| Kafka lag 只在 checkpoint 前后波动，随后回落 | 可继续观察，不急于调整 |
| Kafka lag 15 到 30 分钟持续单调上升 | 复核反压、DB 写入、Checkpoint duration 和 topic partition |

代码级优化方向：

- 减少 3x3 bucket 扩散。
- 限制 bucket 内候选扫描数量。
- 引入更细粒度空间索引。
- 按港区或经纬度范围预过滤。
- 检查 MMSI、空间桶 key 是否存在明显倾斜。

## 5. 旧 Consumer Group 清理

只清理 offset 元数据，不删除 topic，不删除消息，不影响当前 Flink Job。

候选条件：

```text
groupId startsWith "alongside-"
AND members == 0
AND state in ("EMPTY", "DEAD")
AND groupId is not current running group
```

删除前必须逐个复核详情：

```bash
curl -sS "http://192.168.99.31:8067/api/clusters/h92/consumer-groups/<GROUP_ID>" | jq
```

删除命令：

```bash
curl -sS -X DELETE \
  "http://192.168.99.31:8067/api/clusters/h92/consumer-groups/<GROUP_ID>"
```

删除后列表复核：

```bash
curl -sS "http://192.168.99.31:8067/api/clusters/h92/consumer-groups?page=1&perPage=200" \
  | jq -r '.[] | select(.groupId | startswith("alongside-")) | [.groupId, .state, .members, .topics, .messagesBehind] | @tsv'
```

2026-07-07 已清理的旧 group：

```text
alongside-web-db-detail-20260707
alongside-web-db-detail-p8-20260707
alongside-web-db-event-latest-20260707-1
```

删除后三者详情接口显示 `state=DEAD`、`topics=0`、`messagesBehind=0`，列表中不再出现。

当前列表仍可见：

```text
alongside-web-db-event-latest-20260707-2
```

该 group 当前也是 `members=0/state=EMPTY`，如确认没有对应运行中的 Flink Job，可按同样流程清理。
