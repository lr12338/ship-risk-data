# 部署与回滚

本地构建：`mvn clean test`、`mvn clean package`，随后执行 Behavior Platform 打包脚本并校验归档及内部 checksums。上传、备份、替换、启停均属于变更操作，未经授权不得执行。

批准后的人工流程：比较服务器 JAR SHA → 备份当前 JAR/配置 → 上传到临时目录 → 校验 SHA → 原子替换 JAR → 保持外置配置 → stop/start → 核对 PID、8090、30008 和启动日志 → 只执行 Preflight。

回滚：停止新进程 → 恢复备份 JAR 与配置 → 启动 → 核对 PID/端口/日志。数据库回滚只能在确认没有保留数据需求后人工执行 `U20260805__behavior_platform_v1.sql`。
