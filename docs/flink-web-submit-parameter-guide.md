# Flink Web 页面提交 jar 参数配置指南

本文用于开发、测试人员直接在 Apache Flink Web Dashboard 的 `Submit New Job` 页面上传 jar 并配置参数测试。当前 jar 入口类为：

```text
com.hifleet.analysis.portcall.flink.FlinkPortCallJob
```

当前工程同时包含两条输出链路：

- 停船点链路：由 `stop-point.enabled` 控制，开启时输出到 `kafka.stop-point.topic`。
- 搭靠事件链路：默认关闭，只有配置 `--alongside.enabled=true` 后才会启动，输出到 `alongside.output.topic`，可选写入 MySQL。

本次 Flink Web 页面测试要求只跑搭靠事件，不计算停靠点，因此 Program Arguments 必须包含：

```text
--stop-point.enabled=false
--alongside.enabled=true
```

## 1. 页面字段怎么填

Flink Web 页面上传 jar 后，通常会显示以下字段：

| 页面字段 | 建议填写 | 含义 |
| --- | --- | --- |
| Entry Class | `com.hifleet.analysis.portcall.flink.FlinkPortCallJob` | Flink 作业 main class。截图中已经识别到该类，一般无需修改。 |
| Parallelism | 空或按测试规模填写，例如 `1`、`2`、`4` | 作业并行度。为空时使用作业参数 `flink.parallelism` 或集群默认值。小流量测试建议 `1` 或 `2`，实时验证可按资源设置。 |
| Program Arguments | 重点填写，格式见下文 | 传给 main 方法的业务参数。用于覆盖 jar 内置 `application.properties`。 |
| Savepoint Path | 恢复历史状态时填写 | 首次测试通常留空。需要从 savepoint 恢复时填 savepoint 路径。 |
| Allow Non Restored State | 一般不勾选 | 从旧 savepoint 恢复但算子状态无法完全匹配时才考虑勾选。普通测试不要勾选。 |

Program Arguments 支持两种写法：

```text
--key=value
```

或：

```text
--key value
```

推荐使用 `--key=value`，复制到页面里更不容易错。多个参数用空格或换行分隔均可。

## 2. 参数覆盖顺序

作业启动时按以下优先级读取配置，后者覆盖前者：

```text
代码默认值 -> application.properties -> 环境变量 -> JVM -D 参数 -> Flink Program Arguments
```

在 Web 页面测试时，主要使用 `Program Arguments`。如果涉及数据库密码，优先由 Flink 集群环境变量或 JVM 参数提供，不建议直接填在 Web 页面中。

环境变量名规则：配置 key 转大写，并将 `.`、`-` 替换为 `_`。例如：

| 配置 key | 环境变量 |
| --- | --- |
| `kafka.bootstrap.servers` | `KAFKA_BOOTSTRAP_SERVERS` |
| `alongside.enabled` | `ALONGSIDE_ENABLED` |
| `alongside.db-sink.url` | `ALONGSIDE_DB_URL` |
| `alongside.db-sink.username` | `ALONGSIDE_DB_USERNAME` |
| `alongside.db-sink.password` | `ALONGSIDE_DB_PASSWORD` |

## 3. 必填或最常改参数

