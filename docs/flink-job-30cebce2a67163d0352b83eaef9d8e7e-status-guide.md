# Flink Job 运行状态排查报告

排查对象：

```text
Flink Web: http://192.168.99.72:9081
Job Name: reliable-alongside-flink
Job ID: 30cebce2a67163d0352b83eaef9d8e7e
排查时间: 2026-07-07 10:33 CST
Flink Version: 1.16.0
```

## 0. 后续优化执行记录

2026-07-07 后续已按本报告中的反压结论完成一次短期调参部署：

| 项 | 旧值 | 新值 |
| --- | --- | --- |
| 旧 Job | `30cebce2a67163d0352b83eaef9d8e7e` | 已取消 |
| 新 Job | - | `209bcf37151779e31f1c36fc758b4615` |
| `kafka.group.id` | `alongside-web-db-detail-20260706` | `alongside-web-db-detail-p8-20260707` |
| `flink.parallelism` | `2` | `8` |
| `alongside.db-sink.max-detail-points-per-event` | `200` | `20` |
| `alongside.db-sink.batch-size` | `20` | `100` |
| `alongside.db-sink.flush-interval-ms` | `3000` | `5000` |
| `flink.checkpoint.timeout-ms` | `300000` 或集群默认 | `600000` |

验证结果：

- 新 Job `209bcf37151779e31f1c36fc758b4615` 进入 `RUNNING`，32 个 task 全部运行。
- 连续 3 次 Checkpoint 成功，失败 0 次，最新耗时约 18.7 秒。
- 最终采样中 `keyed-ais-preprocess`、`alongside-spatial-candidate-recall`、`alongside-pair-detection + sinks` 均恢复为 `ok`。
- Kafka 新 group lag 在 Checkpoint 周期内短时波动，Checkpoint 提交后可回落。
- 已清理旧测试 consumer group：`alongside-local-group-20260703091758`、`alongside-local-group-20260703092114`、`alongside-local-group-20260703092410`、`alongside-local-group-20260703093410`、`alongside-local-group-20260703095432`、`alongside-realtime-db-test-group`、`alongside-web-db-detail-20260706`。
- 2026-07-07 继续清理：`alongside-web-db-detail-20260707`、`alongside-web-db-detail-p8-20260707`、`alongside-web-db-event-latest-20260707-1`。删除前均为 `members=0/state=EMPTY`；删除后详情接口返回 `state=DEAD/topics=0/messagesBehind=0`，列表中不再出现。

注意：Kafka UI 中 `members=0/state=EMPTY` 的 Flink KafkaSource group 不一定表示 Job 未消费；应结合 Flink Source 记录数、Checkpoint 和 group committed offset 是否前进判断。旧测试 group 的 `Messages Behind` 只是历史 offset 与当前 topic end offset 的差值，不代表仍有计算资源消耗。

## 1. 当前结论

当前 Job 处于可用运行状态：

| 检查项 | 当前结果 | 判断 |
| --- | --- | --- |
| Job 状态 | `RUNNING` | 正常 |
| Job 启动时间 | 2026-07-07 10:24:23 | 已连续运行约 9 分钟以上 |
| 作业拓扑 | 4 个 vertex，8 个 subtask | 全部运行中 |
| 异常页面 | Root Exception 为空，Exception History 为空 | 未发现失败异常 |
| Checkpoint | 触发 3 次，完成 3 次，失败 0 次 | 状态恢复链路正常 |
| TaskManager | 2 个在线，100 个 slot，98 个空闲 | 集群资源充足 |
| 配置 | PIPELINED，作业并行度 2，Checkpoint 间隔 180s，超时 600s，EXACTLY_ONCE | 与实时流作业预期一致 |
| 反压 | Source / preprocess / candidate recall 为 high，末端 pair detection + sink 为 ok | 作业未失败，但存在持续反压，需要持续观察和优化 |

简要判断：

1. 服务没有失败、取消、重启或异常堆栈，Checkpoint 也正常完成，因此不能判定为故障。
2. 当前主要风险是上游算子链存在明显反压，说明数据流某些位置消费速度或网络传输存在压力。
3. 末端 `alongside-pair-detection + Kafka/JDBC sink` 显示反压为 `ok`，但它的 busy 比例接近满载，仍建议继续观察 sink 写入耗时、JDBC 批量写入和 Kafka topic 消费/生产指标。
4. 页面上的 Low Watermark 显示到 2026-07-07 18:30 左右，明显晚于作业启动机器时间 10:24；这通常不是 Flink 运行失败信号，但需要确认输入 AIS `updatetime` 是否是回放数据、未来时间或时区转换差异。

