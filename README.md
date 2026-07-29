# ship-risk-data

船舶风险行为批量计算服务，复用 HBase 轨迹、MySQL、空间 AIS 回放和
XXL-JOB，当前重点验证搭靠与徘徊 5 万船历史生产任务。

## 当前生产测试

- [搭靠与徘徊 5 万船生产测试状态跟踪](docs/ship-behavior-production-test-status.md)
- 当前搭靠批次：`alongside-prod-2026h1-50k-v1`，正在原批次续跑。
- 当前徘徊批次：`loiter-prod-2026h1-50k-v2-valid-v1`，等待搭靠停止后分段启动。
- 两个生产任务不得同时运行；实际进度、参数和下一步以状态跟踪文档为准。

## 文档入口

- [项目文档索引](docs/README.md)
- [部署说明](DEPLOY.md)
- [搭靠模块开发文档](docs/ship-alongside-development.md)
- [搭靠 V3 规则设计](docs/船舶搭靠事件V3设计与实现.md)
- [徘徊模块开发文档](docs/ship-loitering-development.md)
- [徘徊 V3 规则设计](docs/ship-loitering-v3-design.md)

历史运行手册、测试报告和部署证据统一放在 `docs/archive/`，不得用归档文档
替代当前状态文档配置生产任务。
