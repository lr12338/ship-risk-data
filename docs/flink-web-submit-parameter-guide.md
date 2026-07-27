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

## 1. 编译打包

### 1.1 环境要求

本项目使用 Maven 构建，编译目标为 Java 8，Flink 依赖版本为 `1.16.0`。打包前确认本机已安装并能正常执行：

```bash
java -version
mvn -version
```

建议使用 JDK 8 或兼容 Java 8 bytecode 的 JDK。若本机有多个 JDK，先确认 `JAVA_HOME` 指向可用版本。

### 1.2 常用打包命令

在项目根目录执行：

```bash
cd /Users/raymondlu/LocalProject/船舶风险评估数据服务/flink服务参考
mvn clean package
```

如果希望减少 Maven 输出，只看最终是否成功：

```bash
mvn -q clean package
```

打包成功时，命令退出码为 `0`，并在 `target/` 目录生成 jar。

### 1.3 运行测试

完整打包会默认执行单元测试。若只想先快速验证关键配置和 DB 过滤逻辑，可执行：

```bash
mvn -q -Dtest=PortCallConfigTest,ProgramArgumentsParserTest,AlongsideEventJdbcSinkTest test
```

当前只跑搭靠和 L2 入库过滤相关的重点测试包括：

| 测试类 | 覆盖内容 |
| --- | --- |
| `PortCallConfigTest` | `stop-point.enabled=false` 可关闭停靠点链路。 |
| `ProgramArgumentsParserTest` | Flink Web `Program Arguments` 能覆盖配置，包括 `allowed-severity-levels`。 |
| `AlongsideEventJdbcSinkTest` | DB sink 可按 `severity_level` 过滤，只写配置允许的等级。 |

### 1.4 打包产物说明

打包后通常会看到：

| 文件 | 用途 |
| --- | --- |
| `target/ai-flink-detect-ship-stop-0.1.0-SNAPSHOT.jar` | Flink Web 页面上传这个主 jar。 |
| `target/ai-flink-detect-ship-stop-0.1.0-SNAPSHOT-archive.jar` | AIS 归档工具 jar，不用于本次搭靠实时作业。 |
| `target/original-ai-flink-detect-ship-stop-0.1.0-SNAPSHOT.jar` | Maven shade 前的原始 jar，依赖不完整，不上传 Flink。 |

本次 Flink Web 提交应上传：

```text
target/ai-flink-detect-ship-stop-0.1.0-SNAPSHOT.jar
```

不要上传 `-archive.jar` 或 `original-...jar`。

### 1.5 确认主类和参数已打进 jar

可以用以下命令检查 jar manifest 中的主类：

```bash
jar xf target/ai-flink-detect-ship-stop-0.1.0-SNAPSHOT.jar META-INF/MANIFEST.MF
cat META-INF/MANIFEST.MF
```

应看到：

```text
Main-Class: com.hifleet.analysis.portcall.flink.FlinkPortCallJob
```

也可以确认默认配置文件里包含本次新增参数：

```bash
jar tf target/ai-flink-detect-ship-stop-0.1.0-SNAPSHOT.jar | grep application.properties
```

若需要查看 jar 内配置内容，可解压后检查 `application.properties`。实际运行时，Flink Web `Program Arguments` 仍会覆盖 jar 内默认值。

### 1.6 常见问题

| 现象 | 处理方式 |
| --- | --- |
| `mvn: command not found` | 安装 Maven，或使用 IDE/构建机已有 Maven。 |
| Java 版本不符合 | 调整 `JAVA_HOME`，确认 `mvn -version` 显示的 Java 版本正确。 |
| 下载依赖失败 | 检查网络、Maven 镜像或本地仓库配置。 |
| 上传 Flink 后找不到主类 | 确认上传的是主 jar，不是 `original-...jar`；Entry Class 填 `com.hifleet.analysis.portcall.flink.FlinkPortCallJob`。 |
| 作业启动后仍计算停靠点 | 确认 Program Arguments 包含 `--stop-point.enabled=false`。 |
| L1/L3 等事件仍写入数据库 | 确认 Program Arguments 包含 `--alongside.db-sink.allowed-severity-levels=L2`，且使用的是重新打包后的最新 jar。 |

## 2. 页面字段怎么填

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

JDBC URL 中常见的 `?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&useSSL=false` 可以直接放在 Flink Web 的 Program Arguments 输入框中。当前项目使用 MySQL Connector/J 8.0.33，建议同时设置 `serverTimezone=Asia/Shanghai`、`connectionTimeZone=Asia/Shanghai` 和 `forceConnectionTimeZoneToSession=true`，避免 MySQL 会话按 UTC 写入 `created_at` / `updated_at`。若密码包含空格，建议更换为不含空格的测试密码，或改用环境变量方式传入。

## 3. 参数覆盖顺序

作业启动时按以下优先级读取配置，后者覆盖前者：

```text
代码默认值 -> application.properties -> 环境变量 -> JVM -D 参数 -> Flink Program Arguments
```

在 Web 页面测试时，主要使用 `Program Arguments`。本次要求数据库 URL、用户名、密码也直接写入 `Program Arguments`，例如 `--alongside.db-sink.url=...`、`--alongside.db-sink.username=...`、`--alongside.db-sink.password=...`。

程序仍兼容环境变量方式。环境变量名规则：配置 key 转大写，并将 `.`、`-` 替换为 `_`。例如：

