# Maritime Scene 全球基础区域规则契约

本契约只约束 Shadow 证据。`LOW`、`MEDIUM`、`UNKNOWN`、`CONFLICT`、`BOUNDARY_UNCERTAIN`、`NOT_COVERED` 和 `TILE_LOAD_FAILED` 不排除、不增强、不加分、不写 `open_sea`，不允许硬门禁。

| 场景 | 高置信条件 | Shadow 结果 | 硬门禁 |
| --- | --- | --- | --- |
| `LAND` | GSHHG land 内部，无高置信 lake/canal 水面覆盖 | 记录非有效水域证据 | 否 |
| `HIGH_SEAS` | 只命中 High Seas，无 land/lake/canal/territorial/internal/conflict，距法域边界超容差，瓦片已覆盖 | episode 高置信比例达标后 `OPEN_WATER_CONFIRMED` | 否 |
| `OFFSHORE_EEZ` | EEZ 内部，无 territorial/internal/archipelagic/land/lake/canal/conflict，海岸距离至少配置的 12 nm | 达标后 `OPEN_WATER_CONFIRMED` | 否 |
| `TERRITORIAL_SEA` | Marine Regions Territorial Sea | `COASTAL_REVIEW` | 否 |
| `MARITIME_INTERNAL_WATER` | Marine Regions Internal Waters | `COASTAL_REVIEW` | 否 |
| `ARCHIPELAGIC_WATER` | Marine Regions Archipelagic Waters | `COASTAL_REVIEW` | 否 |
| `INLAND_LAKE` | HydroLAKES `Lake_area >= 50 km²`，几何有效，距边界超 1,000 m，覆盖完整 | HIGH 比例 ≥ 0.90 且持续 ≥ 60 分钟时 `WOULD_REJECT_INLAND` | 否 |
| `INLAND_RIVER` | HydroRIVERS 中心线/米制走廊默认 LOW；只有 `runtimeWaterSupport=true`、episode 连续命中、非河口带、无冲突和覆盖完整才可升 HIGH | 无运行时支持必须 `SCENE_UNKNOWN`；HIGH 比例/时长达标才 `WOULD_REJECT_INLAND` | 否 |
| `MAJOR_CANAL` | 仅 Suez/Panama/Kiel 固定 APPROVED OSM way 白名单，连通性通过，在 100 m 走廊内且距边界超 25 m | 与 lake 相同的 episode 门槛 | 否 |
| `UNKNOWN` / `CONFLICT` | 缺失、未覆盖、载入失败、边界或多源矛盾 | 只记证据 | 否 |

12 nm 是业务保守阈值，不是法定海域裁决；Territorial Sea 仍以 Marine Regions 图层为边界证据。Lake 与 GSHHG land、River/Canal 与 land 重叠是来源语义，不自动算冲突。无全球 JRC 时，HydroRIVERS 绝不能单独产生高置信排除。
