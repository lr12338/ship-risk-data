# 徘徊规则

内置 `loitering-insurance-v1:1` 未获生产批准。机器可读阈值在 `src/main/resources/behavior-rule-sets/loitering-insurance-v1.json`。

当前证据覆盖轨迹覆盖率、持续时间、速度、半径、转向、位移/通过排除和 Scene。V1 复用现有 `ShipLoiteringDetector`，不修改其他行为或评分模块。行为结果是标准化风险证据，不直接修改船龄、事故、PSC、运营或年度保险评分。