| 配置 key | 环境变量 |
| --- | --- |
| `kafka.bootstrap.servers` | `KAFKA_BOOTSTRAP_SERVERS` |
| `alongside.enabled` | `ALONGSIDE_ENABLED` |
| `alongside.db-sink.url` | `ALONGSIDE_DB_URL` |
| `alongside.db-sink.username` | `ALONGSIDE_DB_USERNAME` |
| `alongside.db-sink.password` | `ALONGSIDE_DB_PASSWORD` |

## 4. 必填或最常改参数

| 参数 | 默认值 | 自主配置建议 |
| --- | --- | --- |
| `kafka.bootstrap.servers` | `192.168.99.92:9092` | Kafka broker 地址。测试环境、回放环境、生产环境必须确认后再填。 |
| `kafka.topic` | `LIVE_RECENTSHIPS_DATA` | 输入 AIS topic。联调时可改成 replay topic，避免消费生产实时流。 |
| `kafka.group.id` | `portcall-group` | Consumer group。测试必须使用独立 group，避免影响已有作业位点。 |
| `kafka.client.id` | `portcall-consumer-1` | Kafka client 前缀。建议与 group 区分，便于排查。 |
| `kafka.starting-offsets` | `committed` | `committed`、`earliest`、`latest`。追历史积压用 `committed`；只分析作业启动后的最新实时 AIS，用全新 `kafka.group.id` + `latest`。 |
| `stop-point.enabled` | `true` | 是否启用停靠点计算链路。本次只跑搭靠时必须设为 `false`。 |
| `kafka.stop-point.topic` | `RELIABLE_STOP_POINTS` | 停船点输出 topic。只有 `stop-point.enabled=true` 时才会使用。 |
| `alongside.enabled` | `false` | 是否开启搭靠链路。测试搭靠必须填 `true`。 |
| `alongside.output.topic` | `RELIABLE_ALONGSIDE_EVENTS` | 搭靠事件输出 topic。测试建议使用测试 topic。 |
| `flink.parallelism` | `12` | 代码内作业并行度。Web 页面 Parallelism 也可控制，二者不要配置冲突。 |
| `flink.debug.print` | `false` | 是否在 TaskManager 日志打印中间结果。短时间排查可开，长时间作业不要开。 |

## 5. 搭靠识别参数详解

### 5.1 开关和输出

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.enabled` | `false` | 搭靠分支总开关。`false` 时只跑停船点，不产生搭靠 Kafka/DB 输出。 |
| `alongside.output.topic` | `RELIABLE_ALONGSIDE_EVENTS` | 搭靠事件 Kafka topic。测试时建议单独 topic，例如 `RELIABLE_ALONGSIDE_EVENTS_TEST`。 |
| `alongside.rule-version` | `ALONGSIDE_V2_INSURANCE` | 规则版本，会进入事件输出和 `event_id` 生成。改动规则阈值后建议同步升级版本号。 |
| `alongside.output.update-interval-minutes` | `5` | 正式搭靠候选已经输出后，持续满足规则时，每隔多少分钟输出一次 `ALONGSIDE_UPDATE`。 |
| `alongside.metrics.enabled` | `true` | 指标开关。当前代码已注册关键 counters，建议保持开启。 |

### 5.2 输入预过滤

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.pre-filter.enabled` | `true` | 搭靠分支预过滤开关。关闭后会放入更多 AIS 点，误报和状态压力会上升。 |
| `alongside.pre-filter.max-speed` | `4.0` | 进入搭靠分支的单船最大航速，单位 kn。搭靠/并靠通常是低速行为；调大可提高召回，但会增加误报。 |
| `alongside.pre-filter.exclude-invalid-mmsi` | `true` | 是否过滤空 MMSI、非正数 MMSI、`999999999`。建议保持 `true`。 |

### 5.3 空间候选召回

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.spatial.bucket-size-degrees` | `0.02` | 空间网格大小，单位经纬度度。越小候选更少、压力更低，但边界附近可能漏召回；越大候选更多、计算压力更高。 |
| `alongside.candidate.max-distance-nm` | `0.5` | 候选外圈最大距离，单位海里。超过该距离直接排除。调大提高召回，调小减少误报。 |
| `alongside.candidate.max-speed` / `alongside.candidate.max-speed-knots` | `4.0` | 构造候选 pair 时双方最大航速，单位 kn。两者为兼容参数，Web 模板会同时赋相同值。 |
| `alongside.candidate.max-time-gap-minutes` | `10` | 两船 AIS 点最大时间差。调大可适应稀疏 AIS，但会增加错配。 |
| `alongside.candidate.min-duration-minutes` | `20` | 输出弱候选的最短持续时长。调小可更快看到候选，误报也会增加。 |
| `alongside.candidate.min-points` | `3` | 输出弱候选的最少证据点数。测试样本稀疏时可临时降到 `2`。 |

### 5.4 正式搭靠确认

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.confirm.max-distance-nm` | `0.15` | 正式候选最大距离，单位海里。越小越严格。 |
| `alongside.confirm.max-speed` / `alongside.confirm.max-speed-knots` | `2.0` | 正式候选双方最大航速，单位 kn。两者为兼容参数，Web 模板会同时赋相同值。 |
| `alongside.confirm.max-relative-speed` / `alongside.confirm.max-relative-speed-knots` | `1.0` | 两船平均相对速度上限，单位 kn。两者为兼容参数，Web 模板会同时赋相同值。 |
| `alongside.confirm.max-time-gap-minutes` | `10` | 正式确认最大时间差配置，当前主要候选阶段已用 `candidate.max-time-gap-minutes` 拦截。建议与候选时间差保持一致。 |
| `alongside.confirm.high-confidence-time-gap-minutes` | `5` | 高覆盖证据点的时间差阈值。时间差小于该值的点越多，评分越高。 |
| `alongside.confirm.min-duration-minutes` | `30` | 输出正式候选的最短持续时长。 |
| `alongside.confirm.min-points` | `6` | 输出正式候选的最少证据点数。AIS 稀疏测试可降低，真实实时建议谨慎。 |

