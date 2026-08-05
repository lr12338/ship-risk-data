# Behavior + Maritime Scene Shadow V2 本地验证

验证日期：2026-08-04。

- 起始基线：265 tests，0 failures，0 errors，0 skipped。
- 新增验收测试：37 tests。
- 最终 `mvn test`：302 tests，0 failures，0 errors，0 skipped，7.469 秒。
- 新增覆盖：Scene 时间权重和 gap cap、内河持续时间、同步船对与日期变更线、中点 Scene、Region 点数+时间双门槛、边界诊断、搭靠舷侧/跟随/追越/交叉/相对运动/距离稳定性、徘徊闭环/往返/直线/单转向/静止/稀疏 AIS、四层组合决策、船型 fail-closed、安全参数和部署模板。
- 安全回归：旧 `LOITERING_V2_INSURANCE`、`ALONGSIDE_V3_INSURANCE` 候选路径未修改；Shadow Rules OFF 不产生 V2 决策；正式事件对象在 enrichment 前后 JSON 一致；Region Manifest 固定输出 `databaseWrites=0`、`formalEventMutation=false`、`enforceEnabled=false`。

最终交付前还需依次通过 `mvn clean package -DskipTests`、`git diff --check`、JAR 内容/敏感信息检查和部署包校验。
