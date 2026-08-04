# Behavior + Maritime Scene Shadow V2 手工部署

本包只包含应用 JAR、配置增量和人工触发参数。Scene fingerprint 固定为
`5c35fd542d492c5857e546a635f5a71790f2bf321b1590b84eca39c2052dec6e`，服务器已有
`phase1-global-base-v1`，无需重新上传 Runtime。

## 部署顺序

1. 上传本应用部署包及其 `.sha256`，执行 SHA-256 校验。
2. 备份现有应用 JAR 和完整配置文件，记录备份路径与时间。
3. 只人工合并 `config/application-properties-shadow-v2.patch.txt` 增量；不得覆盖完整配置。
4. 替换应用 JAR并重启服务。
5. 检查旧 Handler 与 `shipBehaviorRegionDryRunJobHandler`、`shipMaritimeScenePreflightJobHandler` 均已注册。
6. 人工触发 `jobs/scene-preflight.json`；确认 fingerprint、30645 Scene tiles、5893 Coast tiles、SHADOW、`databaseWrites=0`、`formalEventMutation=false` 和规则版本。
7. 串行执行三个 2 小时任务：明确内河、港区/锚地/近岸、远岸开放水域。
8. 串行执行 6 小时搭靠 A/B，再执行 24 小时组合任务。
9. 按结果回传清单返回 Manifest、事件 NDJSON 和脱敏日志。

所有任务均须人工触发、单机串行、`parallelism=1`。禁止触发 5000 艘 Audit，禁止 ENFORCE，禁止数据库写入。

## 回滚

停止服务，恢复步骤 2 的旧 JAR 和旧配置，重启并检查旧 Handler。不得删除或覆盖 Scene Release，不执行 SQL，不删除 Region 历史输出。回滚后运行旧 Region preflight/dry-run 小样本确认。