### 5.5 航向平行判断

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.heading.enabled` | `true` | 是否启用航向平行约束。AIS course 缺失时不会因为该项直接拒绝。 |
| `alongside.heading.parallel-threshold-degree` / `alongside.heading.parallel-threshold-degrees` | `30` | 判断平行/反向平行的航向差阈值。两船 course 差接近 `0` 或 `180` 都视为平行，差值在阈值内记为接受；两者为兼容参数。 |

### 5.6 状态、合并和 END 事件

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

### 5.7 船型优先级

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.ship-type-filter.enabled` | `false` | 是否启用低优先级船型过滤。默认关闭，避免因船型识别不准漏召回。 |
| `alongside.ship-type.high-priority` | `TANKER,CHEMICAL_TANKER,LNG,LPG,CARGO,BULK_CARRIER,CONTAINER,REEFER,FISH_CARRIER,BUNKER,SUPPLY,OFFSHORE_SUPPLY,UNKNOWN` | 高优先级船型集合。 |
| `alongside.ship-type.normal-service` | `TUG,PILOT,LAW_ENFORCEMENT,SAR,PORT_SERVICE,DREDGER,WORKBOAT` | 常规港作/服务船集合，与商船搭靠时通常降级。 |
| `alongside.ship-type.low-priority` | `PLEASURE_CRAFT,SAILING,LOCAL_PASSENGER` | 低优先级船型集合。启用船型过滤后，任一方为低优先级会被过滤。 |

船型来源优先使用 AIS `shipType`，无法识别时再从船名关键词识别，如 `TUG`、`PILOT`、`TANKER`、`LNG`、`BUNKER`、`FISH`、`CARGO` 等。

### 5.8 商船高匹配输出过滤

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.high-match.enabled` | `false` | 是否启用高匹配商船搭靠输出 profile。默认关闭以保持兼容。 |
| `alongside.high-match.confirmed-only` | `true` | 高匹配模式下只保留正式确认生命周期事件，不输出弱候选。 |
| `alongside.high-match.allowed-pair-priorities` | `HIGH` | 高匹配允许输出的 pair 优先级集合，逗号分隔。 |
| `alongside.high-match.exclude-unknown-type` | `true` | 任一船为未知船型时不输出高匹配事件。 |
| `alongside.high-match.exclude-normal-service` | `true` | 任一船为拖轮、引航、执法、SAR、港服、疏浚、工作船等常规服务船时不输出高匹配事件。 |
| `alongside.high-match.min-confidence` | `HIGH` | 最低轨迹置信等级。 |
| `alongside.high-match.min-track-coverage-ratio` | `0.85` | 最低轨迹覆盖率。 |
| `alongside.high-match.max-ais-gap-count` | `0` | 最大允许 AIS 轨迹断点数。 |
| `alongside.high-match.min-heading-parallel-ratio` | `0.85` | 航向已知时最低平行比例。 |
| `alongside.output.emit-weak` | `true` | 是否输出弱候选；高匹配验证建议设为 `false`。 |

高匹配过滤发生在事件构建之后、Kafka/DB 输出之前，因此会同时减少 Kafka 输出和数据库写入压力。未接入港区、泊位、锚地 geofence 前，不能承诺完全排除港内普通靠泊。

当前 jar 同时保留了速度和航向阈值的旧键与 `-knots`/复数别名，并按旧键优先读取。为避免只覆盖别名却仍命中旧默认值，本文的高匹配模板会把每一组兼容键同时设置为同一个值；它们不是两套独立规则，不能填写不同数值。

### 5.9 V4 静止停泊过滤

以下参数属于当前未提交的 V4 开发。使用模板 E 前必须上传包含本轮静止停泊过滤、空间邻域静止船统计和新 counter 的 jar；旧 jar 会忽略这些参数，仍按 V3 行为运行。

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.high-match.stationary-filter.enabled` | `false` | 只在高匹配输出模式下启用静止停泊过滤。生产灰度模板设为 `true`。 |
| `alongside.high-match.stationary-filter.max-speed-knots` | `0.2` | 单船判定为静止的最大速度，单位 kn。 |
| `alongside.high-match.stationary-filter.min-both-stationary-ratio` | `0.90` | 仅当双船同时静止的 evidence 比例达到该值时，才执行后续抑制。 |
| `alongside.high-match.stationary-filter.exclude-anchored-moored` | `true` | 双船近乎全程静止且窗口内出现 AIS nav status `1` 或 `5` 时，不输出该高匹配事件。 |
| `alongside.high-match.stationary-filter.exclude-multi-ship-cluster` | `true` | 双船近乎全程静止，且邻域静止船数达到阈值时，不输出该高匹配事件。 |
| `alongside.high-match.stationary-filter.cluster-radius-nm` | `0.15` | 围绕 pair 中心点统计邻域静止船的半径，单位海里。 |
| `alongside.high-match.stationary-filter.cluster-min-vessels` | `3` | 触发多船静止簇抑制的最小静止船数，包含当前 pair 两船。 |

