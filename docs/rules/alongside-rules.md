# 搭靠规则

内置 `alongside-insurance-v1:1` 未获生产批准。规则阶段为轨迹质量、候选生成、行为确认、Scene、风险解释、持久化资格。

当前适配证据包含主船/目标船速度、持续时间、同步误差、相对速度、航向差、距离稳定性和 Scene 排除。阈值的机器可读唯一来源是 `src/main/resources/behavior-rule-sets/alongside-insurance-v1.json`。

主船船型 fail-closed；目标船按独立策略解释。目标船类型只能调整置信度、正常作业可能性和复核标签，不能直接推断违法、制裁规避或走私。