## 2. 关键页面怎么读

### 2.1 顶部 Job 摘要

顶部区域用于回答“这个作业是否还活着”。

| 字段 | 含义 | 当前值 | 如何判断 |
| --- | --- | --- | --- |
| Job State | 作业生命周期状态 | `RUNNING` | `RUNNING` 表示正在运行；`FAILED`、`CANCELED`、`RESTARTING` 需要重点排查 |
| Job ID | 作业唯一 ID | `30cebce2a67163d0352b83eaef9d8e7e` | 排查日志、Checkpoint、REST 接口时都用它定位 |
| Start Time | 作业启动时间 | 2026-07-07 10:24:23 | 判断是否刚启动、是否频繁重启 |
| Duration | 已运行时长 | 约 9 分钟以上 | 若 Duration 重置，说明作业可能重启过 |
| Actions / Cancel Job | 作业操作入口 | 可取消作业 | 排查时不要误点 Cancel |

本次顶部状态结论：Job 仍在运行，未看到失败或重启状态。

### 2.2 Overview 页面

Overview 是日常巡检最重要的页面。它同时展示拓扑图和 vertex 表格。

#### 拓扑图

当前拓扑是 4 段：

```text
Source: ais-kafka-source
  -> parse-ais-payload
  -> filter-valid-ais
  -> filter-ais-scope
  -> assign-ais-event-time

keyed-ais-preprocess
  -> alongside-pre-filter
  -> alongside-low-speed-spatial-buckets

alongside-spatial-candidate-recall

alongside-pair-detection
  -> kafka-alongside-event-sink: Writer
  -> kafka-alongside-event-sink: Committer
  -> jdbc-alongside-event-sink
```

每个节点重点看 4 类信息：

| 信息 | 含义 | 当前观察 |
| --- | --- | --- |
| Parallelism | 该算子链并行度 | 全部为 2 |
| Backpressured | 该算子因下游处理慢而被阻塞的比例 | 上游三段较高 |
| Busy | 算子真正忙于计算的比例 | 末端 pair detection + sink 接近满载 |
| Low Watermark | 当前事件时间进度的最低值 | 显示约 2026-07-07 18:30 |

当前 Overview 关键值：

| Vertex | 状态 | 输入记录 | 输出记录 | 页面反压观察 |
| --- | --- | ---: | ---: | --- |
| Source + parse/filter/watermark | `RUNNING` | 0 | 约 140 万 | Backpressured 最高接近 100% |
| keyed preprocess + low-speed buckets | `RUNNING` | 约 140 万 | 约 486 万 | Backpressured 约 92% |
| spatial candidate recall | `RUNNING` | 约 486 万 | 约 552 万 | Backpressured 约 71% |
| pair detection + Kafka/JDBC sink | `RUNNING` | 约 551 万 | 0 | Backpressured 0%，Busy 最高 100% |

注意：

- Source 的 `Bytes/Records Received` 为 0 是常见现象，因为 source 没有上游 Flink 算子；应看 `Records Sent`。
- Sink 链路的 `Records Sent` 为 0 也不一定异常，因为它是终端输出，不再向下游 Flink 算子发送数据；应结合 Kafka/JDBC 目标端和自定义指标判断真实输出。
- 反压不是“作业失败”，而是吞吐瓶颈信号。持续 high 时，说明数据正在被下游速度限制。

### 2.3 Exceptions 页面

Exceptions 用于确认“作业是否因为异常失败过”。

主要看两块：

| 区域 | 含义 | 当前结果 |
| --- | --- | --- |
| Root Exception | 最近导致作业失败的根异常 | 空 |
| Exception History | 历史异常记录 | 空 |

当前结论：未发现异常堆栈，作业没有因 Java 异常、连接异常、反序列化异常等原因失败。

如果这里出现异常，优先看：

1. Root Exception 的第一段异常类型，例如 `NullPointerException`、`TimeoutException`、`SQLException`。
2. `Caused by` 中最底层的真实原因。
3. 异常对应的 TaskManager host 和 subtask。
4. 是否与 Kafka、JDBC、状态后端、序列化或业务数据字段有关。

### 2.4 TimeLine 页面

TimeLine 用于看每个 vertex 的状态变化时间。

常见状态含义：