过滤发生在事件构建后、`out.collect` 前：命中 `STATIONARY_ANCHORED_OR_MOORED` 或 `STATIONARY_MULTI_SHIP_CLUSTER` 时不会产生 Kafka 或 MySQL 事件。它是 AIS 规则的港内普通靠泊抑制，不能代替港区、泊位和锚地 geofence；没有 geofence 时仍应单独统计港内误报。

### 5.10 场景和风险等级约束

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

## 6. 数据库落库参数

| 参数 | 默认值 | 含义和配置建议 |
| --- | --- | --- |
| `alongside.db-sink.enabled` | `false` | 是否开启 MySQL 落库。只测 Kafka 输出时保持 `false`。 |
| `alongside.db-sink.detail-enabled` | `false` | 是否写入事件明细点。长期运行保持 `false`，只写事件表并用事件中的 MMSI、时间窗、空间锚点回查轨迹。 |
| `alongside.db-sink.url` | 空 | JDBC URL。本次直接在 Flink Web `Program Arguments` 中填写。 |
| `alongside.db-sink.username` | 空 | 数据库用户名。本次直接在 Flink Web `Program Arguments` 中填写。 |
| `alongside.db-sink.password` | 空 | 数据库密码。本次直接在 Flink Web `Program Arguments` 中填写。当前测试库密码见下方模板。 |
| `alongside.db-sink.batch-size` | `100` | 批量写入条数。小流量验证可用 `1` 方便立即看到结果；长期运行用默认或更大。 |
| `alongside.db-sink.flush-interval-ms` | `3000` | 批量写入最大等待时间。 |
| `alongside.db-sink.max-retries` | `3` | 写库失败最大重试次数。 |
| `alongside.db-sink.table-event` | `ship_realtime_alongside_event` | 事件摘要表。 |
| `alongside.db-sink.table-detail` | `ship_realtime_alongside_event_detail` | 事件明细表。 |
| `alongside.db-sink.max-detail-points-per-event` | `0` | 单事件最多保留多少个证据点。长期运行保持 `0`；只有临时核对完整 evidence 时才打开 detail，并控制运行时长。 |
| `alongside.db-sink.auto-create-table` | `true` | 启动时自动建表。受限数据库账号可设为 `false` 并提前建表。 |
| `alongside.db-sink.allowed-severity-levels` | 空 | 写入数据库的事件等级白名单，逗号分隔。为空表示所有等级都写库；配置 `L2` 表示只将 `severity_level=L2` 的搭靠事件写入数据库。Kafka 输出不受影响。 |

当前 MySQL 连接配置：

```text
--alongside.db-sink.url=jdbc:mysql://192.168.99.31:3309/dataanalysis?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true
--alongside.db-sink.username=root
--alongside.db-sink.password=dswybsbs@Hifleet2022
```

说明：

- 数据库名沿用当前项目文档中的 `dataanalysis`；如果实际部署库名不同，只替换 URL 中 `/dataanalysis` 这一段。
- `serverTimezone=Asia/Shanghai` 用于 JDBC 时间解析兼容；`connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true` 用于强制连接会话使用北京时间，避免 MySQL `CURRENT_TIMESTAMP` 生成的 `created_at` / `updated_at` 少 8 小时。
- 该密码是明文测试/内网连接配置，复制到工单、截图或外部文档前请脱敏。

`event_id` 采用稳定幂等规则：

```text
md5(rule_version + "|" + pair_id + "|" + event_sub_type + "|" + start_time + "|" + end_time)
```

摘要表对 `event_id` 建唯一键，重复消费、Flink 重启或 DB sink 重试时执行 upsert，避免重复插入。

## 7. Flink 运行参数

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
| `flink.checkpoint.timeout-ms` | `600000` | checkpoint 超时时间。状态较大或 DB 明细开启时建议使用 10 分钟，避免短时高峰导致误超时。 |
| `flink.checkpoint.externalized-cleanup` | `DELETE_ON_CANCELLATION` | cancel 作业时是否删除外部 checkpoint。 |

### 7.1 事件输出、Kafka 与 MySQL 验收

`alongside-pair-detection -> kafka sink / jdbc sink` 是终端算子链，页面上的 `numRecordsOut` 或 `numRecordsOutPerSecond=0` 只表示没有下游算子，不代表没有写出事件。应进入该 vertex 的 Metrics 页面，按全部 subtask 汇总下列指标：

