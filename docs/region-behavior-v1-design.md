# 指定区域内搭靠与徘徊计算 V1 设计

## 目标与边界

原有任务是“目标船列表 → 单船历史轨迹 → 行为事件”，回答某些船发生过什么。V1 是“用户区域+时间 → 区域候选船 → 完整轨迹生产算法 → 事件区域归属”，回答某个港外、锚地外缘、海峡、河口或调查区域内发生过什么。

V1 仅限受控的历史 dry-run：不写正式事件、候选船、目标船列表或 batch status；不支持 ENFORCE、Kafka、超大区域、跨日期变更线 Polygon 或长期单次扫描。

## 候选发现

`coreRegion` 是用户 Polygon/MultiPolygon，只它决定事件归属。`analysisRegion` 是用局部等距米制坐标近似扩展 `regionBufferNm`后的区域，用于候选召回和边界上下文；禁止直接对 EPSG:4326 度数 buffer。

现有 `SpatialAisReplayClient` 只接受 bbox/圆，因此使用：

`analysisRegion envelope → 空间网格 → 时间切片 → bbox replay → analysisRegion 精确点过滤`。

slice ID 由 `regionFingerprint + cellId + sliceStart + sliceEnd` 确定性 SHA-256 生成。指纹来自 MakeValid 后、normalize 后的二进制几何。网格/时间边界重复点按 `mmsi + timestamp + 6位小数 lon/lat` 去重。

## 完整性与资源控制

截断先递归缩短时间片；到达最小时间片后再四分空间网格。到达配置的空间细分深度仍截断时标记 `INCOMPLETE_TRUNCATED`。现有 client 自身保持原有重试和 timeout 语义；重试后 timeout 标记 `INCOMPLETE_TIMEOUT`。解析错误比超过阈值标记 `INCOMPLETE_PARSE_ERROR`。

任一必要 slice 不完整，整体只能是 `PARTIAL`，可输出已见候选供诊断，但不得宣称完整。候选数达上限返回 `CANDIDATE_LIMIT_EXCEEDED`，不静默截断。

默认值参考现有 replay `dev-size=50000`、轨迹 `simplify-zoom=15`、每船最大 200000 点及 6 小时安全加载切片；V1 另限制 24 小时、2500 km²、500 船和 5000 个初始 slice。这些是保守的本地测试边界，不是生产容量承诺。

## 船型与生产算法复用

元数据通过只读 `aid41JdbcTemplate` 分批 IN 查询 `ais41.recentships`。资格使用现有 `ShipTypeClassifier.isMerchant` 和目标船现行 `length>70` 边界。缺失或无法判断的船型是 `UNKNOWN_SHIP_TYPE`，默认不分析。

徘徊先加载前后 context 的完整 HBase 轨迹，再调用 `analysisLoiteringV2Production`和现有 `validNonWeak`。搭靠调用 `analysisAlongsideProductionWindow`，该公开路径已经是 `enableInsert=false`，完整保留其区域 replay、timeout、truncated 和事件抑制逻辑。两者最后再把事件裁回用户时间窗。

不能先裁出 Polygon 内的轨迹再运行行为算法：裁剪会破坏时间间隔、位移、半径、航向和两船同步关系，既可制造假事件，也可漏掉跨边界事件。

## 区域归属和去重

徘徊 `STRICT_CORE` 要求事件中心在 core 内，且 own points 在内比例达阈值。搭靠还要求 own、target 和同步 pair midpoint 的在内比例全部达阈值。中心优先使用原事件字段，缺失时才用 own points 计算。只部分相交的输出为 `REGION_BOUNDARY_PARTIAL`，不计入有效区域事件。

搭靠 pair 规范化为 `min(mmsi), max(mmsi)`，同规则版本且时间重叠率达阈值的 A-B/B-A 事件合并，不重复输出。`sourceEvent` 是原事件的 JSON 副本，不修改原对象。

## 输出与后续集成

每批输出到 `var/region-behavior-runs/<batchNo>/`，包含 manifest、query plan、discovery summary、候选/有效/边界/失败 NDJSON gzip 及 SHA-256。目录已存在时拒绝覆盖。

V1 不依赖 `MaritimeSceneService`。`RegionEventContextEnricher` 当前是 `NO_OP`。全球 Maritime Scene Release 合并后，可在独立集成任务中添加 dominant scene、coast distance、inland/open-water confidence 和 scene snapshot fingerprint；用户区域归属与海洋/内河语义仍保持两层。
