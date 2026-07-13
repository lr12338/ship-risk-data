# 商船搭靠事件高匹配规则设计

本文用于定义下一轮 `reliable-alongside-flink` 商船搭靠事件精确化规则。目标是在不恢复 `ship_realtime_alongside_event_detail` 明细点常态写入的前提下，减少数据库写入量和误报，只输出高匹配度商船搭靠事件。

## 1. 当前开发背景

- 当前 Flink 搭靠链路已经从“摘要表 + 明细轨迹点表”调整为长期只写 `ship_realtime_alongside_event`。
- `ship_realtime_alongside_event_detail` 不再作为生产常态写入目标；轨迹复现通过事件表中的 `mmsi_a`、`mmsi_b`、`start_time`、`end_time`、`start_lon/start_lat`、`end_lon/end_lat`、`center_lon/center_lat` 到外部 AIS 轨迹系统回查。
- 之前高数据量问题主要来自：
  - 弱候选、更新事件和明细点同时写入。
  - `detail-enabled=true` 时 pair state 保存 evidence 点，checkpoint state 增大。
  - JDBC sink 与 pair detection 同并行度运行，p8 时等于 8 路并发写 MySQL，曾触发 deadlock 和 checkpoint failure。
- 当前代码已有规则能力：
  - `candidate.*` 负责外圈召回。
  - `confirm.*` 负责正式候选确认。
  - `ship-type-filter.*` 和 `PairPriority` 负责船型优先级。
  - `confidence_level`、`track_coverage_ratio`、`ais_gap_count` 可用于高匹配筛选。

## 2. 权威依据和工程解释

权威资料给出的是航行安全、AIS 数据含义和 STS 作业边界，不会直接给出“保险风险商船搭靠事件”的算法阈值。工程规则需要把这些事实转换成 AIS 可计算条件。

| 来源 | 可采用事实 | 工程解释 |
| --- | --- | --- |
| IMO AIS 说明：<https://www.imo.org/en/ourwork/safety/pages/ais.aspx> | AIS 可自动提供船舶身份、类型、位置、航向、航速、导航状态等信息。 | 本系统可以基于 MMSI、船型、经纬度、速度、航向、导航状态定义搭靠候选。 |
| IMO SOLAS V/19 AIS 配备要求，见同一 IMO AIS 页面 | 国际航行 300 总吨及以上船舶、非国际航行 500 总吨及以上货船、所有客船需要配备 AIS。 | 商船搭靠识别应优先面向 AIS Class A/大型商船，不应把小型休闲船、港服小船作为核心目标。 |
| eCFR 33 CFR 164.46：<https://www.ecfr.gov/current/title-33/chapter-I/subchapter-P/part-164/section-164.46> | 美国规则要求 65 英尺及以上商业自航船、特定拖船、客船、危险货物/散装易燃液体运输船等配备 AIS。 | “商船”工程口径可包含货船、油化气船、散货、集装箱、冷藏、补给、加油等类型，但应排除拖轮、引航、执法、SAR、港服、休闲船等常规服务船。 |
| eCFR 33 CFR Part 156 Subpart C：<https://www.ecfr.gov/current/title-33/part-156/subpart-C> | STS/lightering 作业要求船舶安全并靠，接近阶段要求近似平行，货物系统开启前服务船需安全系靠。 | 高匹配搭靠事件应表现为近距离、低速/相对低速、持续时间足够、航向近似平行或静止、轨迹覆盖良好。 |
| IMO MARPOL Annex I：<https://www.imo.org/en/ourwork/environment/pages/oilpollution-default.aspx> | 油轮运输和操作污染是被监管的海事风险领域。 | 油轮、化学品船、LNG/LPG、加油船等组合应具有更高风险优先级。 |

结论：本项目中的“商船搭靠事件”不是所有近距离船舶接触，而是两艘商用船舶在非普通靠泊/港服场景下，长时间近距离、低相对速度、轨迹质量足够、船型组合具备风险价值的并靠或疑似船到船接触事件。

