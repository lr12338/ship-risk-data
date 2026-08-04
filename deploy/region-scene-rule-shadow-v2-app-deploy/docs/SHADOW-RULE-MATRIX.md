# Shadow V2 规则矩阵

所有结论只写 Region 输出，不改正式事件。

| 优先级 | Scene/数据 | 行为 | 组合结论 |
|---|---|---|---|
| 1 | PARTIAL/TIMEOUT | 任意 | DATA_PARTIAL_FAIL_CLOSED |
| 2 | REGION_BOUNDARY_PARTIAL | 任意 | REVIEW_REGION_BOUNDARY |
| 3 | 高置信内河时间比例 ≥0.90 且 ≥60 分钟 | 任意 | WOULD_REJECT_INLAND |
| 4 | unknown/conflict | 任意 | SCENE_REVIEW_REQUIRED |
| 5 | 港区/锚地/泊位/作业区数据不可用 | 任意 | OPERATIONAL_AREA_REVIEW |
| 6 | 河口/内水/领海/近岸 | 已确认形态 | COASTAL_OPERATION_REVIEW |
| 7 | 远岸 EEZ/公海 | SIDE_BY_SIDE_CONFIRMED 或 SHAPE_CONFIRMED | WOULD_ACCEPT_SHADOW |
| 8 | 远岸 EEZ/公海 | 跟随/追越/交叉/直线通过 | 对应 WOULD_REJECT_* |

静止搭靠默认关闭；静止等待只能进入 Review。Scene 标签不代表违法、制裁规避、银行风险或风险严重度。
