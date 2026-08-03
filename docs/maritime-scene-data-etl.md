# 船舶行为识别海域场景数据 ETL

本目录的 ETL 是离线数据链路，不改变 Java 正式事件规则、AIS 回放超时语义、生产配置、数据库表或部署流程。原始下载、解压 staging、标准化记录和 release 均位于被 Git 忽略的 `var/maritime-scene-data/`。Java 只读取显式提升到 `SHADOW_READY` 的 Release。

## 可验证成功标准

- 每个数据源在 `tools/maritime-scene-etl/config/datasets.yaml` 有唯一注册，包含官方页面、版本、格式、许可、署名、下载模式和目标文件名。
- `raw-manifests/<dataset_id>.json` 记录实际文件名、大小、SHA-256、最终 URL、ETag/Last-Modified（若服务器提供）、获取时间和 CRS 状态。
- 标准化记录只使用 WGS84，经几何状态标记、动态字段解析和隔离区分；无法解析的几何不进入正式场景层。
- release 具有 `manifest.json`、`checksums.sha256`、`validation-report.json`、`license-attribution.md`、`source-manifests/`、1° 压缩瓦片和 QA GeoPackage。
- 快照指纹只依赖原始清单、处理配置、ETL 版本/代码提交和瓦片路径/哈希，不包含绝对路径或构建时间。
- Golden Points 现在是 `OPTIONAL_MANUAL_REFERENCE`，默认 0 个启用点不会阻塞自动发布；自动样本是处理一致性检查，不是人工地理真值。
- Release 状态按 `RAW_VERIFIED → GEOMETRY_VALIDATED → AUTO_CROSS_VALIDATED → SHADOW_READY` 递进；本轮不实现 `ENFORCE`。

## 运行入口

首次准备独立 Python 环境：

```bash
make -C tools/maritime-scene-etl venv
tools/maritime-scene-etl/bin/scene-etl doctor
tools/maritime-scene-etl/bin/scene-etl list-datasets
```

原始数据获取和校验：

```bash
tools/maritime-scene-etl/bin/scene-etl acquire --all
tools/maritime-scene-etl/bin/scene-etl verify-raw --all
tools/maritime-scene-etl/bin/scene-etl inspect --dataset hydrorivers_v1

# JRC Global Surface Water 1.5（1984–2024）
tools/maritime-scene-etl/bin/scene-etl jrc-plan --bbox 120.5,30.5,122.5,32.0 --mode TARGETED --json
tools/maritime-scene-etl/bin/scene-etl jrc-acquire --bbox 120.5,30.5,122.5,32.0 --mode TARGETED --json
tools/maritime-scene-etl/bin/scene-etl jrc-verify --all --json
tools/maritime-scene-etl/bin/scene-etl jrc-build-index --json
tools/maritime-scene-etl/bin/scene-etl jrc-sample --lon 121.5 --lat 31.5 --json
```

先做小范围 smoke，再做完整 release。`--bbox` 顺序为 `minLon,minLat,maxLon,maxLat`，范围内几何会被裁剪后再写入瓦片：

```bash
tools/maritime-scene-etl/bin/scene-etl normalize --all --bbox 120.5,30.5,122.5,32.0
tools/maritime-scene-etl/bin/scene-etl build --release phase1-smoke-shanghai --bbox 120.5,30.5,122.5,32.0
tools/maritime-scene-etl/bin/scene-etl build --release phase1-20260803-v1
tools/maritime-scene-etl/bin/scene-etl validate --release phase1-20260803-v1
tools/maritime-scene-etl/bin/scene-etl fingerprint --release phase1-20260803-v1
tools/maritime-scene-etl/bin/scene-etl report --release phase1-20260803-v1
tools/maritime-scene-etl/bin/scene-etl golden-candidates --release phase1-smoke-shanghai --json
tools/maritime-scene-etl/bin/scene-etl estimate-build --scope global --json
tools/maritime-scene-etl/bin/scene-etl validate --release phase1-smoke-shanghai-autocross-v1 --json
tools/maritime-scene-etl/bin/scene-etl release-status --release phase1-smoke-shanghai-autocross-v1 \
  --status SHADOW_READY --policy-version MARITIME_SCENE_SHADOW_V1 --json
```

`normalize --force` 只用于明确要求重建本地规范化 staging；Release 目录一旦存在即不可覆盖，必须使用新的 release 名称。两者都不会删除原始下载文件，也不会操作生产数据库。当前命令默认使用仓库下的 `var/maritime-scene-data/`，可用 `--data-root` 指定另一个本地目录。

## 数据源边界

已注册源及处理限制如下：

| 数据源 | 目标版本/产品 | 处理边界 |
| --- | --- | --- |
| Marine Regions | World EEZ v12、12NM v4、Internal Waters v4、Archipelagic Waters v4、High Seas v2 | 官方下载需要接受免责声明，保留 `MANUAL_ACTION_REQUIRED`；不把边界描述为航海或法律裁决 |
| HydroSHEDS | HydroRIVERS v1、HydroLAKES v1 | 通过官方产品直链下载；河流字段按配置候选动态解析，并要求河流阶次 ≥5 且平均流量 ≥10 m³/s，湖泊优先使用源面积字段并支持测地线回退 |
| NOAA NCEI/SOEST GSHHG | v2.3.7 | 只处理 `h` high resolution 下的 `L1` land/ocean 边界；明确不处理 Full resolution |
| OpenStreetMap/Geofabrik | Egypt、Panama、Schleswig-Holstein PBF | 只产出 Suez/Panama/Kiel 目录中的候选；目前 `REVIEW_REQUIRED`，不进入批准的 `major_canal` 场景层 |
| Joint Research Centre / Global Surface Water | GSW1_5-2024，1984–2024，30 m | 官方 10°×10° GeoTIFF；Occurrence、Seasonality、Recurrence、Extent 只作为水面证据，不替代 Marine Regions 法域或航海判断 |