## 3. 商船搭靠事件定义

### 3.1 必要条件

一个高匹配商船搭靠事件必须同时满足：

- 两船都有有效 MMSI，且 `mmsi_a != mmsi_b`。
- 两船至少一方为高风险商船类型：`TANKER`、`CHEMICAL_TANKER`、`LNG`、`LPG`、`BUNKER`、`CARGO`、`BULK_CARRIER`、`CONTAINER`、`REEFER`、`FISH_CARRIER`、`SUPPLY`、`OFFSHORE_SUPPLY`。
- 两船均不属于明确排除类型：`TUG`、`PILOT`、`LAW_ENFORCEMENT`、`SAR`、`PORT_SERVICE`、`DREDGER`、`WORKBOAT`、`PLEASURE_CRAFT`、`SAILING`、`LOCAL_PASSENGER`。
- pair 在事件窗口内持续近距离，且最大距离、平均距离、最小距离均满足高匹配阈值。
- 两船速度和相对速度足够低，排除同向近距离航行。
- 轨迹覆盖质量足够高，排除 AIS 稀疏导致的偶然错配。
- 若航向数据可用，航向平行比例达到高匹配阈值。

### 3.2 排除条件

以下场景不应作为高匹配商船搭靠事件输出：

- 拖轮、引航、执法、SAR、港服、疏浚等服务船与商船的正常作业。
- 两船高速或中速同向近距离航行。
- AIS 点稀疏、时间差大、轨迹断点多的低可信 pair。
- 明确在港区泊位、码头、锚地内的普通靠泊/靠泊排队场景；该项需要港区/泊位/锚地 geofence 数据，否则 AIS 规则只能降权，不能可靠排除。
- 船型未知且没有其他强证据的 pair。未知船型可以进入召回，但不应进入高匹配输出。

## 4. 建议高匹配参数口径

当前默认参数偏向召回。新增“商船高匹配 profile”时建议使用以下口径。

| 参数 | 当前默认 | 高匹配建议 | 目的 |
| --- | --- | --- | --- |
| `alongside.ship-type-filter.enabled` | `false` | `true` | 开启商船/服务船筛选。 |
| `alongside.candidate.max-distance-nm` | `0.5` | `0.25` | 减少外圈候选数量。 |
| `alongside.candidate.max-speed-knots` | `4.0` | `3.0` | 排除明显航行中的船。 |
| `alongside.candidate.max-time-gap-minutes` | `10` | `5` | 减少时间错配。 |
| `alongside.candidate.min-duration-minutes` | `20` | `30` | 过滤短暂擦肩。 |
| `alongside.candidate.min-points` | `3` | `5` | 提高证据密度。 |
| `alongside.confirm.max-distance-nm` | `0.15` | `0.08` | 高匹配并靠距离，约 148 米。 |
| `alongside.confirm.max-speed-knots` | `2.0` | `1.5` | 强调低速/停泊状态。 |
| `alongside.confirm.max-relative-speed-knots` | `1.0` | `0.5` | 排除同向近距离航行。 |
| `alongside.confirm.high-confidence-time-gap-minutes` | `5` | `2` | 保证两船点位接近同步。 |
| `alongside.confirm.min-duration-minutes` | `30` | `45` | 只输出持续接触事件。 |
| `alongside.confirm.min-points` | `6` | `8` | 提高确认门槛。 |
| `alongside.heading.parallel-threshold-degrees` | `30` | `20` | 强化并靠/平行特征。 |
| `alongside.ais-gap-minutes` | `30` | `15` | 更严格识别轨迹断点。 |
| `alongside.output.update-interval-minutes` | `5` | `30` | 减少 UPDATE 写入量。 |

高匹配输出 profile 已具备以下配置项；默认关闭，不影响原有搭靠候选输出：

