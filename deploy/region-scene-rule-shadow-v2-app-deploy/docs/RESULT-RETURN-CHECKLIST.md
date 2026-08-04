# 结果回传清单

- 应用包 SHA-256、服务器 JAR SHA-256、备份路径与部署时间。
- Preflight 完整 JSON：Scene release、fingerprint、Scene/Coast/总 tile 数、规则版本、安全字段、Handler 注册。
- 每个 Region 任务的 `manifest.json`、`checksums.sha256`、`region-events.ndjson.gz`、`boundary-events.ndjson.gz` 和 `failures.ndjson.gz`。
- 三个 2 小时区域的 Scene/combined 计数及 rejectReasonCounts。
- 6 小时搭靠 A/B 的 V3 候选、内河排除、近岸/作业区复核、舷侧确认、跟随/追越/交叉排除和 Shadow 接受数。
- 24 小时组合任务的搭靠/徘徊共现、Scene 切换、边界、重复船对、PARTIAL/TIMEOUT 汇总。
- 脱敏启动日志和任务日志；不得回传密码、token、数据库连接串或未脱敏船舶业务敏感字段。
