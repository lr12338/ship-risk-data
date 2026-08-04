# Rule Changelog

## Behavior + Maritime Scene Shadow V2

- Scene 聚合新增 AIS 时间权重、10 分钟 gap cap、时间比例、覆盖率和 gap 诊断。
- 搭靠新增一对一 3 分钟同步、日期变更线安全中点 Scene，并保留 own/target/pair 三组 Summary。
- Region STRICT_CORE 在 V2 下同时要求点数与时间比例；边界事件也运行诊断但不计有效事件。
- 搭靠 Rule B 委托现有严格规则的动态距离与几何指标，细分舷侧、跟随、追越、交叉等 Shadow 结论。
- 徘徊 Rule B 委托现有严格形态规则，追加时间速度分支，区分闭环/折返、直线低速与静止等待。
- 新增四层决策、reject reasons 和 Manifest A/B 统计。
- Scene 决策 policy V2 与既有 Runtime snapshot policy V1 分离；fingerprint 不变，不重打 Runtime。
- 新功能默认关闭，ENFORCE 与数据库写入启动即拒绝；旧 V2/V3 Rule A 和旧 Handler 未改。
