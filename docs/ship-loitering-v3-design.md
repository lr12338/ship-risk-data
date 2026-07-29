# Loitering V3 规则与服务设计

## 1. 文档状态

- 设计版本：`LOITERING_V3_SHADOW`
- 审计日期：2026-07-27
- 当前状态：设计完成；Analyzer、纯业务内核和未抽稀 HBase 轨迹适配器已实现，场景/船型适配、定时入口与落库尚未实现
- 适用范围：船舶徘徊事件，不修改船舶搭靠事件规则
- 历史配套验证报告：
  [2026-07-27 V3 验证报告](archive/production/ship-loitering-v3-validation-report.md)
- 当前生产状态：
  [搭靠与徘徊 5 万船生产测试状态跟踪](ship-behavior-production-test-status.md)

本文给出 Loitering V3 的业务定义、规则、类结构、数据库字段和运行服务方案。
所有数值阈值都是 shadow 阶段的首版标定值，必须经过真实样本评估后才能升级为保险生产口径。

## 2. 已确认事实与待确认前提

### 2.1 已确认事实

1. AIS 历史轨迹来自 HBase `histyinfo`。
2. 当前行为事件摘要保存到 `dataanalysis.ship_behavior_event`。
3. 当前事件轨迹快照保存到 `dataanalysis.ship_behavior_event_detail`。
4. 项目使用 Spring Boot、Java 8、MySQL、HBase 和 XXL-JOB。
5. 项目已有港区 polygon 加载和 JTS 空间匹配能力。
6. 当前仓库没有经过确认的泊位 polygon 或靠泊点数据接口。
7. 当前 V2 没有稳定事件键，重复扫描不能保证主表和明细表幂等。
8. 当前轨迹加载会做几何抽稀，不能直接证明圆形、蛇形等细节被完整保留。

### 2.2 实现前必须确认

1. 泊位 polygon、泊位中心线或靠泊点的权威数据源、坐标系和更新周期。
2. `recentships.type/minotype` 到目标商船类型的正式映射。
3. 官方锚地等待应当排除、降权还是保留为风险事件。
4. 港外邻近距离是否统一为 10 海里，主要航道邻近距离是否统一为 2 海里。
5. 人工评估的精确率、召回率和正常靠泊误报率目标。

在以上信息未确认时，可以完成 shadow 计算，但不能宣称 V3 已达到生产质量。

## 3. 业务目标

V3 不识别所有低速船舶。正式事件必须表达：

> 目标商船靠近港口或主要航道，未进入正常泊位完成靠泊，却在较长时间内以低速做圆形、环绕、蛇形、往返或小范围漂移活动。

### 3.1 正常靠泊

- 靠近港口并进入港界。
- 最终进入泊位区域或靠泊点邻近范围。
- 低速或停止状态持续。
- 进港航迹总体方向稳定。

### 3.2 异常徘徊

- 靠近港口或主要航道。
- 未进入泊位完成正常停留。
- 主要航速在 1～3 节。
- 持续时间较长。
- 轨迹呈闭环、环绕、蛇形、反复折返或受限范围漂移。
- 首尾位置接近，或实际航程远大于首尾直线距离。

### 3.3 第一阶段船型

只允许：

- 集装箱船
- 油轮
- 散货船
- LNG/LPG
- 普通货船

明确过滤：

- 渔船
- 工程船、服务船
- 拖轮、拖带船
- 游艇、小艇、快艇
- 客船
- 未知船型

未知船型第一阶段采用 fail-closed：跳过并记录统计，不按商船计算。

## 4. V3 端到端计算链路