| 指标 | 含义 | 用途 |
| --- | --- | --- |
| `alongside-pair-detection.emittedEventCount` | pair detector 实际 `out.collect` 的事件累计数。 | 最直接的 Flink 侧事件输出数。 |
| `alongside-pair-detection.confirmedCandidateCount` | 形成正式确认候选的累计数。 | 对比确认事件与最终输出事件，判断高匹配过滤强度。 |
| `alongside-pair-detection.suppressedStationaryMooringCount` | V4 因双船静止且锚泊/系泊状态被抑制的累计数。 | 只在包含 V4 的 jar 且过滤开启时出现。 |
| `alongside-pair-detection.suppressedMultiShipClusterCount` | V4 因多船静止簇被抑制的累计数。 | 判断港内或锚地静止簇的抑制量。 |
| `kafka-alongside-event-sink__Writer.KafkaProducer.record-send-total` | Kafka producer 发起发送的累计记录数。 | 验证 Kafka 分支收到事件。 |
| `Sink__jdbc-alongside-event-sink.numRecordsIn` | JDBC sink 收到的累计事件数。 | 验证 DB 分支接收到事件；若配置 severity 白名单，它不等同于实际入库行数。 |

MySQL 是最终落库口径，优先按写入时间查询，而不是按 AIS 事件时间查询：

```sql
SELECT COUNT(*) AS event_count
FROM ship_realtime_alongside_event
WHERE rule_version = 'ALONGSIDE_V4_STATIONARY_FILTER'
  AND updated_at >= NOW() - INTERVAL 30 MINUTE;

SELECT COUNT(*) AS detail_rows
FROM ship_realtime_alongside_event_detail
WHERE created_at >= NOW() - INTERVAL 30 MINUTE;
```

长期高匹配作业的验收目标是：detail 表不增长、checkpoint `Failed=0` 且 `Completed` 持续增长、当前运行 group 的 lag 在 15 到 30 分钟内不持续单调上升，并人工抽样 20 条事件确认至少 80% 可回放为长时间近距离低速/静止、近似平行的商船 pair。服务船/港服事件误报目标为 0；港内普通靠泊在未接入 geofence 前单独统计。

反压、Kafka group 和 checkpoint 的完整巡检请参考 [alongside-backpressure-optimization-runbook.md](alongside-backpressure-optimization-runbook.md)；单次 Job 页面字段解读见 [flink-job-30cebce2a67163d0352b83eaef9d8e7e-status-guide.md](flink-job-30cebce2a67163d0352b83eaef9d8e7e-status-guide.md)。

## 8. 自主调参方法

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
| 验证数据库摘要和明细 | 仅短时压测使用：`alongside.db-sink.enabled=true`，`detail-enabled=true`，`max-detail-points-per-event` 按需要配置。 |
| 只将 L2 事件写入数据库 | `alongside.db-sink.allowed-severity-levels=L2`。该配置只影响 DB sink，不影响搭靠 Kafka topic。 |
| 控制测试范围 | 使用 `pipeline.scope.mmsi-whitelist` 或经纬度范围参数，只消费目标船或目标区域。 |
| 缓解实时堆积和反压 | 使用 `flink.parallelism=8`，只写事件表：`detail-enabled=false`、`max-detail-points-per-event=0`，`batch-size=100`，`flush-interval-ms=5000`。 |
| 只识别高匹配度商船搭靠 | 参考 [commercial-alongside-high-match-rule-design.md](commercial-alongside-high-match-rule-design.md)，先按商船高匹配 profile 收紧船型、距离、速度、持续时长和轨迹质量。 |
| 只分析当前最新 AIS | 使用从未用过的新 `kafka.group.id`，并设置 `kafka.starting-offsets=latest`。这会跳过当前 Kafka 积压历史数据。 |
| 继续处理历史积压 | 沿用已有 `kafka.group.id`，并设置 `kafka.starting-offsets=committed`。这不会丢历史数据，但事件 `created_at/updated_at` 可能不是当前时刻。 |
| 清理旧测试 group | 确认 group `members=0` 且 `state=EMPTY/DEAD` 后，可删除旧 `alongside-*` consumer group offset 元数据；不要删除当前运行 group。 |

范围过滤参数：

| 参数 | 含义 |
| --- | --- |
| `pipeline.scope.mmsi-whitelist` | MMSI 白名单，逗号分隔。为空表示不过滤。 |
| `pipeline.scope.min-lon` / `pipeline.scope.max-lon` | 经度范围。 |
| `pipeline.scope.min-lat` / `pipeline.scope.max-lat` | 纬度范围。 |

## 9. 参数模板

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

