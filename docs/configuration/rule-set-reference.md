# RuleSet 参考

RuleSet 的稳定身份为 `ruleSetId + ruleSetVersion + ruleSetHash`。同一 batch 首次绑定后不得混用其他身份。阈值变化必须创建新 RuleSet、新 Profile 和新 batch。

每条 `RuleDecision` 包含 `ruleId`、`ruleVersion`、`passed`、`observedValue`、`threshold`、`unit`、`reasonCode` 和 `evidence`。当前机器可读配置位于 `src/main/resources/behavior-rule-sets/`，两套内置规则的 `productionApproved=false`。