```text
XXL-JOB 每小时触发
        ↓
LoiteringAnalyzerService 创建运行记录
        ↓
MerchantShipProvider 获取目标商船
        ↓
LoiteringTrajectorySource 读取过去 24 小时 AIS
        ↓
轨迹清洗、去重、保形重采样
        ↓
PortContextProvider 标注港界、泊位、靠泊点、航道关系
        ↓
低速与场景门槛切分候选 episode
        ↓
正常靠泊和直线低速通行排除
        ↓
LoiteringFeatureExtractor 计算空间和运动特征
        ↓
LoiteringRuleEngine 分类轨迹模式
        ↓
置信度与风险评分
        ↓
LoiteringEventReconciler 合并 OPEN/历史事件
        ↓
LoiteringEventStore 事务 upsert 摘要、特征和明细
        ↓
LoiteringQualityService 写每日统计并生成 TOP 案例
```

## 5. 输入数据契约

### 5.1 船舶元数据

最少需要：

```text
mmsi
imo
ship_name
ship_type
minor_ship_type
length
dwt
metadata_updated_at
```

V3 不应修改现有 `selectRecentShips()` 的返回语义，以免影响搭靠和区域任务。
应新增 Loitering 专用查询或 Provider。

### 5.2 AIS 点

最少需要：

```text
mmsi
point_time
lon
lat
sog
cog
nav_status
report_source
```

要求：

- 不使用会删除 1～3 节关键点的轨迹读取方法。
- 不能依靠高度几何抽稀后的轨迹直接判断闭环和蛇形。
- 原始点保留后，再在 V3 内按时间做确定性重采样。
- 无效 COG 不应转换成真实的 0 度航向参与方向变化。

当前 HBase 适配器固定以 `simplifyZoom=0` 调用分片读取，因此不执行 DP/角度抽稀。
底层原始读取仍会过滤 `reportsrc=19` 且航速小于 1 节的点，但不会删除本规则
关注的 1～3 节点；该来源差异必须进入数据质量说明。

### 5.3 港口场景

`PortContextProvider` 输出：

```text
scene_data_available
inside_port
nearest_port_id
port_distance_nm
near_port
inside_berth
nearest_berth_id
berth_distance_nm
near_berth_point
near_channel
channel_distance_nm
berth_entry_followed_by_stay
minutes_to_berth_entry
final_approach_direction_efficiency
final_approach_direction_change_per_hour
```

定义：

- `inside_port`：点被港区 polygon 覆盖。
- `near_port`：`inside_port=true`，或到港界距离不超过 10 海里。
- `inside_berth`：点被泊位 polygon 覆盖。
- `near_berth_point`：到靠泊点距离不超过 0.15 海里。
- `near_channel`：到主要航道 geometry 距离不超过 2 海里。

现有 `ZoneSpatialMatcher.match()` 只能判断点是否被 polygon 覆盖，不能直接表达港界外距离；
V3 可以复用 `ZoneLoaderService` 和 JTS geometry，但需要独立的距离计算与泊位索引。

## 6. 轨迹处理

### 6.1 清洗

1. 按时间升序。
2. 丢弃空时间、越界经纬度、负航速和明显异常高速点。
3. 按 `mmsi+time+lon+lat` 去重。
4. 对相同时间的冲突点保留质量优先级最高的数据源；没有质量信息时记录冲突并跳过该时刻。
5. 计算相邻坐标运动方位角，作为 `derived_course`。
6. 原始 COG 只在有效且与位移方向相容时参与特征。

### 6.2 重采样

- 目标间隔：5 分钟。
- 不跨 30 分钟 AIS gap 插值。
- 每个间隔保留最接近采样时刻的真实点。
- 不用插值点扩大覆盖面积或制造闭环。

### 6.3 AIS gap

- `gap <= 15 min`：正常连续。
- `15 < gap <= 30 min`：连续但降低覆盖质量。
- `gap > 30 min`：切断 episode。

AIS gap 只影响数据置信度，不直接增加徘徊风险分。

## 7. 候选 episode 切分

### 7.1 点级门槛

点满足以下场景条件之一：

```text
near_port = true
OR near_channel = true
```

低速条件：

```text
1.0 <= sog <= 3.0 kn
```

允许因浪涌、数据噪声产生的短暂越界：

```text
0.5 <= sog <= 4.0 kn
且持续不超过 15 分钟
```

### 7.2 候选开始