每次部署建议替换 `<RUN_ID>`，例如 `202607071530`。数据库 URL、用户名、密码已按当前 MySQL 环境填入；不要复用旧测试 group。

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=LIVE_RECENTSHIPS_DATA
--kafka.group.id=alongside-web-db-summary-<RUN_ID>
--kafka.client.id=alongside-web-db-summary-<RUN_ID>
--kafka.starting-offsets=latest
--stop-point.enabled=false
--alongside.enabled=true
--alongside.output.topic=RELIABLE_ALONGSIDE_EVENTS_TEST
--alongside.db-sink.enabled=true
--alongside.db-sink.detail-enabled=false
--alongside.db-sink.url=jdbc:mysql://192.168.99.31:3309/dataanalysis?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true
--alongside.db-sink.username=root
--alongside.db-sink.password=dswybsbs@Hifleet2022
--alongside.db-sink.batch-size=1
--alongside.db-sink.flush-interval-ms=1000
--alongside.db-sink.table-event=ship_realtime_alongside_event
--alongside.db-sink.auto-create-table=true
--alongside.db-sink.allowed-severity-levels=L2
--flink.parallelism=1
```

适用场景：先验证摘要表 upsert 和事件数量，不写明细，且只将 `severity_level=L2` 的搭靠事件写入数据库，便于排查。

### 模板 E：推荐，V4 商船高匹配实时灰度 + MySQL 事件表落库

第一轮验证“只识别高匹配度商船搭靠事件”时优先使用该模板。它使用全新 group + `latest`，跳过历史 backlog，只观察作业启动后的实时 AIS；同时开启高匹配输出过滤、V4 静止停泊过滤、关闭弱候选、只写事件表，不写明细表。提交前确认上传的 jar 已包含本轮 V4 改动，否则静止停泊参数不会生效。

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=LIVE_RECENTSHIPS_DATA
--kafka.group.id=flink-alongside
--kafka.client.id=alonside-ceshi
--kafka.starting-offsets=latest
--stop-point.enabled=false
--alongside.enabled=true
--alongside.output.topic=RELIABLE_ALONGSIDE_EVENTS_TEST
--alongside.rule-version=ALONGSIDE_V4_STATIONARY_FILTER
--alongside.high-match.enabled=true
--alongside.high-match.confirmed-only=true
--alongside.high-match.allowed-pair-priorities=HIGH
--alongside.high-match.exclude-unknown-type=true
--alongside.high-match.exclude-normal-service=true
--alongside.high-match.min-confidence=HIGH
--alongside.high-match.min-track-coverage-ratio=0.85
--alongside.high-match.max-ais-gap-count=0
--alongside.high-match.min-heading-parallel-ratio=0.85
--alongside.high-match.stationary-filter.enabled=true
--alongside.high-match.stationary-filter.max-speed-knots=0.2
--alongside.high-match.stationary-filter.min-both-stationary-ratio=0.90
--alongside.high-match.stationary-filter.exclude-anchored-moored=true
--alongside.high-match.stationary-filter.exclude-multi-ship-cluster=true
--alongside.high-match.stationary-filter.cluster-radius-nm=0.15
--alongside.high-match.stationary-filter.cluster-min-vessels=3
--alongside.output.emit-weak=false
--alongside.ship-type-filter.enabled=true
--alongside.candidate.max-distance-nm=0.25
--alongside.candidate.max-speed=3.0
--alongside.candidate.max-speed-knots=3.0
--alongside.candidate.max-time-gap-minutes=5
--alongside.candidate.min-duration-minutes=30
--alongside.candidate.min-points=5
--alongside.confirm.max-distance-nm=0.08
--alongside.confirm.max-speed=1.5
--alongside.confirm.max-speed-knots=1.5
--alongside.confirm.max-relative-speed=0.5
--alongside.confirm.max-relative-speed-knots=0.5
--alongside.confirm.high-confidence-time-gap-minutes=2
--alongside.confirm.min-duration-minutes=45
--alongside.confirm.min-points=8
--alongside.heading.enabled=true
--alongside.heading.parallel-threshold-degree=20
--alongside.heading.parallel-threshold-degrees=20
--alongside.ais-gap-minutes=15
--alongside.output.update-interval-minutes=30
--alongside.db-sink.enabled=true
--alongside.db-sink.detail-enabled=false
--alongside.db-sink.url=jdbc:mysql://192.168.99.31:3309/dataanalysis?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true
--alongside.db-sink.username=root
--alongside.db-sink.password=dswybsbs@Hifleet2022
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

适用场景：当前最推荐的 V4 商船高匹配规则验收。第一轮建议运行 2 到 4 小时，重点观察 `ship_realtime_alongside_event_detail` 不增长、checkpoint 无持续失败、当前 group lag 不持续单调上升，并同时观察 `suppressedStationaryMooringCount`、`suppressedMultiShipClusterCount` 是否符合港内静止船分布，再人工抽样核对事件轨迹。

### 模板 F：继续追历史积压，开启搭靠 Kafka + MySQL 事件表落库

如果要追历史积压，`<EXISTING_GROUP_ID>` 必须是明确要继续使用的 group；不要填写刚刚清理过的旧测试 group。

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
--alongside.db-sink.url=jdbc:mysql://192.168.99.31:3309/dataanalysis?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true
--alongside.db-sink.username=root
--alongside.db-sink.password=dswybsbs@Hifleet2022
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

适用场景：需要保留并继续处理已有 Kafka 积压数据，不希望跳过历史 offset。本模板不写 `ship_realtime_alongside_event_detail`，事件表会保存 MMSI、起止时间、起止/中心经纬度和轨迹质量指标，前端按这些锚点到外部 AIS 轨迹系统回查轨迹。使用 `committed` 会从 group 已提交位点继续消费；当 Kafka lag 较大时，DB 中新写入或更新的事件可能对应较早的 AIS 轨迹时间。

### 模板 G：只分析当前最新 AIS，事件创建时间接近当前

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
--alongside.db-sink.url=jdbc:mysql://192.168.99.31:3309/dataanalysis?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true
--alongside.db-sink.username=root
--alongside.db-sink.password=dswybsbs@Hifleet2022
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

适用场景：只关心作业启动后的最新实时 AIS，不处理当前 Kafka 积压历史数据。必须使用已包含北京时间解析和未来点过滤逻辑的新 jar、从未使用过的新 `kafka.group.id`、`kafka.starting-offsets=latest`，并使用强制北京时间会话的 JDBC URL；否则 `latest` 或 `created_at` / `updated_at` 可能不会按预期表现。该模板会跳过历史 backlog；新插入事件的 `created_at` 应接近当前 MySQL 北京时间，但 `start_time/end_time` 仍是 AIS 轨迹时间，不是系统处理时间。

### 模板 H：完整明细压测模板，不建议长期运行

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=LIVE_RECENTSHIPS_DATA
--kafka.group.id=alongside-web-db-detail-full-detail-<RUN_ID>
--kafka.client.id=alongside-web-db-detail-full-detail-<RUN_ID>
--kafka.starting-offsets=latest
--stop-point.enabled=false
--alongside.enabled=true
--alongside.output.topic=RELIABLE_ALONGSIDE_EVENTS_TEST
--alongside.db-sink.enabled=true
--alongside.db-sink.detail-enabled=true
--alongside.db-sink.url=jdbc:mysql://192.168.99.31:3309/dataanalysis?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true
--alongside.db-sink.username=root
--alongside.db-sink.password=dswybsbs@Hifleet2022
--alongside.db-sink.batch-size=20
--alongside.db-sink.flush-interval-ms=3000
--alongside.db-sink.max-detail-points-per-event=200
--alongside.db-sink.table-event=ship_realtime_alongside_event
--alongside.db-sink.table-detail=ship_realtime_alongside_event_detail
--alongside.db-sink.auto-create-table=true
--alongside.db-sink.allowed-severity-levels=L2
--flink.parallelism=2
```

