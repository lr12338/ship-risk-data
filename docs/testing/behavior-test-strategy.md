# 测试策略

基线（2026-08-05）：303 tests，0 failure，0 error，0 skipped，Maven 5.260 s。实现后首次全量回归：330 tests，全部通过，6.715 s；后续最终构建结果以发布 manifest 为准。

新增测试覆盖 Profile 加载/继承/hash、未知 Profile、短参数与截断 JSON、权限降级、dryRun/NONE/SHADOW/FORMAL 门禁、Scene 三态、UNKNOWN/CONFLICT、强类型 Preflight、船型策略、RuleSet/batch guard、文件幂等、旧 Detector characterization、Preflight 不读轨迹和自动 DDL 默认关闭。

本地 fixture/mock 不等同真实 AIS/HBase。未经授权未执行服务器 10/100/500/5000 任务，因此生产 A/B 仍是后续人工阶段。
