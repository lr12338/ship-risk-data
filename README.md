# ship-risk-data

船舶风险行为数据服务，包含双船搭靠、疑似 STS（船对船转运）线索、单船徘徊和区域行为计算。项目使用 Java 8、Spring Boot 和 Maven，历史轨迹来自 HBase 与 AIS Replay，结果写入 MySQL。

## 第一次阅读

建议先读 [STS 阅读与学习指南](docs/sts/README.md)。该文档依次说明事件含义、名单和时间范围、计算流程、源码入口、结果字段及本地测试方法。

| 阅读目的 | 入口 |
| --- | --- |
| 理解交付表里的候选、待核和物理接近 | [事件分类与字段说明](docs/sts/README.md#事件分类与字段) |
| 学习名单到搭靠结果的计算过程 | [计算流程与源码导航](docs/sts/README.md#计算流程与源码导航) |
| 核对当前使用的源码、运行包和验证记录 | [版本与验证记录](docs/sts/PUBLISHED_VERSION.md) |
| 了解批准规则及历史变更依据 | [STS_DEVELOPMENT.md](docs/sts/STS_DEVELOPMENT.md) |
| 在不连接生产环境的情况下学习 | [本地测试与配置](docs/sts/README.md#本地测试与配置) |

## 当前发布范围

本次源码快照对应 2026-11-12 核验的 242 在用 STS 计算版本，原始源码提交为 `d48eec580960a96b258ce05146afc64946c75bc9`，沿用现有 Alongside Core。后续本地初筛、分区及批次事件实验实现不包含在此快照中。

当前商船批次的计划窗口为北京时间 `[2026-08-05 00:00:00, 2026-11-05 00:00:00)`，名单为 21,881 个 IMO、43,200 个 MMSI 别名。两类任务仍在计算，阶段记录不代表全名单全窗口已经完成。名单、AIS 轨迹、数据库结果和交付附件通过授权渠道提供，不随源码公开。

疑似 STS 是待核查线索，不能据此确认实际转运；单船徘徊也不等同于两船 STS。当前 STS 入口为 `shipStsAlongsideHistoryJobHandler`，结果为 `SHADOW_ONLY`，STS 风险评分未启用。

公开配置中的连接信息和凭据使用环境变量占位符。学习版重新打包的 JAR 与服务器既有 JAR 分别记录摘要，不能互相当作同一部署产物。

## 常用入口

- [部署与运维](DEPLOY.md)
- [行为保险规则 V1：测试服部署、规则与 XXL-Job 手册](deploy/behavior-insurance-rules-v1/README.md)
- [搭靠生产测试参数与系统维护](docs/alongside-production-test-operations.md)
- [船舶搭靠模块开发文档](docs/ship-alongside-development.md)
- [船舶搭靠事件 V3 设计与实现](docs/船舶搭靠事件V3设计与实现.md)
- [船舶徘徊模块开发文档](docs/ship-loitering-development.md)

## 本地验证

使用 JDK 8，先确认 `java -version` 和 `mvn -version` 指向同一个 JDK。以下命令运行与当前 STS 链路直接相关的离线单元测试，不启动服务：

```bash
mvn -Dtest='Sts*Test,ShipAlongsideDetectorTest,ShipAlongsideReplayCandidateProviderTest,AlongsideSceneServiceTest,AlongsideComputePolicyTest,LoiteringProductionTestServiceTest,ProductionTestParamsTest,WorldSeaEventGateTest,AlongsideRuleConfigHashIsolationTest,BehaviorEventTransactionStoreTest' test
```

测试通过后，本地打包：

```bash
bash deploy/package.sh
```

JAR 位于 `target/ship-risk-data-0.0.1-SNAPSHOT.jar`，本地交付目录为 `dist/`。`package.sh` 使用 `-DskipTests`，打包成功不能代替前面的测试。外部数据源、区域数据和 XXL-Job 是实际运行所需依赖；启动服务、建表、导入名单或执行任务需另行准备与授权，见 [配置说明](docs/sts/README.md#本地测试与配置)。