适用场景：只在短时间压测或核对完整 evidence 明细时使用。该模板会显著增加 pair state、Checkpoint 体积和 MySQL detail 表写入量，容易在实时流量下造成反压和 Kafka lag 增长。

### 模板 I：限定 MMSI 或区域的小范围验证

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

## 10. 当前搭靠事件评分规则说明

### 10.1 先判定是否形成事件

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

### 10.2 分数如何计算

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
- V4 高匹配模式启用静止停泊过滤后，双船静止比例达到阈值且命中锚泊/系泊状态或多船静止簇时，事件会在输出前被抑制，不再只是降分。
- 若置信度为 `LOW`，分数再乘以 `0.6`。
- 若事件是弱候选，分数最高封顶 `20`，等级固定按弱候选处理为 `L1`，随后仍受无场景数据封顶影响。
- 最终分数限制在 `0..100`。

### 10.3 置信度如何判断

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

### 10.4 风险等级如何映射

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

### 10.5 输出字段速读

| 字段 | 含义 |
| --- | --- |
| `event_id` | 幂等事件 ID，用于 Kafka 重放和 DB upsert 去重。 |
| `event_type` | 当前为 `ALONGSIDE_CANDIDATE`。 |
| `event_sub_type` | `WEAK_ALONGSIDE_CANDIDATE`、`ALONGSIDE_CONFIRMED_CANDIDATE`、`ALONGSIDE_UPDATE`、`ALONGSIDE_END`。 |
| `source_mode` | 当前为 `REALTIME_AUTO_BUCKET`。 |
| `rule_version` | 规则版本。 |
| `pair_id` | `minMmsi-maxMmsi`，避免 A-B/B-A 双写。 |
| `mmsi_a` / `mmsi_b` | 搭靠 pair 的两船 MMSI，也是回查轨迹的主查询键。 |
| `start_time` / `end_time` | 搭靠事件时间窗。回查轨迹时默认向前/向后各扩 10 分钟。 |
| `start_lon` / `start_lat` | 事件开始时两船中心点，可用于地图起点定位。 |
| `end_lon` / `end_lat` | 事件结束时两船中心点，可用于地图终点定位。 |
| `center_lon` / `center_lat` | 事件窗口中心位置，地图列表和详情页优先用它定位。 |
| `duration_minutes` | pair 证据窗口持续时间。 |
| `point_count` | pair 证据点数量。 |
| `min_distance_nm` / `max_distance_nm` / `avg_distance_nm` | 两船距离统计，单位海里。 |
| `avg_speed` | 事件窗口内两船平均单船速度，单位 kn。 |
| `avg_relative_speed` | 平均相对速度，单位 kn。 |
| `heading_parallel_ratio` | 航向平行接受比例，无航向数据时为空。 |
| `ais_gap_count` / `max_ais_gap_minutes` | 事件窗口内 AIS 断点数量和最大断点分钟数，用于判断轨迹复现质量。 |
| `track_coverage_ratio` | 按持续时长和证据点数估算的轨迹覆盖率。 |
| `pair_priority` | pair 优先级，可能为 `HIGH`、`MEDIUM`、`DOWNGRADED` 等。 |
| `severity_level` | 轻量风险等级，受场景封顶约束。 |
| `event_score` | 轻量规则分。 |
| `confidence_level` | `LOW`、`MEDIUM`、`HIGH`。 |
| `exclusion_flag` / `exclusion_reason` | 降权或结束原因。 |
| `score_detail_json` | 当前包含 `track_coverage_ratio`、`heading_parallel_ratio`、`ais_gap_count` 等解释字段。 |

### 10.6 时间字段速读

| 字段 | 含义 |
| --- | --- |
| `start_time` / `end_time` | 事件对应的 AIS 轨迹时间，来自原始 AIS `updatetime`。即使实时处理，也不等于系统当前时间。 |
| `created_at` | MySQL 首次插入该 `event_id` 的时间，由 MySQL `CURRENT_TIMESTAMP` 产生，代码不显式写入。 |
| `updated_at` | MySQL 最近一次 upsert 更新该 `event_id` 的时间，由 MySQL `CURRENT_TIMESTAMP` 产生，判断当前 Job 是否仍在写入时优先看它。 |

