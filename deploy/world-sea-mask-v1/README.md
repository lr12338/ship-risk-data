# WORLD_SEA_MASK V1R1 发布与生产验证

本文档记录 2026-08-07 实际发布结果。`world_sea` 仅表示全球命名海洋水域 `WORLD_SEA_NAMED_AREA`，不表示法律公海、EEZ/领海、主权、合规或风险分类。

## 当前状态

- 数据集：`WORLD_SEA_102_V1R1`
- 授权：`OPERATOR_AUTHORIZED`，`authorizationMode=OPERATOR_OVERRIDE`
- release：`WORLD_SEA_102_V1R1_31dd89bd4968_20260806_180406`，状态 `ACTIVE`
- formal：`dataanalysis.ship_behavior_world_sea`，102 条
- 唯一 ACTIVE：1 条，指纹与 formal 一致
- recovery：`false`
- 应用代码 SHA：`7f66c5015e2f8f5900f17eacae9c6d664fa4126f`
- 生产 JAR SHA-256：`4b808d683fec4d780ac4168960a1ab2ef0164d065e676e0c7de831f1c6e8bbc5`
- 服务：PID 会随重启变化；8090 HTTP 与 30008 XXL executor 必须同时监听

应用代码 SHA 和 JAR 身份以发布包 `release-manifest.json` 为准；后续纯文档提交不会改变已经部署的 JAR，也不得据此伪称重新构建或重新部署。

旧 JAR `c0e90d35da9de3c71ef4fd996364de8e6bb72b0293dffe2d9208843350b37002` 已标记为 `SUPERSEDED_NOT_FOR_WORLD_SEA_102_V1R1`，不得部署。

## 固定数据身份

- 原始 stage：`dataanalysis.ship_behavior_world_sea_stage_20260806_155556`
- repaired stage 原名：`dataanalysis.ship_behavior_world_sea_stage_20260806_180406`（发布时已由状态机 RENAME 为 formal）
- 原始文件 SHA-256：`de7d39f8e652721796d64ce8697bb036da30280500940de8ece70ece4914266b`
- 原始 stage 指纹：`81c810a9db7d319fdfbf9a17805a61493d3beda23b09154958ce2a2b3a31fd1e`
- repaired/formal 指纹：`31dd89bd4968f8f436135b57cdb81e870ac7b827d4dc2c086282e7dfe91ab9c6`
- 修复方法：`JTS_GEOMETRY_FIXER_1_18_2`
- 变化 ID：`11,26,85,91,92,99,24556`；其余 95 条 WKB 原样不变
- Baffin Bay：`DROP_NON_POLYGONAL_COLLAPSE`
- Bo Hai：`RETAIN`

`repair-report.*` 中的 `PENDING_REVIEW` 是报告生成时的历史状态，不得改写原始证据。最终授权以 `release-authorization.json` 和数据库 repair audit 的 `OPERATOR_AUTHORIZED` 为准，不伪造人工 reviewer。

## 实际 Preflight

`shipBehaviorWorldSeaMaskPreflightJobHandler` 已 PASS：

```text
featureCount=102
invalidGeometryCount=0
activeDatasetCount=1
datasetVersion=WORLD_SEA_102_V1R1
geometryFingerprint=31dd89bd4968f8f436135b57cdb81e870ac7b827d4dc2c086282e7dfe91ab9c6
indexReady=true
loadElapsedMs=783
preparedGeometryBuildElapsedMs=5
strtreeBuildElapsedMs=0
databaseWrites=0
```

预检时进程 RSS 约 702 MiB，未发现新的 Full GC、OOM、Bean 初始化或 WORLD_SEA 加载错误。

## 实际 dry-run 结果

| 阶段 | 批次 | DONE 窗口 | FAILED | 候选事件 | 正式事件 | 结果 |
|---|---|---:|---:|---:|---:|---|
| 20 徘徊 V2 | `loitering-merchant20-2025-worldsea-v1r1-smoke` | 260 | 0 | 197 | 0 | PASS |
| 20 搭靠 V3 | `alongside-merchant20-2025-worldsea-v1r1-smoke` | 260 | 0 | 5 | 0 | PASS |
| 100 徘徊 V2 | `loitering-merchant100-2025-worldsea-v1r1` | 运行中 | 0（最后记录） | 运行中 | 0 | 人工接管 |
| 100 搭靠 V3 | `alongside-merchant100-2025-worldsea-v1r1` | 未触发 | — | — | — | WAIT |
| 500 / 5000 | 见 `xxl-job-params.json` | 未触发 | — | — | — | WAIT |

20 船搭靠的 Replay：`baseline=1177`、`filtered=1165`、`saved=12`、`attempted=1165`、`successful=1165`、`failed=0`、`truncated=0`。

## 手工运行规则

- 所有任务使用单机路由、`SERIAL_EXECUTION`、`parallelism=1`、手动触发。
- 搭靠和徘徊禁止并发；必须先看到前一任务 `job execute end(finish)`。
- 20/100/500 必须 `enableInsert=false`，数据库正式事件数必须为 0。
- 只有 20/100/500 全部 PASS 后才可手动触发 5000，且两个 5000 任务不得并发。
- 中断后必须使用原 `batchNo`、原参数和 `resume=true`。
- 当前无 XXL 管理中心登录凭据：`XXL_ADMIN_AUTH_UNAVAILABLE`。不得猜测密码或输出 executor token。

详细操作见 `manual-xxl-validation.md`，验收 SQL 见 `query.sql`，回滚见 `rollback.md`。
