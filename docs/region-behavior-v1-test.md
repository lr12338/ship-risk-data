# Region Behavior V1 测试记录

## 自动化验收

覆盖范围：

- Polygon、MultiPolygon、自交 MakeValid、空几何、坐标越界、跨日期变更线 fail-closed、确定性指纹、米制 buffer、bbox。
- dry-run/store 安全参数、凭据键拒绝、时间上限和保守默认值。
- 时空切片、确定性 slice ID、截断后先时间后空间细分、终端截断、timeout、parse error、点去重、精确 Polygon 过滤、候选上限、PARTIAL 不误报 COMPLETE。
- 船型元数据分批、现有 merchant classifier、长度资格和 UNKNOWN 默认排除。
- 徘徊 STRICT_CORE/边界事件；搭靠双船点、pair midpoint、A-B/B-A 及小时间偏差去重。
- 完整 context 轨迹窗口、V2 production filter、搭靠 replay failure/truncation 保持为不完整。
- preflight 零 AIS/分析调用、PARTIAL XXL-JOB 失败、dry-run 全套文件、零数据库写入声明和 checksums。

## 执行项

完成前必须执行：

```text
mvn test
mvn clean package -DskipTests
git diff --check
```

本分支最终验证：

- Region Behavior V1 定向测试：37 tests，0 failures，0 errors。
- 2026-08-04 Behavior + Maritime Scene Shadow V2 全仓回归：302 tests，0 failures，0 errors，0 skipped；原有搭靠、徘徊、replay timeout/truncation、事务存储和 Region V1 套件均通过。
- `mvn clean package -DskipTests`：BUILD SUCCESS，生成 `target/ship-risk-data-0.0.1-SNAPSHOT.jar`。
- `git diff --check` 与 `git diff --cached --check`：通过。

## 真实接口 Smoke

本地自动化验收使用 fixture/mock，不伪造真实 AIS/HBase 运行。如环境可用，运维应使用小 Polygon 依次执行 2h、6h、24h，并将 API/HBase 耗时、raw/unique points、候选/合格船、行为候选/区域事件、timeout/truncated、峰值内存和 SHA-256 记录到独立运行报告。