| 参数 | 默认值 | 自主配置建议 |
| --- | --- | --- |
| `kafka.bootstrap.servers` | `192.168.99.92:9092` | Kafka broker 地址。测试环境、回放环境、生产环境必须确认后再填。 |
| `kafka.topic` | `LIVE_RECENTSHIPS_DATA` | 输入 AIS topic。联调时可改成 replay topic，避免消费生产实时流。 |
| `kafka.group.id` | `portcall-group` | Consumer group。测试必须使用独立 group，避免影响已有作业位点。 |
| `kafka.client.id` | `portcall-consumer-1` | Kafka client 前缀。建议与 group 区分，便于排查。 |
| `kafka.starting-offsets` | `committed` | `committed`、`earliest`、`latest`。首次测试常用 `latest` 或独立 group + `earliest`。 |
| `stop-point.enabled` | `true` | 是否启用停靠点计算链路。本次只跑搭靠时必须设为 `false`。 |
| `kafka.stop-point.topic` | `RELIABLE_STOP_POINTS` | 停船点输出 topic。只有 `stop-point.enabled=true` 时才会使用。 |
| `alongside.enabled` | `false` | 是否开启搭靠链路。测试搭靠必须填 `true`。 |
| `alongside.output.topic` | `RELIABLE_ALONGSIDE_EVENTS` | 搭靠事件输出 topic。测试建议使用测试 topic。 |
| `flink.parallelism` | `12` | 代码内作业并行度。Web 页面 Parallelism 也可控制，二者不要配置冲突。 |
| `flink.debug.print` | `false` | 是否在 TaskManager 日志打印中间结果。短时间排查可开，长时间作业不要开。 |

## 4. 搭靠识别参数详解

### 4.1 开关和输出

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.enabled` | `false` | 搭靠分支总开关。`false` 时只跑停船点，不产生搭靠 Kafka/DB 输出。 |
| `alongside.output.topic` | `RELIABLE_ALONGSIDE_EVENTS` | 搭靠事件 Kafka topic。测试时建议单独 topic，例如 `RELIABLE_ALONGSIDE_EVENTS_TEST`。 |
| `alongside.rule-version` | `ALONGSIDE_V2_INSURANCE` | 规则版本，会进入事件输出和 `event_id` 生成。改动规则阈值后建议同步升级版本号。 |
| `alongside.output.update-interval-minutes` | `5` | 正式搭靠候选已经输出后，持续满足规则时，每隔多少分钟输出一次 `ALONGSIDE_UPDATE`。 |
| `alongside.metrics.enabled` | `true` | 指标开关。当前代码已注册关键 counters，建议保持开启。 |

### 4.2 输入预过滤

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.pre-filter.enabled` | `true` | 搭靠分支预过滤开关。关闭后会放入更多 AIS 点，误报和状态压力会上升。 |
| `alongside.pre-filter.max-speed` | `4.0` | 进入搭靠分支的单船最大航速，单位 kn。搭靠/并靠通常是低速行为；调大可提高召回，但会增加误报。 |
| `alongside.pre-filter.exclude-invalid-mmsi` | `true` | 是否过滤空 MMSI、非正数 MMSI、`999999999`。建议保持 `true`。 |

### 4.3 空间候选召回

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.spatial.bucket-size-degrees` | `0.02` | 空间网格大小，单位经纬度度。越小候选更少、压力更低，但边界附近可能漏召回；越大候选更多、计算压力更高。 |
| `alongside.candidate.max-distance-nm` | `0.5` | 候选外圈最大距离，单位海里。超过该距离直接排除。调大提高召回，调小减少误报。 |
| `alongside.candidate.max-speed` | `4.0` | 构造候选 pair 时双方最大航速，单位 kn。通常与预过滤速度保持一致。 |
| `alongside.candidate.max-time-gap-minutes` | `10` | 两船 AIS 点最大时间差。调大可适应稀疏 AIS，但会增加错配。 |
| `alongside.candidate.min-duration-minutes` | `20` | 输出弱候选的最短持续时长。调小可更快看到候选，误报也会增加。 |
| `alongside.candidate.min-points` | `3` | 输出弱候选的最少证据点数。测试样本稀疏时可临时降到 `2`。 |

### 4.4 正式搭靠确认

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.confirm.max-distance-nm` | `0.15` | 正式候选最大距离，单位海里。越小越严格。 |
| `alongside.confirm.max-speed` | `2.0` | 正式候选双方最大航速，单位 kn。用于排除仍在明显航行的近距离船舶。 |
| `alongside.confirm.max-relative-speed` | `1.0` | 两船平均相对速度上限，单位 kn。越小越强调同步低速/静止。 |
| `alongside.confirm.max-time-gap-minutes` | `10` | 正式确认最大时间差配置，当前主要候选阶段已用 `candidate.max-time-gap-minutes` 拦截。建议与候选时间差保持一致。 |
| `alongside.confirm.high-confidence-time-gap-minutes` | `5` | 高覆盖证据点的时间差阈值。时间差小于该值的点越多，评分越高。 |
| `alongside.confirm.min-duration-minutes` | `30` | 输出正式候选的最短持续时长。 |
| `alongside.confirm.min-points` | `6` | 输出正式候选的最少证据点数。AIS 稀疏测试可降低，真实实时建议谨慎。 |