- 连续至少 4 个重采样点（3 个 5 分钟间隔）满足场景和低速门槛。
- 覆盖时间至少 15 分钟。
- `candidate_start_time` 取第一个满足门槛的真实点。

### 7.3 候选保持

- 低速门槛短暂失效不超过 15 分钟时保持同一 episode。
- 港口或航道邻近条件短暂失效不超过 15 分钟时保持。
- 不跨越大于 30 分钟的 AIS gap。

### 7.4 候选结束

候选门槛短暂失效不超过 15 分钟时保持同一 episode。超过 15 分钟时：

- 旧 episode 的 `candidate_end_time` 固定为最后一个满足候选门槛的真实点。
- 后续重新满足条件的点必须重新通过 4 点/15 分钟开始门槛。
- `normal_berthing=true` 已代表泊位内低速停留满 30 分钟，可立即结束候选。
- AIS gap 大于 30 分钟直接切断 episode。

episode 已切断不代表立即发布 FINAL；事件结束时间早于任务水位 30 分钟后才确认，
见 14.2 节。活动范围持续扩大、直线低速通行在完整 episode 特征计算后排除。

### 7.5 时长级别

- `<60 min`：不保存事件，只计拒绝统计。
- `60～119 min`：保存 `REVIEW` 候选，可进入案例库，不进入保险风险汇总。
- `>=120 min`：允许进入正式模式识别。

## 8. 空间与运动特征

所有距离先投影到事件中心附近的局部等距平面，再换算为海里。

### 8.1 中心和半径

```text
center = 经度中位数、纬度中位数
point_radius_i = distance(point_i, center)
radius_nm = P95(point_radius)
max_radius_nm = max(point_radius)
radial_cv = stddev(point_radius) / mean(point_radius)
```

主规则使用 P95 半径，最大半径只用于异常点诊断。

### 8.2 覆盖面积

```text
trajectory_area_nm2 = area(convex_hull(projected_points))
```

点数少于 3 时面积为 0，但不能单独据此判定闭环。

### 8.3 路径、首尾和闭合

```text
path_length_nm = Σ distance(point[i-1], point[i])
start_end_distance_nm = distance(first, last)
direction_efficiency =
    start_end_distance_nm / max(path_length_nm, epsilon)
closure_score =
    1 - min(1, start_end_distance_nm / max(path_length_nm, epsilon))
```

- `closure_score` 越接近 1，首尾相对实际航程越接近闭合。
- `direction_efficiency` 越接近 1，越像直线通行。

### 8.4 方向变化

```text
direction_change_degree = Σ abs(circular_delta(derived_course[i-1], derived_course[i]))
direction_change_per_hour = direction_change_degree / duration_hours
signed_turn_degree = Σ signed_circular_delta(...)
turn_sign_change_count = 转向符号切换次数
reversal_count = 接近 180 度的方向反转次数
same_turn_direction_ratio =
    max(clockwise_turn_count, counterclockwise_turn_count) / valid_turn_count
```

方向反转的 shadow 阈值：

```text
abs(turn_delta) >= 135°
```

## 9. 正常场景排除

### 9.1 正常泊位停留

满足：

```text
inside_berth = true
OR berth_distance_nm <= 0.15
```

并且：

```text
sog <= 0.5 kn 持续 >= 30 min
```

输出：

```text
event_status = EXCLUDED
exclusion_flag = EXCLUDED_NORMAL
exclusion_reason = NORMAL_BERTHING
risk_score = 0
```

### 9.2 正常靠泊进近

候选结束后 2 小时内进入泊位并停留，同时最后进港段：

```text
direction_efficiency >= 0.70
direction_change_per_hour < 45°
```

输出：

```text
event_status = EXCLUDED
exclusion_reason = NORMAL_BERTHING_APPROACH
```

### 9.3 直线低速通行

满足全部：

```text
direction_efficiency >= 0.75
direction_change_per_hour < 45°
start_end_distance_nm > 3.0
```

输出：

```text
event_status = EXCLUDED
exclusion_reason = STRAIGHT_LOW_SPEED_TRANSIT
```