AIS `updatetime` 解析规则：

- 带时区的时间按原始时区解析，例如 `2026-07-07T11:10:51Z` 或 `2026-07-07T19:10:51+08:00`。
- 不带时区的 SQL 时间按北京时间 `Asia/Shanghai` 解析，例如 `2026-07-07 19:10:51` 会解析为 `2026-07-07T11:10:51Z`。
- 解析后若 AIS 点时间晚于当前处理时间 5 分钟以上，会在 `filter-valid-ais` 阶段过滤，避免生成未来事件；该行为要求线上已部署包含北京时间解析和未来点过滤逻辑的新 jar。

如果使用 `committed` 且 Kafka lag 很大，作业会先处理历史 offset，`created_at/updated_at` 可能不是当前 14 点附近。若要求事件创建时间接近当前，使用“模板 G”的新 jar + 新 group + `latest` + 北京时间 JDBC URL；若要同时验证商船高匹配规则，优先使用“模板 E”。

### 10.7 时间异常排查清单

| 现象 | 优先检查项 |
| --- | --- |
| `start_time` / `end_time` 跑到当前时间之后 | 确认线上使用新 jar；确认没有继续处理旧 backlog；抽样检查 Kafka 原始 `updatetime` 是否本身就是未来时间。 |
| `created_at` / `updated_at` 比北京时间少 8 小时 | 确认 JDBC URL 包含 `connectionTimeZone=Asia/Shanghai` 和 `forceConnectionTimeZoneToSession=true`；必要时查询 MySQL session `time_zone`。 |
| 只想分析当前最新 AIS | 使用从未使用过的新 `kafka.group.id`，并设置 `kafka.starting-offsets=latest`；不要复用旧测试 group。 |
| 需要追历史积压 | 使用已有 group + `kafka.starting-offsets=committed`，并接受事件时间和写入时间可能不代表当前最新数据。 |

### 10.8 轨迹复现查询契约

生产长期运行不把 evidence 明细点写入 `ship_realtime_alongside_event_detail`。事件详情页或排查工具需要展示轨迹时，按 `ship_realtime_alongside_event` 的锚点到外部 AIS 轨迹系统查询：

```text
ownMmsi = mmsi_a
targetMmsi = mmsi_b
queryStartTime = start_time - 10 minutes
queryEndTime = end_time + 10 minutes
mapAnchor = center_lon/center_lat
fallbackAnchor = midpoint(start_lon/start_lat, end_lon/end_lat)
```

展示时建议同时读取两船在查询时间窗内的 AIS 点，按 `updatetime` 排序后绘制轨迹；若 `track_coverage_ratio < 0.6`、`ais_gap_count > 0` 或 `max_ais_gap_minutes` 较大，应在页面标记“轨迹可能不完整”。`ship_realtime_alongside_event_detail` 只作为短时 evidence 调试表，不作为 AIS 原始轨迹归档。

## 11. 提交前检查清单

- 确认 `kafka.group.id` 是本次测试专用，不复用生产 group。
- 确认 `kafka.topic` 是目标输入 topic，回放测试不要误连生产 topic。
- 确认输出 topic 是测试 topic，避免污染正式结果。
- 本次只跑搭靠事件时，确认包含 `--stop-point.enabled=false`。
- 开启搭靠时必须有 `--alongside.enabled=true`。
- 如果开启 DB sink，确认数据库账号、表名、权限和密码传入方式。
- 本文件已按当前要求写入内网 MySQL 明文密码；复制到截图、工单、外部文档或公开日志前必须脱敏。
- 小样本首次测试可用 `flink.parallelism=1`；真实 `LIVE_RECENTSHIPS_DATA` 长时间运行建议从 `8` 起步，若 `spatial-candidate-recall` 长期 high backpressure，再提高到 `12`。
- 长期运行保持 `detail-enabled=false` 和 `max-detail-points-per-event=0`；需要完整 evidence 时再短时间启用 legacy 明细模板压测。
- 如果要求只分析当前最新 AIS，必须使用新的 `kafka.group.id` + `kafka.starting-offsets=latest`；不要复用已有 group。
- 如果要求不丢历史积压，使用已有 group + `committed`，并接受事件创建/更新时间可能滞后当前时间。
- 如果要求 `created_at` / `updated_at` 使用北京时间，确认 JDBC URL 包含 `connectionTimeZone=Asia/Shanghai` 和 `forceConnectionTimeZoneToSession=true`。
- 使用模板 E 时确认 jar 已包含 V4 静止停泊过滤；在末端 vertex Metrics 中汇总 `emittedEventCount`、两个 `suppressedStationary*Count`、Kafka producer 发送数和 JDBC sink 输入数，不要仅根据终端 sink 的 `numRecordsOutPerSecond` 判断是否写入。
- 每次测试使用新的 `kafka.group.id`，不要复用旧测试 group；确认旧 group 已无成员后，可在 Kafka UI 删除其 consumer group offset 元数据，避免旧 lag 干扰排查。
- 长时间运行不要开启 `flink.debug.print=true`。
- 如果从 savepoint 恢复，确认 Entry Class、算子链和状态兼容性，再考虑 Savepoint Path。