### 4.5 航向平行判断

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.heading.enabled` | `true` | 是否启用航向平行约束。AIS course 缺失时不会因为该项直接拒绝。 |
| `alongside.heading.parallel-threshold-degree` | `30` | 判断平行/反向平行的航向差阈值。两船 course 差接近 `0` 或 `180` 都视为平行，差值在阈值内记为接受。 |
| `alongside.heading.parallel-threshold-degrees` | `30` | 同上，兼容参数名。建议只填一个，优先使用 `alongside.heading.parallel-threshold-degree`。 |

### 4.6 状态、合并和 END 事件

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.merge.enabled` | `true` | 是否合并同一 pair 的连续证据。关闭后有新断点就更容易切成多个事件。 |
| `alongside.merge.max-gap-minutes` | `30` | 同一 pair 两段 evidence 间隔超过该值时切分；已输出过事件的 pair 会补一个 `ALONGSIDE_END`。 |
| `alongside.merge.max-center-distance-nm` | `1.0` | 预留的中心点合并距离配置，当前 detector 主要按时间 gap 切分。 |
| `alongside.ais-gap-minutes` | `30` | 统计单船 AIS 轨迹断点的阈值。超过该间隔会增加 `ais_gap_count`，并影响评分和覆盖率。 |
| `alongside.state.bucket-point-ttl-minutes` | `15` | 空间桶内最近点状态保留时间。要大于或等于候选最大时间差。 |
| `alongside.state.pair-ttl-minutes` | `120` | pair 检测状态保留时间。需覆盖预期最长事件和断续间隔。 |
| `alongside.state.ended-pair-ttl-hours` | `6` | 已结束 pair 状态保留时长配置。 |
| `alongside.state.ttl-hours` | `48` | 搭靠配置中的通用状态 TTL。 |

### 4.7 船型优先级

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.ship-type-filter.enabled` | `false` | 是否启用低优先级船型过滤。默认关闭，避免因船型识别不准漏召回。 |
| `alongside.ship-type.high-priority` | `TANKER,CHEMICAL_TANKER,LNG,LPG,CARGO,BULK_CARRIER,CONTAINER,REEFER,FISH_CARRIER,BUNKER,SUPPLY,OFFSHORE_SUPPLY,UNKNOWN` | 高优先级船型集合。 |
| `alongside.ship-type.normal-service` | `TUG,PILOT,LAW_ENFORCEMENT,SAR,PORT_SERVICE,DREDGER,WORKBOAT` | 常规港作/服务船集合，与商船搭靠时通常降级。 |
| `alongside.ship-type.low-priority` | `PLEASURE_CRAFT,SAILING,LOCAL_PASSENGER` | 低优先级船型集合。启用船型过滤后，任一方为低优先级会被过滤。 |

船型来源优先使用 AIS `shipType`，无法识别时再从船名关键词识别，如 `TUG`、`PILOT`、`TANKER`、`LNG`、`BUNKER`、`FISH`、`CARGO` 等。

### 4.8 场景和风险等级约束

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.scene.nav-status-downgrade.enabled` | `true` | 任一船 AIS nav status 为 `1` 或 `5` 时视作锚泊/系泊场景，事件降权。 |
| `alongside.scene.no-scene-data.max-level` | `L2` | 无港口、锚地、STS、船舶画像、制裁区等外部证据时，最终等级最高不超过该值。建议保持 `L2`。 |
| `alongside.severity-cap.no-scene-data.max-level` | `L2` | 同上，兼容参数名。 |
| `alongside.scene-data.available` | `false` | 是否已有外部场景数据。当前实时链路默认没有。 |
| `alongside.counterparty-risk.available` | `false` | 是否已有交易对手/船舶风险数据。当前默认没有。 |
| `alongside.scene.auto-l4-enabled` | `false` | 是否允许自动输出 L4。当前候选事件不建议打开。 |
| `alongside.auto-l4-enabled` | `false` | 同上，兼容参数名。 |

