# 分阶段测试顺序

所有区域参数先替换 `<POLYGON_WKT>`、`<START_TIME>`、`<END_TIME>`、`<BATCH_SUFFIX>`。Polygon 必须是小范围 Polygon/MultiPolygon；时间范围必须分别严格等于文件名标注的 2、6、24 小时。每次使用新的 batchNo。

1. `shipMaritimeScenePreflightJobHandler` + `jobs/scene-preflight.json`：只接受 `PASS`；不查询 5,000 艘、不写库。
2. `shipBehaviorRegionPreflightJobHandler` + `jobs/region-preflight-2h.json`：只接受 `PASS`，并确认 `realAisQueryExecuted=false`、`databaseWrites=0`。
3. `shipBehaviorRegionDryRunJobHandler` + `jobs/region-loitering-2h.json`：确认只输出 LOITERING。
4. `shipBehaviorRegionDryRunJobHandler` + `jobs/region-alongside-6h.json`：确认只输出 ALONGSIDE，搭靠 pair 已去重。
5. `shipBehaviorRegionDryRunJobHandler` + `jobs/region-combined-24h.json`：确认同时输出 LOITERING/ALONGSIDE，正式事件零变化。

每阶段通过后才进入下一阶段。`PARTIAL`/`PARTIAL_TIMEOUT` 只作为不完整 dry-run 结果记录，不得当作完整成功，也不得写正式事件。

禁止运行 `shipMaritimeSceneShadowAuditJobHandler`。当前它必须返回 `SHADOW_RUNNER_NOT_IMPLEMENTED` 且 XXL-JOB 失败。
