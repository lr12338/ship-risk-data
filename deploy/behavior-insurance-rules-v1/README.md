# 行为保险规则 V1

本目录是提交 `b006fb8` 对应的测试服发布与人工验证入口；发布物由同一工作树构建。它不包含服务器密码、完整 `application.properties` 或自动触发生产任务的脚本。

## 开发记录与版本

| 版本 | 内容 |
| --- | --- |
| WORLD_SEA_102_V1R1 | 统一全球命名海域 Gate；仅表示 `WORLD_SEA_NAMED_AREA`，不表示法律公海、EEZ 或领海。 |
| LOITERING_V2_PORT_APPROACH_V1 | 引入港口/锚地场景数据；此版本曾把港口距离作为 Loitering 硬 Gate。 |
| 行为保险规则 V1 | 将行为识别、场景分类、保险解释分层；Loitering 规则升级为 `LOITERING_V2_INSURANCE_SCENE_V1`，Alongside 升级为 `ALONGSIDE_V3_INSURANCE_SCENE_V2`。 |
| 港口锚地几何修复 V1 | 港口参考边界排除 `zones_type=1`/锚地名称；固定修复26条自相交锚地并保留可回滚备份。 |

## 三层计算链路

`AIS 清洗排序 → WORLD_SEA Gate → Physical Behavior Detection → Scene Classification → Insurance Risk Interpretation → Persistence`

- **行为识别**只判断是否发生 Loitering/Alongside，不由港口距离或风险分数决定。
- **场景分类**统一为 `PORT_OPERATIONAL`、`ANCHORAGE`、`PORT_APPROACH`、`OFFSHORE`。`PORT_APPROACH` 使用到港口 Polygon 边界的距离，不再以 Polygon centroid 作为正式距离语义。
- **保险解释**输出风险指标、可能情境或 review；不得输出走私、制裁规避或非法转运已确认等结论。

## Loitering 规则

- WORLD_SEA 内、SOG 有效且 `>=1kn`，港区/锚地之外，才进入行为检测；低于 1kn、无效速度、港区、锚地都是 hard split，正式事件 `belowMinSpeedEvidenceCount=0`。
- `PORT_APPROACH` 与 `OFFSHORE` 是检测后的场景结果，**港口附近不是 Loitering 硬 Gate**。远海仍必须满足慢速紧凑长期停留或强机动证据，不能仅凭远海慢航成立。
- `SLOW_HOLDING`、`MANEUVER_LOITERING`、`COMPOSITE_LOITERING` 与 `WEAK_CANDIDATE` 是行为模式；weak 可统计/dry-run，但 `weak.store-enabled=false`，不写正式保险事件。
- 场景数据无法加载时 fail closed，日志/异常必须显示 `LOITERING_PORT_SCENE_UNAVAILABLE` 或 `SCENE_DATA_UNAVAILABLE`，不得把全部事件静默分类成 Offshore。

## Alongside 规则

- 主船必须是合格商船；目标船保持 wide recall，允许商船、加油/补给、驳船、拖轮、小艇及 UNKNOWN。
- 每个 detector-confirmed pair 的主船点、目标点和 pair midpoint 都必须满足 WORLD_SEA，coverage=1.0；维持低速、近距离、相对速度稳定、航向平行、持续时间和 pair count 的正式阈值。
- `PORT_OPERATIONAL` 与 `ANCHORAGE` 为正常作业/等待的正式排除；`PORT_APPROACH` 与 `OFFSHORE` 都可形成正式风险事件。`<1kn` 静止并靠保留为 `STATIONARY_SIDE_BY_SIDE_REVIEW`，默认不写正式风险事件。
- 解释示例：港口接近区加油船为 `OFF_PORT_BUNKERING_REVIEW`，远海商船为 `POSSIBLE_STS_LIKE_CONTACT`，远海未知目标为 `UNKNOWN_OFFSHORE_COUNTERPARTY_CONTACT`。

## 使用顺序

1. 按 [release-manifest.md](release-manifest.md) 核对本地构建身份，再按 [manual-deployment.md](manual-deployment.md) 构建、校验并手工部署 JAR。
2. 仅逐项合并 [application.properties.delta](application.properties.delta)。
3. 按 [geometry-repair.md](geometry-repair.md) 完成只读预演、受控写回与场景验收；需要恢复原始 WKB 时使用 [geometry-repair-rollback.sql](geometry-repair-rollback.sql)。
4. 按 [xxl-job-guide.md](xxl-job-guide.md) 依次执行 20、100、500 dry-run；参数见 [xxl-job-params.json](xxl-job-params.json)。
5. 使用 [query.sql](query.sql) 与 [validation-checklist.md](validation-checklist.md) 验收。5000 正式写库任务需前序全部通过和人工确认，两个行为任务不得并发。