当前搭靠事件只是“候选事件 + 轻量评分”，不是最终保险高风险结论。没有外部场景和对手风险证据时，建议保持最高 `L2`，不要在实时候选阶段直接给 L4。

## 5. 数据库落库参数

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.db-sink.enabled` | `false` | 是否开启 MySQL 落库。只测 Kafka 输出时保持 `false`。 |
| `alongside.db-sink.detail-enabled` | `true` | 是否写入事件明细点。压测或只看摘要时可设为 `false`。 |
| `alongside.db-sink.url` | 空 | JDBC URL。建议通过 `ALONGSIDE_DB_URL` 环境变量提供。 |
| `alongside.db-sink.username` | 空 | 数据库用户名。建议通过 `ALONGSIDE_DB_USERNAME` 提供。 |
| `alongside.db-sink.password` | 空 | 数据库密码。建议通过 `ALONGSIDE_DB_PASSWORD` 提供，不要写进文档、代码或截图。 |
| `alongside.db-sink.batch-size` | `100` | 批量写入条数。小流量验证可用 `1` 方便立即看到结果；长期运行用默认或更大。 |
| `alongside.db-sink.flush-interval-ms` | `3000` | 批量写入最大等待时间。 |
| `alongside.db-sink.max-retries` | `3` | 写库失败最大重试次数。 |
| `alongside.db-sink.table-event` | `ship_realtime_alongside_event` | 事件摘要表。 |
| `alongside.db-sink.table-detail` | `ship_realtime_alongside_event_detail` | 事件明细表。 |
| `alongside.db-sink.max-detail-points-per-event` | `200` | 单事件最多保留多少个证据点。 |
| `alongside.db-sink.auto-create-table` | `true` | 启动时自动建表。受限数据库账号可设为 `false` 并提前建表。 |
| `alongside.db-sink.allowed-severity-levels` | 空 | 写入数据库的事件等级白名单，逗号分隔。为空表示所有等级都写库；配置 `L2` 表示只将 `severity_level=L2` 的搭靠事件写入数据库。Kafka 输出不受影响。 |

`event_id` 采用稳定幂等规则：

```text
md5(rule_version + "|" + pair_id + "|" + event_sub_type + "|" + start_time + "|" + end_time)
```

摘要表对 `event_id` 建唯一键，重复消费、Flink 重启或 DB sink 重试时执行 upsert，避免重复插入。

## 6. Flink 运行参数

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `flink.checkpoint.interval-ms` | `180000` | checkpoint 间隔。测试可设 `60000`，长期运行按状态规模调整。 |
| `flink.state.backend` | `rocksdb` | 状态后端。实时大状态建议 RocksDB。 |
| `flink.checkpoint.dir` | `file:///data/flink/checkpoints` | checkpoint 目录。集群运行应使用各 TaskManager/JobManager 都可访问的持久路径。 |
| `flink.savepoint.dir` | `file:///data/flink/savepoints` | savepoint 目录。 |
| `flink.rocksdb.localdir` | `/data/flink/rocksdb` | TaskManager 本地 RocksDB 工作目录。 |
| `flink.parallelism` | `12` | 作业并行度，`0` 表示使用集群默认。 |
| `flink.checkpoint.max-retained` | `1` | 保留 checkpoint 个数。 |
| `flink.checkpoint.incremental` | `true` | RocksDB 增量 checkpoint。 |
| `flink.checkpoint.min-pause-ms` | `60000` | 两次 checkpoint 最小间隔。 |
| `flink.checkpoint.timeout-ms` | `300000` | checkpoint 超时时间。 |
| `flink.checkpoint.externalized-cleanup` | `DELETE_ON_CANCELLATION` | cancel 作业时是否删除外部 checkpoint。 |

