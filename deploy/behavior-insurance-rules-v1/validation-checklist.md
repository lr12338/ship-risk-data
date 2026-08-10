# 分级验证与停止条件

## 发布前

- [ ] 本地 `mvn clean test`、`mvn clean package -DskipTests`、`git diff --check` 均通过。
- [ ] JAR 的 Git SHA、构建时间、SHA-256 已记录，并与上传文件一致。
- [ ] WORLD_SEA active dataset 为 `WORLD_SEA_102_V1R1`；港区/锚地 scene 加载成功。
- [ ] 服务器配置只合并 delta，保留数据源、WORLD_SEA 与 XXL-Job 原有配置。

## 20 / 100 / 500 dry-run

- [ ] 每个任务必须 `enableInsert=false`、`parallelism=1`、`resume=true`，且使用参数文件给定的未复用 `batchNo`。
- [ ] 先执行 20；状态表 `FAILED=0`、WORLD_SEA 审计没有 failed/truncated replay、正式事件表该批次为 0 条，才能进入 100。
- [ ] 100 通过后才执行 500；任何失败、场景数据不可用、配置 hash 不一致或正式事件非 0 都停止，不得以更换 batchNo 绕过。
- [ ] 检查 Loitering：港区/锚地/低于 1kn 被拒绝，`OFFSHORE` 没有作为“距离港口过远”被拒绝；正式候选审计中 `belowMinSpeedEvidenceCount=0`。
- [ ] 检查 Alongside：港区/锚地为排除，`PORT_APPROACH` 与 `OFFSHORE` 可保留；静止并靠仅 review。

## 5000 正式写库

- [ ] 20、100、500 两个行为均通过，并已人工审核场景、距离桶、行为模式、异常与样本轨迹。
- [ ] 两条 5000 参数已再次确认 `enableInsert=true`，且 `batchNo`、时间范围、名单、配置 hash 未被修改。
- [ ] Loitering 与 Alongside **串行**执行；禁止并发、禁止自动重试时改 batchNo。
- [ ] 完成后状态表无失败，正式事件数与状态表 candidate/event 统计可解释一致；保留 `query.sql` 输出作为验收记录。

## 失败恢复与回滚

- [ ] 窗口失败：修复根因后使用完全相同 JSON、原 `batchNo` 和 `resume=true` 续跑。
- [ ] JAR/配置启动失败：停止服务，恢复本次备份 JAR 与 `application.properties`，重新启动；不要删除批次状态、审计表或已经生成的正式事件。
