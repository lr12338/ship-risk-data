# Maritime Scene 全球基础数据 V1

`phase1-global-base-v1` 是一个确定性、不可原地覆盖的 `GLOBAL VECTOR BASE`。它包含 Marine Regions EEZ v12、Territorial Sea v4、Internal Waters v4、Archipelagic Waters v4、High Seas v2；GSHHG 2.3.7 high-resolution L1 land/coastline；HydroLAKES v1 中面积至少 50 km² 的湖泊和水库；HydroRIVERS v1 中 `ORD_FLOW >= 5 AND DIS_AV_CMS >= 10` 的主要河流候选；以及固定 OSM way 白名单中的 Suez、Panama、Kiel 三条运河。

这些数据只是事件 Shadow 证据，不是法律海域、通航许可或导航安全裁决。本版没有全球 JRC、航道、ENC、TSS、STS、法定航路、全球运河、小型湖泊/河流或高精度河道 Polygon。上海 JRC Release 保留为独立的 `REGIONAL JRC OVERLAY`，不合并到全球基础版。

## 构建和资源边界

构建按数据源流式读取，按 1° 瓦片 spool 和确定性排序，不执行全球 union/difference，不建立单个全球 STRtree，不将 Release 装入 JAR。跨瓦片 Feature 可复制，Java 以 `sceneType + sourceFeatureId + geometryType` 去重；日期变更线几何在规范化时拆分。验证也按瓦片运行，避免全球几何笛卡尔积。

```bash
tools/maritime-scene-etl/bin/scene-etl verify-raw --all --json
tools/maritime-scene-etl/bin/scene-etl estimate-build --release phase1-global-base-v1 --json
tools/maritime-scene-etl/bin/scene-etl normalize --all --force --json
tools/maritime-scene-etl/bin/scene-etl build --release phase1-global-base-v1 --json
tools/maritime-scene-etl/bin/scene-etl validate --release phase1-global-base-v1 --json
tools/maritime-scene-etl/bin/scene-etl fingerprint --release phase1-global-base-v1 --json
```

资源不足时优先按 `marine`、`land-coast`、Asia、Europe、Africa、North America、South America、Oceania 独立重跑后组合 manifest；不删减 Marine Regions/GSHHG。湖泊或河流阈值如需收紧，必须改配置并进入 fingerprint/报告，不允许静默调整。

## 覆盖、验证和发布

Manifest 显式记录 `globalCoverage`、`coveredTileCount`、`emptyCoveredTileCount`、`notCoveredTileCount` 和 `layerCoverage`。`COVERED_EMPTY` 表示瓦片已处理但没有 Feature；`NOT_COVERED` 表示未处理；文件破损是 `TILE_LOAD_FAILED`。后两者只能输出 `SCENE_UNKNOWN`。

自动验证产出全球瓦片 CSV/报告、13 组重叠关系、海岸距离、冲突详情、固定种子样本、12 区域图册和简化 QGIS 包。`BLOCKING_CORE` 内部样本通过率必须至少 98%；River 是 `NON_BLOCKING_CANDIDATE`；边界点是 `BOUNDARY_EXPECTED`。同一输入连续构建的瓦片 gzip SHA 和 fingerprint 必须一致。

Release 只能按 `RAW_VERIFIED → GEOMETRY_VALIDATED → GLOBAL_CORE_VALIDATED → AUTO_CROSS_VALIDATED → SHADOW_READY` 促进。仅当核心源、三条运河、样本、重叠、确定性、Java 读取和 OFF/SHADOW 零变化都通过时才可标记 `SHADOW_READY`。本版不具备 `ENFORCE_READY` 条件。