## 7. 自主调参方法

常见目标和建议：

| 目标 | 建议调整 |
| --- | --- |
| 只跑搭靠事件 | `--stop-point.enabled=false` 且 `--alongside.enabled=true`。这是本次 Web 页面测试的推荐方式。 |
| 只验证 jar 能启动，不想产生搭靠输出 | `stop-point.enabled=true`，保持 `--alongside.enabled=false`，使用独立 `kafka.group.id` 和测试 stop-point topic。 |
| 快速看到搭靠候选 | `alongside.enabled=true`，降低 `candidate.min-duration-minutes`、`candidate.min-points`、`confirm.min-duration-minutes`、`confirm.min-points`。只用于样本测试。 |
| 减少误报 | 收紧 `candidate.max-distance-nm`、`confirm.max-distance-nm`、`confirm.max-relative-speed`，提高 `confirm.min-duration-minutes` 和 `confirm.min-points`。 |
| AIS 点稀疏，召回不足 | 适度调大 `candidate.max-time-gap-minutes`、`bucket-point-ttl-minutes`，降低 `confirm.min-points`，但要关注错配。 |
| 事件更新太频繁 | 调大 `alongside.output.update-interval-minutes`。 |
| 只看 Kafka，不写数据库 | `alongside.db-sink.enabled=false`。 |
| 验证数据库摘要表 | `alongside.db-sink.enabled=true`，`detail-enabled=false`，`batch-size=1`。 |
| 验证数据库摘要和明细 | `alongside.db-sink.enabled=true`，`detail-enabled=true`，`max-detail-points-per-event` 按需要配置。 |
| 只将 L2 事件写入数据库 | `alongside.db-sink.allowed-severity-levels=L2`。该配置只影响 DB sink，不影响搭靠 Kafka topic。 |
| 控制测试范围 | 使用 `pipeline.scope.mmsi-whitelist` 或经纬度范围参数，只消费目标船或目标区域。 |

范围过滤参数：

| 参数 | 含义 |
| --- | --- |
| `pipeline.scope.mmsi-whitelist` | MMSI 白名单，逗号分隔。为空表示不过滤。 |
| `pipeline.scope.min-lon` / `pipeline.scope.max-lon` | 经度范围。 |
| `pipeline.scope.min-lat` / `pipeline.scope.max-lat` | 纬度范围。 |

## 8. 参数模板

### 模板 A：只验证 jar 启动和停船点链路，不开启搭靠

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=LIVE_RECENTSHIPS_DATA
--kafka.group.id=portcall-web-smoke-20260706
--kafka.client.id=portcall-web-smoke-1
--kafka.starting-offsets=latest
--kafka.stop-point.topic=RELIABLE_STOP_POINTS_TEST
--alongside.enabled=false
--flink.parallelism=1
--flink.checkpoint.interval-ms=60000
```

适用场景：先确认 jar、入口类、Kafka 连接、checkpoint 基本正常。

### 模板 B：开启搭靠，输出到测试 Kafka topic，不写数据库

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=LIVE_RECENTSHIPS_DATA
--kafka.group.id=alongside-web-kafka-20260706
--kafka.client.id=alongside-web-kafka-1
--kafka.starting-offsets=latest
--stop-point.enabled=false
--alongside.enabled=true
--alongside.output.topic=RELIABLE_ALONGSIDE_EVENTS_TEST
--alongside.db-sink.enabled=false
--alongside.scene.no-scene-data.max-level=L2
--alongside.scene.auto-l4-enabled=false
--flink.parallelism=2
--flink.checkpoint.interval-ms=60000
```

适用场景：只运行搭靠链路，验证实时搭靠 Kafka 输出，不计算停靠点，不落库。

