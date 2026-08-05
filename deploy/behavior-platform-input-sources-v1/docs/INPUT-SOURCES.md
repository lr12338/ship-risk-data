# Behavior Platform unified input sources

The recommended handler is `shipBehaviorAuditJobHandler`; `shipBehaviorShadowV2FleetAuditJobHandler` delegates to it. The formal handler remains disabled. Every V2 profile is `FILE_AUDIT` and `productionApproved=false`.

- `SHIPS` accepts an ordered, deduplicated CSV of 9-digit MMSIs. It resolves metadata from the fixed read-only `ais41.recentships` table and fails closed for unknown or out-of-profile primary types.
- `LIST` reads only `dataanalysis.ship_behavior_target_ship`. `startSeq` means `seq_no > startSeq`; no table, SQL, or WHERE input is accepted.
- `REGION` accepts exactly one versioned `region` ID or a small `bbox`. Complex WKT is loaded only from the read-only region directory. The legacy Region handlers retain their original WKT contract.

REGION stays a separate scope: discovery and query planning run before metadata filtering; runners load full context trajectories; region attribution happens after behavior detection. PARTIAL discovery or analysis remains fail-closed.

Runtime rules are immutable per run. `AlongsideRuleConfig` and `LoiteringRuleConfig` build detector inputs from the behavior RuleSet; `SceneRuleConfig` controls timed aggregation and Scene decisions. No Spring singleton fields are mutated.

| Existing configuration baseline | V2 rule field | Default | Runtime read point |
|---|---|---:|---|
| `ship.behavior.region.shadow-rules.scene.gap-cap-minutes` | `gap-cap` | 10 min | `MaritimeSceneTraceEvaluator` |
| `...scene.min-temporal-coverage` | `min-temporal-coverage` | 0.75 | `MaritimeSceneTraceEvaluator` |
| `...scene.inland-time-ratio` | `inland-time-ratio` | 0.90 | `MaritimeSceneTraceEvaluator` |
| `...scene.inland-min-duration-minutes` | `inland-min-duration` | 60 min | `MaritimeSceneTraceEvaluator` |
| `...scene.open-water-time-ratio` | `open-water-time-ratio` | 0.90 | `MaritimeSceneTraceEvaluator` |
| `...scene.max-unknown-time-ratio` | `max-unknown-time-ratio` | 0.05 | `MaritimeSceneTraceEvaluator` |
| Alongside Shadow V2 / production detector baseline | `min-speed`, `max-speed`, `min-duration`, synchronization, geometry and stability fields | see `alongside-runtime-v2.json` | `AlongsideRuleConfig` → `ShipAlongsideDetector.AlongsideRule` |
| Loitering Shadow V2 / production detector baseline | speed, duration, coverage, shape, turn, tortuosity and transit rejection fields | see `loitering-runtime-v2.json` | `LoiteringRuleConfig` → `ShipLoiteringDetector.LoiteringConfig` |

Regression coverage: `RuleSetRuntimeConfigTest`, `BehaviorDetectorAdapterCharacterizationTest`, `CounterpartyShipTypePolicyTest`, `BehaviorCheckpointStoreTest`, and the existing Region runner/discovery/matcher suites.