### 9.4 场景数据不可用

如果港口或泊位数据未加载：

- 不把所有候选标记为高风险。
- 正式事件状态降为 `REVIEW`。
- `exclusion_reason=SCENE_DATA_UNAVAILABLE`。
- 运行统计记录 `scene_data_failure_count`。

## 10. 轨迹模式

正式事件需要命中至少一种模式。

### 10.1 CIRCULAR

```text
closure_score >= 0.65
start_end_distance_nm <= 2.0
abs(signed_turn_degree) >= 270°
radial_cv <= 0.60
radius_nm <= 5.0
```

### 10.2 ORBITAL

```text
abs(signed_turn_degree) >= 270°
same_turn_direction_ratio >= 0.70
direction_efficiency <= 0.50
reversal_count < 3
radius_nm <= 5.0
```

### 10.3 SERPENTINE

```text
direction_change_per_hour >= 90°
turn_sign_change_count >= 4
tortuosity >= 1.8
radius_nm <= 5.0
```

其中：

```text
tortuosity = path_length_nm / max(start_end_distance_nm, radius_nm, 0.1)
```

### 10.4 BACK_AND_FORTH

```text
reversal_count >= 3
direction_efficiency <= 0.50
radius_nm <= 3.0
```

### 10.5 WAITING_DRIFT

```text
duration_minutes >= 240
1.0 <= avg_speed <= 3.0
radius_nm <= 1.0
inside_berth = false
near_berth_point = false
```

### 10.6 模式优先级

如果同时命中多种：

```text
CIRCULAR
> ORBITAL
> SERPENTINE
> BACK_AND_FORTH
> WAITING_DRIFT
```

主字段保存优先级最高的 `trajectory_pattern`，
所有命中模式保存到 `matched_patterns_json`。

## 11. 正式事件硬门槛

必须满足全部：

```text
merchant_ship = true
scene_data_available = true
near_port = true OR near_channel = true
duration_minutes >= 120
low_speed_point_ratio >= 0.60
p90_speed <= 4.0
radius_nm <= 5.0
trajectory_area_nm2 <= 25.0
confidence_score >= 0.65
matched_pattern_count >= 1
normal_berthing = false
straight_low_speed_transit = false
```

不满足正式门槛但持续至少 60 分钟的，可以作为 `REVIEW` 样本。

## 12. 置信度

```text
coverage_score =
    min(1, actual_sample_count / expected_sample_count)

gap_score =
    max(0, 1 - max_gap_minutes / 30)

course_score =
    valid_derived_course_count / max(1, segment_count)

scene_score =
    1，港区和泊位数据都可用
    0.5，只有港区数据
    0，场景数据不可用

confidence_score =
    0.40 * coverage_score
  + 0.20 * gap_score
  + 0.20 * course_score
  + 0.20 * scene_score
```

等级：

- `HIGH`：`confidence_score >= 0.80`
- `MEDIUM`：`0.65 <= confidence_score < 0.80`
- `LOW`：`confidence_score < 0.65`

LOW 不发布为正式保险事件。

## 13. 风险评分

### 13.1 模式分，最高 40

- CIRCULAR：40
- ORBITAL：40
- SERPENTINE：35
- BACK_AND_FORTH：30
- WAITING_DRIFT：20

### 13.2 时长分，最高 20

- 120～239 分钟：8
- 240～479 分钟：14
- 480 分钟及以上：20

### 13.3 受限范围分，最高 15

- `radius <=1 nm`：15
- `1<radius<=3 nm`：10
- `3<radius<=5 nm`：5

### 13.4 场景分，最高 15

- 港界外 10 海里内或航道附近：15
- 港界内但不在泊位：10

### 13.5 未靠泊证据，最高 10

- 从未进入泊位：6
- 全程距靠泊点大于 0.15 海里：4

### 13.6 最终分

```text
raw_risk_score =
    pattern_score
  + duration_score
  + confinement_score
  + port_context_score
  + no_berth_score

risk_score =
    round(raw_risk_score * confidence_score, 2)
```

