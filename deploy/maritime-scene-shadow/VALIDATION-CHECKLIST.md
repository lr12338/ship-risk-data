# Shadow 验收清单

- [ ] `SELECT DATABASE()` 返回 `dataanalysis`
- [ ] 包内 `scripts/verify-package.sh` PASS
- [ ] 服务端 `scripts/verify-server-files.sh` PASS
- [ ] `shipMaritimeScenePreflightJobHandler` 返回 PASS、SHADOW_READY、fingerprintMatch=true、checksumFailures=0
- [ ] 100 艘 dry-run 期间 `audit.store-enabled=false`
- [ ] 正式 event/detail 前后 COUNT、MAX(id)、MAX(created_at) 完全一致
- [ ] 100 艘写库前人工执行 Shadow DDL，并把 store-enabled 改为 true 后重启
- [ ] Shadow 表以外没有写入；重复运行 audit_key 无重复
- [ ] 500 艘前 100 艘 UNKNOWN/CONFLICT、内存、缓存、timeout 均可解释
- [ ] 5,000 艘前存在覆盖目标轨迹范围的全球/分区 Release；当前上海 Release 不满足此项
- [ ] 全程 mode=SHADOW，未设置或实现 ENFORCE

当前代码门禁：`shipMaritimeSceneShadowAuditJobHandler` 只完成预检并返回 READY，不会遍历船舶或 AIS episode。完成实际 runner 并通过 OFF/SHADOW 同轨迹回归前，不得执行 100/500/5,000 阶段。
