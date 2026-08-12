# ship-risk-data

船舶风险行为数据服务，当前部署脚本统一位于 `deploy/`，业务文档位于 `docs/`。

## 常用入口

- [部署与运维](DEPLOY.md)
- [行为保险规则 V1：测试服部署、规则与 XXL-Job 手册](deploy/behavior-insurance-rules-v1/README.md)
- [搭靠生产测试参数与系统维护](docs/alongside-production-test-operations.md)
- [船舶搭靠模块开发文档](docs/ship-alongside-development.md)
- [船舶搭靠事件 V3 设计与实现](docs/船舶搭靠事件V3设计与实现.md)
- [船舶徘徊模块开发文档](docs/ship-loitering-development.md)

## 本地验证

```bash
mvn clean test
mvn clean package -DskipTests
```

默认 Jar 输出：`target/ship-risk-data-0.0.1-SNAPSHOT.jar`。