兼容字段：

- `risk_score` 写入现有 `event_score`。
- `confidence` 写入现有 `confidence_level`。
- `event_type` 使用现有 `behavior_type=LOITERING`。

等级继续使用：

- 0：L0
- 1～25：L1
- 26～50：L2
- 51～75：L3
- 76～100：L4

## 14. 事件生命周期和重复控制

### 14.1 状态

```text
REVIEW
OPEN
FINAL
EXCLUDED
```

### 14.2 Watermark

- 每小时运行时间为 `T`。
- AIS 读取窗口为 `[T-24h, T]`。
- 只把结束时间早于 `T-30min` 的事件改成 FINAL。
- 最近 30 分钟事件保持 OPEN，等待延迟 AIS。

### 14.3 稳定事件键

首次创建事件时按 `mmsi+rule_version+首次候选开始时间` 生成确定性 SHA-256
事件键（截取 32 位），后续扫描通过事件匹配复用，不从会变化的结束时间重新生成。

新候选与已有 OPEN/FINAL 事件满足以下条件时视为同一事件：

```text
mmsi 相同
rule_version 相同
时间重叠率 >= 0.50，或间隔 <=30 分钟
中心距离 <=1 海里
```

时间重叠率定义为交集时长除以两个事件较短的时长；`间隔 <=30 分钟` 只用于
没有正时长交集的前后相邻事件，不能让低重叠的长事件绕过 0.50 门槛。
存在多个匹配项时依次选择重叠率最高、时间间隔最小、中心距离最近的事件。

匹配后：

- 保留原 `event_key`。
- 扩展开始或结束时间。
- 合并并去重轨迹点。
- 重新计算全部特征、模式、置信度和评分。

### 14.4 数据库幂等

- `UNIQUE(behavior_type, event_key)`。
- 摘要使用 upsert。
- 特征表按 `event_key` upsert。
- 明细按 `event_key+point_role+point_time+lon+lat` 唯一。
- 摘要、特征、明细和运行状态在同一事务中提交。

## 15. 输出数据

### 15.1 ship_behavior_event

现有字段直接复用：

```text
behavior_type        对应 event_type
mmsi
start_time
end_time
duration_minutes     对应 duration
avg_speed
radius_nm            对应 radius
confidence_level     对应 confidence
event_score          对应 risk_score
rule_version
batch_no
severity_level
exclusion_flag
exclusion_reason
center_lon
center_lat
score_detail_json
```

建议新增：

```text
event_key VARCHAR(64)
imo VARCHAR(20)
ship_name VARCHAR(128)
ship_type VARCHAR(64)
event_status VARCHAR(16)
trajectory_pattern VARCHAR(32)
near_port TINYINT(1)
inside_port TINYINT(1)
nearest_port_id BIGINT
first_detected_at DATETIME
last_detected_at DATETIME
updated_at DATETIME
```

索引：

```sql
UNIQUE KEY uk_behavior_event_key (behavior_type, event_key);
KEY idx_behavior_status_time (behavior_type, event_status, start_time);
KEY idx_behavior_port_time (behavior_type, nearest_port_id, start_time);
```

`near_port` 已出现在当前未提交的共享表改动中，V3 应复用同一列名；
但必须另外增加 `inside_port`，不能把“港界内”和“港口邻近”混成一个布尔语义。

### 15.2 Loitering 专属特征表

避免将全部 V3 特征放进搭靠共享模型：

```sql
CREATE TABLE dataanalysis.ship_behavior_loitering_feature (
  event_key VARCHAR(64) NOT NULL,
  radius_p95_nm DOUBLE,
  max_radius_nm DOUBLE,
  trajectory_area_nm2 DOUBLE,
  path_length_nm DOUBLE,
  start_end_distance_nm DOUBLE,
  closure_score DOUBLE,
  direction_efficiency DOUBLE,
  direction_change_degree DOUBLE,
  direction_change_per_hour DOUBLE,
  signed_turn_degree DOUBLE,
  turn_sign_change_count INT,
  reversal_count INT,
  radial_cv DOUBLE,
  low_speed_point_ratio DOUBLE,
  p90_speed DOUBLE,
  inside_berth TINYINT(1),
  nearest_berth_id BIGINT,
  berth_distance_nm DOUBLE,
  near_channel TINYINT(1),
  channel_distance_nm DOUBLE,
  confidence_score DOUBLE,
  matched_patterns_json TEXT,
  reason_codes_json TEXT,
  feature_version VARCHAR(32),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (event_key)
);
```