### 模板 C：本地样本或 replay topic 快速出结果

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=AIS_REPLAY_ALONGSIDE_TEST
--kafka.group.id=alongside-web-replay-20260706
--kafka.client.id=alongside-web-replay-1
--kafka.starting-offsets=earliest
--stop-point.enabled=false
--alongside.enabled=true
--alongside.output.topic=RELIABLE_ALONGSIDE_EVENTS_REPLAY_TEST
--alongside.candidate.min-duration-minutes=1
--alongside.candidate.min-points=2
--alongside.confirm.min-duration-minutes=2
--alongside.confirm.min-points=3
--alongside.db-sink.enabled=false
--flink.parallelism=1
--flink.debug.print=true
```

适用场景：用短样本快速验证规则链路。该模板阈值偏宽松，不用于长期实时运行。

### 模板 D：开启搭靠 Kafka + MySQL 摘要落库

数据库 URL、用户名、密码建议在 Flink 集群环境变量中配置：

```bash
ALONGSIDE_DB_URL=jdbc:mysql://<host>:<port>/dataanalysis?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
ALONGSIDE_DB_USERNAME=<username>
ALONGSIDE_DB_PASSWORD=<password>
```

Program Arguments：

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=LIVE_RECENTSHIPS_DATA
--kafka.group.id=alongside-web-db-summary-20260706
--kafka.client.id=alongside-web-db-summary-1
--kafka.starting-offsets=latest
--stop-point.enabled=false
--alongside.enabled=true
--alongside.output.topic=RELIABLE_ALONGSIDE_EVENTS_TEST
--alongside.db-sink.enabled=true
--alongside.db-sink.detail-enabled=false
--alongside.db-sink.batch-size=1
--alongside.db-sink.flush-interval-ms=1000
--alongside.db-sink.table-event=ship_realtime_alongside_event
--alongside.db-sink.auto-create-table=true
--alongside.db-sink.allowed-severity-levels=L2
--flink.parallelism=1
```

适用场景：先验证摘要表 upsert 和事件数量，不写明细，且只将 `severity_level=L2` 的搭靠事件写入数据库，便于排查。

### 模板 E：开启搭靠 Kafka + MySQL 摘要和明细落库

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=LIVE_RECENTSHIPS_DATA
--kafka.group.id=alongside-web-db-detail-20260706
--kafka.client.id=alongside-web-db-detail-1
--kafka.starting-offsets=latest
--stop-point.enabled=false
--alongside.enabled=true
--alongside.output.topic=RELIABLE_ALONGSIDE_EVENTS_TEST
--alongside.db-sink.enabled=true
--alongside.db-sink.detail-enabled=true
--alongside.db-sink.batch-size=20
--alongside.db-sink.flush-interval-ms=3000
--alongside.db-sink.max-detail-points-per-event=200
--alongside.db-sink.table-event=ship_realtime_alongside_event
--alongside.db-sink.table-detail=ship_realtime_alongside_event_detail
--alongside.db-sink.auto-create-table=true
--alongside.db-sink.allowed-severity-levels=L2
--flink.parallelism=2
```

适用场景：验证完整落库，且只将 `severity_level=L2` 的搭靠事件写入数据库。注意明细点较多时写库压力会明显增加。

### 模板 F：限定 MMSI 或区域的小范围验证

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=LIVE_RECENTSHIPS_DATA
--kafka.group.id=alongside-web-scope-20260706
--kafka.client.id=alongside-web-scope-1
--kafka.starting-offsets=latest
--pipeline.scope.mmsi-whitelist=123456789,987654321
--pipeline.scope.min-lon=120.0
--pipeline.scope.max-lon=123.0
--pipeline.scope.min-lat=29.0
--pipeline.scope.max-lat=32.0
--stop-point.enabled=false
--alongside.enabled=true
--alongside.output.topic=RELIABLE_ALONGSIDE_EVENTS_SCOPE_TEST
--alongside.db-sink.enabled=false
--flink.parallelism=1
```

