# 服务器结果回传清单

- Scene Preflight 的完整 XXL-JOB 返回 JSON 与执行时间。
- Region Preflight 的完整返回 JSON，尤其是 status、regionFingerprint、querySlices、candidateRisk、databaseWrites。
- 三次区域 dry-run 的完整 summary JSON 和各自输出目录。
- 每个输出目录的 `manifest.json`、`discovery-summary.json`、`checksums.sha256`。
- LOITERING 输出中的 dominantMaritimeScene、sceneConfidence、shadowDecision、snapshot、coast distance、unknown/conflict ratio 样例。
- ALONGSIDE 输出中的 ownScene、targetScene、synchronizedScene、sceneConfidence、shadowDecision 样例；双方矛盾时必须为 SCENE_CONFLICT。
- `scripts/check-handler-registration.sh` 输出。
- 启动日志末 200 行和对应 XXL-JOB handler 日志；敏感配置、口令、token 必须脱敏。
- `sha256sum` 校验结果、服务器 Release fingerprint、Scene/Coast tile 数。
- 明确确认未执行 SQL、未运行 Shadow Audit、未写正式事件。
