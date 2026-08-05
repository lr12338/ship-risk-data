# Scene 决策规则

| 模式 | 读取 Scene | 改变控制组 | 实验输出 |
|---|---:|---:|---|
| OFF | 否 | 否 | 控制结果 |
| SHADOW | 是 | 否 | Scene 证据，候选仍保留 |
| FILTER | 是 | 否 | 开放水域 ACCEPT；明确内陆 REJECT；其余 REVIEW |

UNKNOWN、CONFLICT、低覆盖和沿海不确定不得视为开放水域。运行时策略与快照策略分别存入 `SceneRuntimeInfo` 和 `SceneSnapshotInfo`，不会通过 Map 合并互相覆盖。