| 状态 | 含义 | 排查意义 |
| --- | --- | --- |
| CREATED | 作业图已创建 | 正常启动过程 |
| SCHEDULED | 任务等待调度 | 如果长时间停留，可能 slot 不足 |
| DEPLOYING | 正在部署到 TaskManager | 如果长时间停留，可能 TM 或网络问题 |
| INITIALIZING | 初始化 operator/state | 恢复大状态时可能较久 |
| RUNNING | 正在运行 | 正常 |
| FAILED | 任务失败 | 需要看 Exceptions 和日志 |
| CANCELING / CANCELED | 正在取消或已取消 | 人工或系统取消 |
| RESTARTING | 正在重启 | 需要看失败原因和重启策略 |

当前 TimeLine 结论：

- 4 个 vertex 均进入 `RUNNING`。
- 未看到 `FAILED`、`CANCELED`、`RESTARTING`。
- 启动阶段耗时很短，没有卡在调度或初始化。

### 2.5 Checkpoints 页面

Checkpoints 用于确认“状态一致性和故障恢复是否健康”。

当前 Checkpoint 统计：

| 指标 | 当前值 | 判断 |
| --- | ---: | --- |
| Triggered | 3 | 已按配置触发 |
| In Progress | 0 | 当前没有卡住的 Checkpoint |
| Completed | 3 | 全部成功 |
| Failed | 0 | 正常 |
| Restored | 0 | 当前作业不是从历史 Checkpoint/Savepoint 恢复启动 |

最新完成的 Checkpoint：

| 字段 | 当前值 |
| --- | --- |
| ID | 3 |
| 完成时间 | 2026-07-07 10:32:27 |
| 端到端耗时 | 约 6.953 秒 |
| Checkpointed Data Size | 约 632 MB |
| Full Checkpoint Data Size | 约 632 MB |
| 类型 | aligned checkpoint |
| 路径 | `file:/data/flink/checkpoints/30cebce2a67163d0352b83eaef9d8e7e/chk-3` |
| 失败 Checkpoint | None |
| Savepoint | None |
| Restore | None |

各算子状态大小：

| Operator | Acknowledged | 耗时 | 状态大小 |
| --- | --- | ---: | ---: |
| Source + parse/filter/watermark | 2/2 | 4 ms | 521 B |
| keyed preprocess + low-speed buckets | 2/2 | 6 s | 153 MB |
| spatial candidate recall | 2/2 | 2 s | 34.8 MB |
| pair detection + sinks | 2/2 | 2 s | 444 MB |

如何判断 Checkpoint 是否健康：

1. `Failed` 持续为 0 是好信号。
2. `Completed` 应按配置周期持续增长。本集群配置 `execution.checkpointing.interval=180000`，即约 3 分钟一次。
3. `End to End Duration` 应明显小于超时时间。本集群配置 `execution.checkpointing.timeout=600000`，即 10 分钟；当前约 7 秒，健康。
4. 状态大小持续快速膨胀时，要检查 keyed state 是否存在 TTL 不生效、候选 pair 状态清理不及时、输入乱序或数据倾斜。

### 2.6 Configuration 页面

Configuration 用于确认作业运行方式和参数。

当前页面显示：

| 字段 | 当前值 | 含义 |
| --- | --- | --- |
| Execution mode | `PIPELINED` | 流式流水线执行 |
| Max. number of execution retries | Cluster level default restart strategy | 使用集群默认重启策略 |
| Job parallelism | 2 | 本 Job 并行度为 2 |
| Object reuse mode | false | 对象复用关闭，更安全但可能多一些对象分配 |
| User Configuration | No Data | 页面未展示用户自定义配置 |

集群侧关键配置：

| key | value | 解读 |
| --- | --- | --- |
| `execution.checkpointing.mode` | `EXACTLY_ONCE` | 精确一次状态语义 |
| `execution.checkpointing.interval` | `180000` | 每 180 秒触发一次 Checkpoint |
| `execution.checkpointing.timeout` | `600000` | Checkpoint 10 分钟超时 |
| `parallelism.default` | `5` | 集群默认并行度为 5，但当前 Job 页面显示并行度 2 |
| `taskmanager.numberOfTaskSlots` | `50` | 每个 TaskManager 50 个 slot |
| `taskmanager.memory.process.size` | `61440m` | TaskManager 进程内存 60 GB |

注意：Configuration 页面显示 `User Configuration: No Data` 不代表业务参数没有生效，只表示当前 Flink Web 没有把用户配置展开展示。业务参数仍需要从提交参数、日志或代码配置读取逻辑确认。

## 3. 当前反压解读

二次采样显示反压不是瞬时噪声：