适用场景：只验证目标船或目标海域，减少无关数据和状态规模。

## 9. 当前搭靠事件评分规则说明

### 9.1 先判定是否形成事件

一条搭靠事件不是单点判断，而是同一 pair 的多条近距离证据累计形成窗口后再判定。

候选 pair 先满足：

- 两船不是同一 MMSI，且 MMSI 有效。
- 双方速度不超过 `alongside.candidate.max-speed`。
- 两船 AIS 点时间差不超过 `alongside.candidate.max-time-gap-minutes`。
- 两船距离不超过 `alongside.candidate.max-distance-nm`。
- pair priority 不是 `EXCLUDE`。
- 如果启用船型过滤，低优先级船型会被过滤。

弱候选 `WEAK_ALONGSIDE_CANDIDATE`：

- 持续时长 `>= alongside.candidate.min-duration-minutes`。
- 证据点数 `>= alongside.candidate.min-points`。

正式候选 `ALONGSIDE_CONFIRMED_CANDIDATE`：

- 持续时长 `>= alongside.confirm.min-duration-minutes`。
- 证据点数 `>= alongside.confirm.min-points`。
- 最大距离 `<= alongside.confirm.max-distance-nm`。
- 最大速度 `<= alongside.confirm.max-speed`。
- 平均相对速度 `<= alongside.confirm.max-relative-speed`。
- 开启航向判断且有 course 数据时，航向平行接受比例 `>= 0.8`。

正式候选输出后，后续持续满足规则并超过 `alongside.output.update-interval-minutes` 会输出 `ALONGSIDE_UPDATE`。同一 pair evidence 间隔超过 `alongside.merge.max-gap-minutes` 时切分状态；如果之前已经输出过弱候选或正式候选，会补发 `ALONGSIDE_END`。

### 9.2 分数如何计算

当前分数为 0 到 100 的轻量规则分，不是最终保险风险分。基础加分项如下：

| 因子 | 加分规则 | 解释 |
| --- | --- | --- |
| 最近距离 | `min_distance_nm <= 0.1` 加 6，否则加 4 | 越近越像真实搭靠。 |
| 持续时长基础分 | `duration_minutes >= 30` 加 5，否则加 3 | 持续时间越长，偶然近距离经过的可能越低。 |
| 证据点数量 | `point_count >= 6` 加 5，否则加 3 | 多点证据比单点更可靠。 |
| 时间覆盖 | 高时间质量点达到 80% 加 4，否则加 2 | 高时间质量点指两船 AIS 点时间差不超过 `confirm.high-confidence-time-gap-minutes`。 |
| 平均相对速度 | `avg_relative_speed <= 1.0 kn` 加 3，否则加 0 | 两船速度越同步，越像并靠。 |
| 航向平行 | `heading_parallel_ratio >= 0.8` 加 2，否则加 0 | 航向平行或反向平行都可接受。 |
| AIS 断点 | `ais_gap_count > 0` 加 10 | AIS 断点可能是异常线索，但也可能是数据质量问题。 |
| 长时间持续 | `>= 360 min` 加 10；`>= 120 min` 加 6；`>= 30 min` 加 3 | 长时间近距离持续会提高风险分。 |

之后再做以下调整：

- 若任一船 nav status 为 `1` 或 `5`，表示锚泊/系泊迹象，`event_score` 乘以 `0.5`，并输出 `exclusion_flag=DOWNGRADED_NORMAL_LIKELY`、`exclusion_reason=NAV_STATUS_ANCHORED_OR_MOORED`。
- 若置信度为 `LOW`，分数再乘以 `0.6`。
- 若事件是弱候选，分数最高封顶 `20`，等级固定按弱候选处理为 `L1`，随后仍受无场景数据封顶影响。
- 最终分数限制在 `0..100`。

### 9.3 置信度如何判断