### 15.3 明细关联

向 `ship_behavior_event_detail` 增加：

```text
event_key VARCHAR(64)
is_resampled TINYINT(1)
derived_course DOUBLE
```

事件解释应保存真实点；重采样点可以保存，但必须由 `is_resampled` 明确标识。

### 15.4 运行记录

```sql
CREATE TABLE dataanalysis.ship_behavior_loitering_run (
  id BIGINT NOT NULL AUTO_INCREMENT,
  run_key VARCHAR(64) NOT NULL,
  rule_version VARCHAR(32) NOT NULL,
  window_start DATETIME NOT NULL,
  window_end DATETIME NOT NULL,
  status VARCHAR(16) NOT NULL,
  analyzed_ship_count INT DEFAULT 0,
  eligible_ship_count INT DEFAULT 0,
  candidate_count INT DEFAULT 0,
  final_event_count INT DEFAULT 0,
  excluded_event_count INT DEFAULT 0,
  review_event_count INT DEFAULT 0,
  failed_ship_count INT DEFAULT 0,
  scene_data_failure_count INT DEFAULT 0,
  error_message VARCHAR(1024),
  started_at DATETIME,
  finished_at DATETIME,
  PRIMARY KEY (id),
  UNIQUE KEY uk_run_key (run_key)
);
```

## 16. 类结构

```text
ShipBehaviorJob
  └─ shipLoiteringV3JobHandler
       └─ LoiteringAnalyzerService
            ├─ MerchantShipProvider
            ├─ LoiteringTrajectorySource
            ├─ PortContextProvider
            ├─ LoiteringFeatureExtractor
            ├─ LoiteringRuleEngine
            ├─ LoiteringEventReconciler
            ├─ LoiteringEventStore
            └─ LoiteringQualityService
```

### 16.1 LoiteringAnalyzerService

- 解析任务窗口。
- 创建运行记录。
- 控制船舶遍历和并发。
- 调用各纯业务组件。
- 汇总运行结果。
- 不包含具体几何公式和评分公式。

当前已实现不绑定 Spring、HBase 或 MySQL 的编排核心：固定读取 `[T-24h,T]`，
按船隔离失败，并输出 MMSI、IMO、事件时间、时长、平均航速、半径、模式、
港口关系、置信度和风险分。真实 Provider、运行记录和 Store 仍待接入。

### 16.2 MerchantShipProvider

- 获取 MMSI、IMO、船名和船型。
- 使用正面白名单。
- 输出跳过原因。
- 不修改现有搭靠船舶查询。

### 16.3 LoiteringTrajectorySource

- 专门保证低速点和运动形态点不被预先删除。
- 对 HBase 返回模型做隔离。
- 不让 detector 直接依赖 HBase DTO。
- 当前适配器强制 `simplifyZoom=0`，支持小时分片和单窗最大点数保护。

### 16.4 PortContextProvider

- 管理港界、泊位、靠泊点和航道空间索引。
- 明确区分 inside、near 和 unavailable。
- 可以复用 `ZoneLoaderService`，但不依赖 `AlongsideSceneService` 的事件中心点语义。

### 16.5 LoiteringFeatureExtractor

- 纯函数。
- 输入已切分、重采样的单船 episode，输出不可变 feature。
- 不读数据库，不落库，不打业务等级。

### 16.6 LoiteringRuleEngine

- 正常靠泊排除。
- 直线通行排除。
- 轨迹模式识别。
- 置信度和风险评分。
- 输出 reason codes。