OSM 最新区域 PBF 是可变下载对象，清单保留获取时的 HTTP 元数据。Marine Regions 的官方免责声明和各数据源许可证/署名随 release 一起保存，不将未知的法律边界含义写进业务规则。

## 人工下载协议

当 CLI 输出下列块时，必须逐字段执行，不改名、不解压、不从镜像替换：

```text
MANUAL_ACTION_REQUIRED
数据源：Marine Regions
产品名称：Maritime Boundaries Geodatabase
目标版本：World EEZ v12; Territorial Seas v4; Internal Waters v4; Archipelagic Waters v4; High Seas v2
阻塞原因：官方下载页面要求人工提交表单并接受免责声明
官方页面：https://www.marineregions.org/downloads.php
保存到本地路径：var/maritime-scene-data/raw/marine-regions/
```

实际文件名和版本以 `datasets.yaml` 与 CLI 输出为准。下载完成后执行 `verify-raw --dataset <dataset_id>`；该命令会在人工文件尚无清单时生成清单，然后再执行 `normalize`/`build`。

## 自动交叉验证

每个自动 Release 会生成：

- `validation/layer-overlap-matrix.json/csv`：预定义 15 组图层关系，区分 `EXPECTED_OVERLAP`、边界接触和 `CONFLICT`；`river/lake/canal × land` 是可解释的预期重叠。
- `validation/coast-distance-report.json`：按本地投影计算到 GSHHG 海岸线的海里距离，并支持跨 1° 瓦片搜索。
- `validation/auto-samples.json`、`auto-samples-report.json`：自动生成 Land、River、Lake、Marine、Boundary、Negative 样本；不构成人工真值，未知和冲突单独统计。
- `validation/cross-source-validation.json`：几何自洽与跨源一致性分开报告；没有 JRC 覆盖时不会把水面证据升级为高置信。
- `validation/automatic-validation.json`、`qa/conflicts.gpkg` 和冲突 NDJSON：严重冲突定位后默认不阻塞 Shadow，但不得伪装为无冲突。

高置信规则要求河流/湖泊有几何与 JRC 稳定水面证据，海域分类还要通过海岸距离和边界策略；`UNKNOWN`、`CONFLICT` 不是硬排除条件，也不会升级为高置信。GSHHG `land` 与河流、湖泊的重叠是来源语义关系，不表示把河道从陆地数据中裁掉。

## 输出层

- `scene`：正式候选场景记录；每条记录包含 `sceneType`、来源标识、权威级别、置信度、几何状态、WKB 和源属性。
- `coast`：GSHHG 海岸线 QA 层，不作为生产排除规则。
- `review`：OSM canal review-required 候选，只放 QA，不自动升级为批准场景。
- `quarantined_geometry`：几何修复失败或类型不一致的隔离记录。
- `qa/maritime-scene-canonical.gpkg`：供人工 GIS 检查的 canonical QA 层。

`land`、河流走廊、湖泊和 Marine Regions 法域层是来源候选层，允许存在空间重叠。GSHHG land 只表达海岸线陆地，不扣除河道水面；Marine Regions EEZ 产品也可能覆盖内部水域。Golden Point 候选命令只生成最近要素、距离、来源和置信度报告，不修改 `golden-points.yaml`，不自动启用验证点。

Golden Points 仍可供人工抽查，但不是自动 Release 的阻塞门禁，也不再要求用户打开 QGIS 才能完成本轮自动校验。若业务需要人工参考，可运行 `golden-candidates` 后在任意 GIS 工具中复核来源 ID、坐标和语义；启用点仍必须保留依据，且不能把候选距离当作生产真值。

由于河流走廊与 `land` 重叠是预期现象，若某个河流 Golden Point 将 `land` 列入 `forbidden_hits`，即使命中河流走廊也可能被验证器拒绝；应先复核该点的业务语义和配置证据，不通过裁剪图层来绕过冲突。

## 全球与分区构建

`estimate-build --scope global` 会先估算压缩原始数据、解压、处理和安全余量；全球构建默认建议 `PARTITIONED_STREAMING`，不在本地一次性加载全球几何。区域构建使用 `--bbox`，每个区域使用独立 Release 名称、manifest、checksum 和 fingerprint。JRC `GLOBAL_IF_SAFE` 只有安全余量通过时才允许计划，全局资源不足会 fail-closed。

## JRC 版本、许可与限制

当前策略固定为 `GSW1_5-2024`，官方页面为 <https://global-surface-water.appspot.com/download>，数据用户指南为 <https://storage.googleapis.com/water-world/downloads_ancillary/DataUsersGuidev2024_v.5.pdf>。Release 保留下载文件 SHA-256、产品版本、NoData=255、30 m/EPSG:4326 和处理策略；署名为 `Source: EC JRC/Google`，并引用 Pekel et al. (2016)。Occurrence、Seasonality、Recurrence 只能说明水面证据，不能作法律边界、航海许可或导航安全裁决；JRC 缺失覆盖时记录 `NO_COVERAGE`。

数据更新时应下载到新的 data root 或生成新的 Release，重新执行 raw 校验、JRC index、自动验证和 fingerprint；不要覆盖既有 Release。回滚只需把 Java 外置配置切回 `mode=OFF` 或指向前一份通过门禁的快照，不删除 Release、不执行数据库 DDL。