| Vertex | Backpressure Level | 采样现象 |
| --- | --- | --- |
| Source + parse/filter/watermark | high | subtask 0 反压接近 99%，subtask 1 空闲 |
| keyed preprocess + low-speed buckets | high | 两个 subtask 反压均约 94% 到 95% |
| spatial candidate recall | high | 两个 subtask 反压约 73% 到 75% |
| pair detection + Kafka/JDBC sink | ok | 无反压，但 busy 比例较高 |

这说明：

1. 数据链路能处理数据，记录数在增长。
2. 上游存在被下游限制的现象，尤其 preprocess 和 candidate recall 反压明显。
3. Source 的两个 subtask 数据不均衡：一个 subtask 处理大量数据，另一个几乎空闲。这通常与 Kafka topic 分区数、Kafka 分区分配、数据 key 分布或 source 并行度有关。
4. 末端算子没有反压，但 busy 高，表示它在持续计算或写出；如果后续吞吐下降，应优先检查 JDBC sink、Kafka sink、批量写入参数和数据库响应时间。

反压排查建议：

1. 观察 15 到 30 分钟，看 `Backpressured` 是否长期 high。如果只在启动或数据高峰出现，可以接受；如果长期 high，应优化。
2. 检查 Kafka 输入 topic 分区数是否至少大于等于 source 并行度 2，并确认两个 source subtask 都有分区可消费。
3. 检查输入数据是否 key 倾斜，例如某些 MMSI 或空间桶产生过多候选 pair。
4. 检查 `alongside-spatial-candidate-recall` 的候选膨胀比例。本次 preprocess 输出约 486 万，candidate recall 输出约 552 万，膨胀可控，但仍需结合持续运行后的状态大小观察。
5. 检查 JDBC sink 写入耗时、连接池、批量大小、数据库索引和锁等待。虽然页面显示 sink 反压 ok，但 busy 高，需要确认是否接近吞吐上限。
6. 若反压持续影响延迟，可考虑提高并行度，并同步确认 Kafka 分区数、keyBy 分布、数据库写入能力是否能承接。

## 4. Watermark 解读

Watermark 表示事件时间进度，不是系统当前时间。

当前 Overview 显示 Low Watermark 约为：

```text
2026-07-07 18:30:25 ~ 2026-07-07 18:30:31
```

它晚于本次排查机器时间 `2026-07-07 10:33 CST`。这通常有几种可能：

1. Kafka 输入是回放数据，事件时间不是当前机器时间。
2. AIS 字段 `updatetime` 已经是未来时间。
3. 时间字段解析时存在时区转换差异。
4. 上游数据源本身写入了未来时间戳。

这不是直接的 Flink 失败信号，但会影响基于事件时间的窗口、定时器和延迟判断。建议抽样检查 Kafka 输入消息里的原始时间字段，并对照程序中 `assign-ais-event-time` 的解析逻辑。

## 5. 后续巡检清单

日常判断这个 Job 是否健康，可以按下面顺序看：

| 顺序 | 页面 | 看什么 | 健康标准 |
| --- | --- | --- | --- |
| 1 | 顶部摘要 | `Job State`、`Duration` | `RUNNING`，Duration 持续增加 |
| 2 | Overview | 所有 vertex 状态 | 全部 `RUNNING`，记录数持续增长 |
| 3 | Overview | Backpressured / Busy / Watermark | 反压不要长期 high，Watermark 符合输入数据时间 |
| 4 | Exceptions | Root Exception / History | 为空 |
| 5 | Checkpoints | Completed / Failed / Duration | Completed 增长，Failed 为 0，耗时小于 timeout |
| 6 | Configuration | parallelism / checkpoint 配置 | 与预期一致 |
| 7 | TaskManagers | TM 数量、slot、资源 | TM 在线，slot 和内存充足 |
| 8 | 外部系统 | Kafka / JDBC | topic 有输出，数据库写入正常，无堆积或慢查询 |

## 6. 本次使用的验证接口

本次除页面检查外，还使用了 Flink REST 接口核对：

```text
GET /jobs/30cebce2a67163d0352b83eaef9d8e7e
GET /jobs/30cebce2a67163d0352b83eaef9d8e7e/exceptions
GET /jobs/30cebce2a67163d0352b83eaef9d8e7e/checkpoints
GET /jobs/30cebce2a67163d0352b83eaef9d8e7e/config
GET /overview
GET /taskmanagers
GET /jobs/overview
GET /jobmanager/config
GET /jobs/30cebce2a67163d0352b83eaef9d8e7e/vertices/<vertex-id>
GET /jobs/30cebce2a67163d0352b83eaef9d8e7e/vertices/<vertex-id>/backpressure
```

这些接口适合后续脚本化巡检。页面适合人工观察，REST 接口适合定时采样、报警和留存证据。