| 置信度 | 条件 |
| --- | --- |
| `LOW` | 轨迹覆盖率 `< 0.6` 或证据点数 `< 3`。 |
| `HIGH` | 证据点数 `>= 6`，轨迹覆盖率 `>= 0.8`，无 AIS gap，且航向未知或航向平行比例 `>= 0.8`。 |
| `MEDIUM` | 不满足 LOW，也不满足 HIGH。 |

轨迹覆盖率当前按持续时长、证据点数和 `alongside.ais-gap-minutes` 估算：

```text
expected_points = duration_minutes / alongside.ais-gap-minutes + 1
track_coverage_ratio = min(1.0, point_count / expected_points)
```

### 9.4 风险等级如何映射

基础等级由分数映射：

| 分数区间 | 基础等级 |
| --- | --- |
| `score <= 0` | `L0` |
| `0 < score <= 25` | `L1` |
| `25 < score <= 50` | `L2` |
| `50 < score <= 75` | `L3` |
| `score > 75` | `L4` |

然后应用两个重要约束：

- 如果 `scene-data.available=false` 且 `counterparty-risk.available=false`，等级最高不超过 `alongside.scene.no-scene-data.max-level`，默认 `L2`。
- 如果 `alongside.scene.auto-l4-enabled=false`，即使基础等级达到 L4，也会降为 L3。

因此，当前默认配置下，即使某个候选分数较高，只要没有外部场景和对手风险证据，最终 `severity_level` 最高仍为 `L2`。

### 9.5 输出字段速读

| 字段 | 含义 |
| --- | --- |
| `event_id` | 幂等事件 ID，用于 Kafka 重放和 DB upsert 去重。 |
| `event_type` | 当前为 `ALONGSIDE_CANDIDATE`。 |
| `event_sub_type` | `WEAK_ALONGSIDE_CANDIDATE`、`ALONGSIDE_CONFIRMED_CANDIDATE`、`ALONGSIDE_UPDATE`、`ALONGSIDE_END`。 |
| `source_mode` | 当前为 `REALTIME_AUTO_BUCKET`。 |
| `rule_version` | 规则版本。 |
| `pair_id` | `minMmsi-maxMmsi`，避免 A-B/B-A 双写。 |
| `duration_minutes` | pair 证据窗口持续时间。 |
| `point_count` | pair 证据点数量。 |
| `min_distance_nm` / `max_distance_nm` / `avg_distance_nm` | 两船距离统计，单位海里。 |
| `avg_relative_speed` | 平均相对速度，单位 kn。 |
| `heading_parallel_ratio` | 航向平行接受比例，无航向数据时为空。 |
| `pair_priority` | pair 优先级，可能为 `HIGH`、`MEDIUM`、`DOWNGRADED` 等。 |
| `severity_level` | 轻量风险等级，受场景封顶约束。 |
| `event_score` | 轻量规则分。 |
| `confidence_level` | `LOW`、`MEDIUM`、`HIGH`。 |
| `exclusion_flag` / `exclusion_reason` | 降权或结束原因。 |
| `score_detail_json` | 当前包含 `track_coverage_ratio`、`heading_parallel_ratio`、`ais_gap_count` 等解释字段。 |

## 10. 提交前检查清单

- 确认 `kafka.group.id` 是本次测试专用，不复用生产 group。
- 确认 `kafka.topic` 是目标输入 topic，回放测试不要误连生产 topic。
- 确认输出 topic 是测试 topic，避免污染正式结果。
- 本次只跑搭靠事件时，确认包含 `--stop-point.enabled=false`。
- 开启搭靠时必须有 `--alongside.enabled=true`。
- 如果开启 DB sink，确认数据库账号、表名、权限和密码传入方式。
- 不要把数据库密码粘贴到截图、文档、git 或公开日志。
- 首次测试建议 `flink.parallelism=1`，稳定后再提高并行度。
- 长时间运行不要开启 `flink.debug.print=true`。
- 如果从 savepoint 恢复，确认 Entry Class、算子链和状态兼容性，再考虑 Savepoint Path。
