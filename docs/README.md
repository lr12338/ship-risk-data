# Behavior Platform V1 文档入口

当前稳定的新入口只负责搭靠与徘徊实验计算：

- `shipBehaviorProfilePreflightJobHandler`：只读预检，不读全年轨迹，不写数据库。
- `shipBehaviorShadowV2FleetAuditJobHandler`：Profile 驱动的 `NONE` / `FILE_AUDIT` / 受门禁的 `SHADOW_DB`。
- `shipBehaviorFormalJobHandler`：专用正式入口；内置 RuleSet 均未获生产批准，因此默认拒绝。

旧 Handler 继续注册并保持原调用链。其他风险指标、任务入口和数据库不在 V1 变更范围。

## 当前文档

- [架构与兼容边界](architecture/behavior-platform.md)
- [搭靠规则](rules/alongside-rules.md)、[徘徊规则](rules/loitering-rules.md)、[Scene 决策](rules/scene-decision-rules.md)
- [Profile](configuration/behavior-profiles.md)、[RuleSet](configuration/rule-set-reference.md)、[船型](configuration/ship-type-policies.md)、[XXL 参数](configuration/xxl-job-parameters.md)
- [运行手册](operations/behavior-service-runbook.md)、[部署回滚](operations/deploy-and-rollback.md)、[日志健康](operations/logs-and-health-check.md)、[Fleet Audit](operations/fleet-audit-runbook.md)
- [数据模型](database/behavior-data-model.md)、[写入矩阵](database/write-mode-matrix.md)、[迁移](database/migrations.md)
- [测试策略](testing/behavior-test-strategy.md)、[Scene A/B](testing/scene-ab-evaluation.md)、[样本复核](testing/sample-review-guide.md)

历史长参数、旧生产测试和旧设计已移动到 `archive/legacy/behavior-v0/`，仅供追溯。若归档与当前代码冲突，以本页链接的 V1 文档和代码为准。