| 新配置 | 推荐默认 | 说明 |
| --- | --- | --- |
| `alongside.high-match.enabled` | `false` | 启用高匹配输出 profile。 |
| `alongside.high-match.confirmed-only` | `true` | 只输出 `ALONGSIDE_CONFIRMED_CANDIDATE` 和必要的 `ALONGSIDE_END`，不输出弱候选。 |
| `alongside.high-match.min-confidence` | `HIGH` | 只输出高置信事件。 |
| `alongside.high-match.min-track-coverage-ratio` | `0.85` | 要求轨迹覆盖率足够高。 |
| `alongside.high-match.max-ais-gap-count` | `0` | 高匹配事件不允许 AIS 断点。 |
| `alongside.high-match.min-heading-parallel-ratio` | `0.85` | 航向已知时要求平行比例。 |
| `alongside.high-match.exclude-unknown-type` | `true` | 未知船型不进入高匹配输出。 |
| `alongside.high-match.exclude-normal-service` | `true` | 拖轮、引航、执法、港服等常规服务船不进入高匹配输出。 |
| `alongside.high-match.allowed-pair-priorities` | `HIGH` | 仅允许高优先级船型组合。 |
| `alongside.high-match.stationary-filter.enabled` | `false` | 是否启用静止停泊过滤；商船高匹配生产灰度建议开启。 |
| `alongside.high-match.stationary-filter.max-speed-knots` | `0.2` | 判断单船静止的最大速度，单位 kn。 |
| `alongside.high-match.stationary-filter.min-both-stationary-ratio` | `0.90` | 双船都静止的 evidence 比例达到该值后才应用过滤。 |
| `alongside.high-match.stationary-filter.exclude-anchored-moored` | `true` | 双船近乎全程静止且出现 nav status `1/5` 时不输出。 |
| `alongside.high-match.stationary-filter.exclude-multi-ship-cluster` | `true` | 双船近乎全程静止且邻域静止船数达到阈值时不输出。 |
| `alongside.high-match.stationary-filter.cluster-radius-nm` | `0.15` | 周边静止船舶统计半径，单位海里。 |
| `alongside.high-match.stationary-filter.cluster-min-vessels` | `3` | 多船静止停泊簇的最小船舶数，包含当前 pair 两船。 |
| `alongside.output.emit-weak` | `true` | 控制是否输出弱候选；高匹配 profile 建议显式设为 `false`。 |
| `alongside.geo.exclude-port-berth-enabled` | `false` | 有泊位/码头 geofence 时，排除普通靠泊。 |
| `alongside.geo.exclude-anchorage-enabled` | `false` | 有锚地 geofence 时，排除常规锚泊并排。 |

## 5. 推荐 Flink Web 参数模板

该模板用于“只识别高匹配商船搭靠事件”的第一轮验证。它同时启用高匹配输出过滤并收紧现有召回/确认规则。

```text
--kafka.bootstrap.servers=192.168.99.92:9092
--kafka.topic=LIVE_RECENTSHIPS_DATA
--kafka.group.id=alongside-commercial-highmatch-<RUN_ID>
--kafka.client.id=alongside-commercial-highmatch-1
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
--alongside.candidate.max-speed-knots=3.0
--alongside.candidate.max-time-gap-minutes=5
--alongside.candidate.min-duration-minutes=30
--alongside.candidate.min-points=5
--alongside.confirm.max-distance-nm=0.08
--alongside.confirm.max-speed-knots=1.5
--alongside.confirm.max-relative-speed-knots=0.5
--alongside.confirm.high-confidence-time-gap-minutes=2
--alongside.confirm.min-duration-minutes=45
--alongside.confirm.min-points=8
--alongside.heading.enabled=true
--alongside.heading.parallel-threshold-degrees=20
--alongside.ais-gap-minutes=15
--alongside.output.update-interval-minutes=30
--alongside.db-sink.enabled=true
--alongside.db-sink.detail-enabled=false
--alongside.db-sink.max-detail-points-per-event=0
--alongside.db-sink.url=<JDBC_URL>
--alongside.db-sink.username=<JDBC_USERNAME>
--alongside.db-sink.password=<JDBC_PASSWORD>
--alongside.db-sink.batch-size=100
--alongside.db-sink.flush-interval-ms=5000
--alongside.db-sink.table-event=ship_realtime_alongside_event
--alongside.db-sink.auto-create-table=true
--alongside.db-sink.allowed-severity-levels=L2
--flink.parallelism=8
--flink.checkpoint.interval-ms=180000
--flink.checkpoint.timeout-ms=600000
--flink.checkpoint.min-pause-ms=60000
```

