# Behavior Profiles

内置 Profile：`alongside-control-off-v1`、`alongside-scene-shadow-v1`、`alongside-scene-filter-v1`、`alongside-counterparty-extended-v1`、`alongside-scene-ab-v1`、`loitering-control-off-v1`、`loitering-scene-shadow-v1`、`loitering-scene-filter-v1`、`loitering-scene-ab-v1`。

外部目录由 `ship.behavior.profile-dir` 指定。目录应只读；新配置必须使用新的 `profileId` 和显式 `-vN` 后缀。重复 ID、未知父 Profile、继承循环、未知 RuleSet 或缺字段均阻止启动。任务参数只能把 Scene/写入权限降级。

内置文件位于 `src/main/resources/behavior-profiles/`，外部样例位于 `config/behavior-profiles/`。
