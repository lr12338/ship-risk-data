# WORLD_SEA_MASK V1R1 发布验证报告

## 数据库发布

```text
releaseId=WORLD_SEA_102_V1R1_31dd89bd4968_20260806_180406
repairReviewStatus=OPERATOR_AUTHORIZED
authorizationMode=OPERATOR_OVERRIDE
releaseStatus=ACTIVE
lastCompletedStep=ACTIVATE_DATASET
formalExists=true
formalRows=102
formalFingerprint=31dd89bd4968f8f436135b57cdb81e870ac7b827d4dc2c086282e7dfe91ab9c6
activeDatasetVersion=WORLD_SEA_102_V1R1
activeFingerprint=31dd89bd4968f8f436135b57cdb81e870ac7b827d4dc2c086282e7dfe91ab9c6
activeCount=1
recoveryNeeded=false
nextAllowedAction=NONE
```

数据通过 `REGISTER_STAGE → STATE → PUBLISH_STAGE → STATE → ACTIVATE_DATASET → STATE` 发布，未手工 UPDATE release 状态，未手工 RENAME。

## 代码与构建

```text
branch=codex/world-sea-v1r1-release
applicationGitSha=7f66c5015e2f8f5900f17eacae9c6d664fa4126f
tests=241
failures=0
errors=0
skipped=0
buildWorktree=detached-clean-worktree
gitDirtyBefore=false
gitDirtyAfter=false
jarSha256=4b808d683fec4d780ac4168960a1ab2ef0164d065e676e0c7de831f1c6e8bbc5
serverJarSha256=4b808d683fec4d780ac4168960a1ab2ef0164d065e676e0c7de831f1c6e8bbc5
```

## 服务与 Preflight

- 8090 和 30008 均已监听；应用启动日志无新的 Bean、数据库、WORLD_SEA 或 OOM 错误。
- Preflight：PASS；102 条、invalid=0、ACTIVE=1、指纹一致、索引就绪、databaseWrites=0。
- 加载耗时 783 ms，PreparedGeometry 5 ms，STRtree build 小于 1 ms。
- 预检时 RSS 约 702 MiB；20 船验证期间未发现新 OOM 或异常重启。

## 20 船验收

```text
targetListCount=5000
targetListDistinctMmsi=5000

loitering20: ships=20/20 windows=260 failed=0 candidateEvents=197 formalEvents=0
loitering20Mask: raw=200 strictPass=200 rejected=0

alongside20: ships=20/20 windows=260 failed=0 candidateEvents=5 formalEvents=0
alongside20Mask: raw=26 strictPass=26 rejected=0 rejectedWindows=9
alongside20Replay: baseline=1177 filtered=1165 saved=12 attempted=1165 successful=1165 failed=0 truncated=0
```

## 尚未完成

- 100 船徘徊 dry-run 已启动并交由人工监控；文档不将中间进度冒充为 PASS。
- 100 船搭靠、500 船和 5000 正式任务未触发。
- dry-run 审计表当前没有独立持久化 `shadow 95% pass` 计数；`storeDetails=false` 时也无法从事件详情回查。不得把 strict pass 冒充为 shadow 指标。

因此当前状态为“数据与 JAR 已生产发布，分级生产验证进行中”，不得表述为 5000 船任务已完成。

本报告之后的纯文档提交不会改变 `applicationGitSha` 或 JAR SHA；只有重新完成干净构建、部署和校验，才能更新上述构建身份。
