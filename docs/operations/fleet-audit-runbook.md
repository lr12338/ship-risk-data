# Fleet Audit Runbook

1. 用 `shipBehaviorProfilePreflightJobHandler` 验证 Profile、RuleSet、名单、时间、Scene 和写入门禁。
2. 10 艘：`limit=10,pageSize=10,parallelism=1`，只用 NONE/FILE_AUDIT。
3. 100 艘：同名单、时间和规则比较 OFF/SHADOW/FILTER。
4. 500 艘：验证耗时、内存、失败率、幂等和输出 checksum。
5. 5,000 艘：仅经审批执行 Shadow Audit，不自动启用数据库或正式写入。

每阶段使用新 batch。任何阈值、Profile、RuleSet 或 Scene fingerprint 改变都必须停止续跑并创建新 batch。`startSeq`、`shipLimit`、`pageSize`、`parallelism`、`resume` 位于短信封；默认 parallelism=1、storeTrajectoryDetails=false。
