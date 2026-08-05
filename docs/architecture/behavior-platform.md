# Behavior Platform V1 架构

V1 采用 Strangler：`BehaviorPlatformJobs → BehaviorPlatformFleetAuditService → BehaviorRunOrchestrator → Existing Detector Adapter`。旧 Detector 的数学规则不重写；`AlongsideDetectorAdapter` 和 `LoiteringDetectorAdapter` 的 characterization tests 锁定关键输出。

任务信封只携带 Profile、名单、时间、分页、续跑、dry-run 和降级覆盖。`BehaviorPlatformRegistries` 在启动时加载 classpath Profile/RuleSet，并可读取 `ship.behavior.profile-dir` 外部只读目录。同 ID 重复直接失败；resolved snapshot 与 SHA-256 写入 Run Manifest。

Scene 在候选轨迹已加载后执行。OFF 不调用 Scene；SHADOW 只产生证据；FILTER 只改变隔离的实验 disposition。UNKNOWN、CONFLICT、覆盖不足均进入 REVIEW。comparison key 由行为、船舶、事件时间和 RuleSet hash 确定性生成。

V1 的结构化规则证据由兼容适配层将已接受事件映射为 `RuleDecision`。这保证阈值与版本可追溯，但不宣称已把旧 Detector 内部所有拒绝分支重写为独立规则类；后续抽离必须继续使用 characterization tests。