### 16.7 LoiteringEventReconciler

- 匹配已有 OPEN/FINAL 事件。
- 维护稳定 event_key。
- 处理小时扫描、延迟 AIS 和跨日边界。

### 16.8 LoiteringEventStore

- 事务 upsert。
- Loitering 专用，不改变现有搭靠 repository 行为。
- 负责摘要、特征、明细和运行状态一致性。

### 16.9 LoiteringQualityService

- 生成每日统计。
- 选择 TOP、排除和边界案例。
- 输出案例数据，不参与事件判定。

### 16.10 接口使用原则

只在外部边界使用接口：

- 船舶目录
- 轨迹源
- 场景数据
- 事件存储

特征提取和规则引擎使用普通类，不为单次实现建立不必要的抽象层。

## 17. 服务运行方案

### 17.1 XXL-JOB

新增 handler：

```text
shipLoiteringV3JobHandler
```

shadow 阶段保留：

```text
shipLoiteringJobHandler
shipLoiteringBatchJobHandler
shipAlongsideJobHandler
shipAlongsideBatchJobHandler
```

### 17.2 调度

```text
频率：每小时
扫描窗口：过去 24 小时
watermark：30 分钟
规则版本：LOITERING_V3_SHADOW
并发：首期 1～2，根据 HBase 和内存压测调整
```

XXL-JOB 使用单机串行阻塞策略，数据库 `run_key` 唯一键作为重复触发保护。

### 17.3 失败策略

- 单船失败不终止全批。
- 场景数据整体不可用时不发布正式事件。
- 摘要和明细事务失败时不标记船舶完成。
- 失败任务记录 MMSI、窗口、阶段和异常摘要。
- 重试复用同一个 `run_key` 或创建显式 retry run，不静默生成重复事件。

### 17.4 日志

每次运行至少输出：

```text
run_key
window
analyzed_ship_count
eligible_ship_count
candidate_count
final_event_count
excluded_event_count
review_event_count
failed_ship_count
scene_data_available
elapsed_ms
```

事件日志至少输出：

```text
event_key
mmsi
imo
pattern
start/end
duration
avg_speed
radius
area
closure
near_port/inside_port/inside_berth
confidence
risk_score
reason_codes
```

## 18. Shadow 上线步骤

1. 建立真实样本 fixture 和验收测试。
2. 实现纯特征提取，不接数据库。
3. 实现商船和港口/泊位场景 Provider。
4. 实现规则引擎和状态机。
5. 实现独立 V3 Store 和幂等事件键。
6. 新增 V3 XXL handler。
7. 只运行 shadow，V2 保持生产行为不变。
8. 至少积累 2～4 周事件和人工标签。
9. 达到质量门槛后，业务确认切换正式规则版本。
10. 切换后继续保留 V2 回放能力一个观察周期。

当前已完成第 2 步、第 4 步中的候选状态机和规则判定，以及第 5 步之前的
纯事件对账逻辑；Analyzer 编排、每日统计与 TOP 风险选择的纯逻辑也已完成。
轨迹 Provider 适配器已实现；场景/商船 Provider 适配器、Store、案例 HTML
生成和定时 handler 尚未实现。

## 19. 搭靠隔离约束

- 不修改 `ShipAlongsideDetector` 的规则。
- 不修改搭靠 handler 参数和返回值。
- 不修改搭靠候选来源。
- 不改变现有搭靠字段含义。
- 新增共享主表字段必须允许 NULL，旧搭靠 INSERT 必须继续可用。
- Loitering 专属特征进入独立表。
- V3 测试套件必须执行全部现有搭靠单元测试。
- 港口底层 geometry 可以共用，事件业务判断不能共用同一个上下文对象。

## 20. 非目标

本阶段不做：

- 机器学习分类器。
- 全局实时 AIS 流处理平台。
- 自动拒保或理赔结论。
- 制裁区、海盗区、天气等额外风险联动。
- 搭靠 V3 重构。
- 未经样本验证的生产阈值承诺。
