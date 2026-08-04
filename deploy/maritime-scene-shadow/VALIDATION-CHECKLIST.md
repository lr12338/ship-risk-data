# Shadow 验收清单

- [ ] `SELECT DATABASE()` 返回 `dataanalysis`
- [ ] 包内 `scripts/verify-package.sh` PASS
- [ ] 服务端 `scripts/verify-server-files.sh` PASS
- [ ] `shipMaritimeScenePreflightJobHandler` 返回 PASS、SHADOW_READY、fingerprintMatch=true、checksumFailures=0
- [ ] 只运行 Preflight，没有启动 Shadow Audit Handler
- [ ] Preflight 内部状态非 PASS 时 XXL-JOB 返回 FAIL
- [ ] 正式 event/detail、`open_sea`、severity、event_score 和 replay/timeout 语义未变
- [ ] 全程 mode=SHADOW，未设置或实现 ENFORCE

当前代码门禁：`shipMaritimeSceneShadowAuditJobHandler` 返回 `SHADOW_RUNNER_NOT_IMPLEMENTED` 并使 XXL-JOB 失败。完成独立 runner 任务前，不得执行 100/500/5,000。