注意：高匹配过滤会作用于 Kafka 输出和 DB sink 之前；DB 侧仍建议保留 `allowed-severity-levels=L2` 作为落库保护。

## 6. 代码实现说明

本轮已按最小修改实现：

1. 增加高匹配输出过滤层，不改空间召回主链路。
   - 在 `AlongsidePairDetectorProcessFunction.buildEventIfReady` 生成事件后，根据高匹配配置决定是否 `out.collect(event)`。
   - 高匹配模式不输出弱候选；非高匹配模式可用 `alongside.output.emit-weak=false` 单独关闭弱候选。
2. 扩展 `AlongsideConfig`。
   - 增加 `high-match.*` 和 `output.emit-weak` 参数。
   - 默认关闭高匹配模式，保持现有行为兼容。
3. 强化 PairPriority 过滤。
   - 高匹配模式下只允许 `PairPriority.HIGH`。
   - `UNKNOWN` 船型默认不输出高匹配事件。
4. 引入轨迹质量门槛。
   - `confidence_level >= HIGH` 或 `track_coverage_ratio >= 0.85`。
   - `ais_gap_count <= 0`。
   - 航向已知时 `heading_parallel_ratio >= 0.85`。
5. 可选接入 geofence。
   - 没有泊位/锚地数据时，不要声称可以可靠排除普通靠泊。
   - 有 geofence 后再增加 `geo.exclude-port-berth-enabled`、`geo.exclude-anchorage-enabled`。

## 7. 测试和验收标准

### 单元测试

- 商船高匹配正例：
  - `TANKER + BUNKER` 或 `TANKER + TANKER`。
  - 持续 45 分钟以上。
  - `max_distance_nm <= 0.08`。
  - `avg_relative_speed <= 0.5`。
  - `point_count >= 8`。
  - `track_coverage_ratio >= 0.85`。
  - `ais_gap_count = 0`。
- 排除服务船：
  - `TANKER + TUG`、`CARGO + PILOT`、`CARGO + PORT_SERVICE` 不输出高匹配。
- 排除同向航行：
  - 距离近但速度高于 `1.5 kn` 或相对速度高于 `0.5 kn` 不输出。
- 排除低质量轨迹：
  - AIS gap、低覆盖、点数不足、时间差过大不输出。
- 兼容性：
  - `high-match.enabled=false` 时原有规则测试不变。

### 线上验收

- `ship_realtime_alongside_event` 写入量明显低于当前 L2 事件量。
- `ship_realtime_alongside_event_detail` 不增长。
- Flink checkpoint `Failed` 不持续增长，最新 checkpoint duration 稳定低于 60 秒为目标。
- Kafka group lag 在 15 到 30 分钟窗口内保持可控或回落。
- 人工抽样 20 条高匹配事件：
  - 至少 80% 能从轨迹复现中看到两船长时间近距离、低速或静止、近似平行。
  - 服务船/港服误报为 0 或接近 0。
  - 普通泊位靠泊误报需要结合 geofence 单独统计。

## 8. 关键限制

- AIS 只能提供位置、速度、航向、船型、导航状态等信息，不能直接证明货物转运、人员登临或非法交易。
- 没有港区、泊位、锚地、航道 geofence 时，无法仅靠 AIS 稳定地区分普通靠泊和异常商船搭靠。
- 船型依赖 AIS `shipType` 和船名关键词时，未知/错误船型会影响识别质量；高匹配模式应宁可漏报未知船，也不要放大误报。
- 新规则应先用新 `rule-version` 和新 consumer group 小范围验证，不应直接覆盖当前生产 group。
