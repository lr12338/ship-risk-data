# 项目文档索引

## 当前状态与运维

- [0731液货船/集装箱船搭靠与徘徊测试维护手册](liquid-container-behavior-test-20260731.md)：
  当前5,000艘年度任务、规则、XXL参数、数据库验收和事件排障的唯一入口。
- [部署说明](../DEPLOY.md)：通用打包、上传、启动、停止和日志说明。

## 当前开发文档

- [海域场景数据离线 ETL](maritime-scene-data-etl.md)：第一阶段官方数据登记、下载校验、标准化、瓦片、QA 和发布命令。
- [海域场景 Shadow 接入](maritime-scene-shadow-integration.md)：`SHADOW_READY` 快照门禁、Java 独立服务、5,000 艘目标任务参数、审计与回滚。
- [Maritime Scene 全球基础数据 V1](maritime-scene-global-base.md)：全球向量数据范围、资源计划、覆盖状态、验证与发布边界。
- [全球基础区域规则契约](maritime-scene-global-base-rule-contract.md)：点级置信度、episode Shadow 结果和 UNKNOWN/CONFLICT 保守语义。

- [搭靠模块开发文档](ship-alongside-development.md)：搭靠任务链路、召回、
  检测、评分、落库和排障。
- [搭靠 V3 规则设计](船舶搭靠事件V3设计与实现.md)：保险风险识别规则、
  特征、评分和已知边界。
- [徘徊模块开发文档](ship-loitering-development.md)：V2 任务链路、轨迹处理、
  检测、评分、落库和排障。
- [徘徊 V3 规则设计](ship-loitering-v3-design.md)：V3 episode、场景排除、
  运动模式和生产门禁设计。
- [区域通行质量评估](zone-passage-quality-eval.md)：独立的区域通行质量任务。

## 文档维护规则

- 当前液货船/集装箱船测试状态只更新
  `liquid-container-behavior-test-20260731.md`。
- 开发文档只维护长期有效的接口、规则、数据流和边界，不记录滚动任务进度。
- 一次性部署证据、旧参数、旧测试任务和阶段性报告放入 `archive/`。
- 归档文档仅用于追溯，若与当前状态冲突，以当前状态文档和当前代码为准。
- 新文档使用仓库相对链接，不新增本机绝对路径。

## 历史归档

- `archive/production/`：保留一份2026-07-29旧5万商船状态证据。
- `archive/legacy/`：保留一份早期需求与实现映射，供追溯使用。
