# 行为数据模型

`ship_behavior_run` 保存代码、JAR、Profile、RuleSet、Scene、名单、参数和写入范围；主键 `run_id`，批次/Profile/hash 唯一。`ship_behavior_shadow_result` 是实验结果，唯一键 `(batch_no, comparison_key)`，支持幂等和按 batch 清理。`ship_behavior_rule_decision_evidence` 保存逐规则证据。现有 `ship_behavior_maritime_scene_audit` 继续复用为 Scene 审计。

这些新增表均非正式业务事件表。正式 `ship_behavior_event` 和 `ship_behavior_event_detail` 不增加实验字段。
