# Behavior Service Runbook

顺序固定为：本地测试 → 打包校验 → 服务器只读核验 → 外部配置准备 → 部署审批 → Profile Preflight → 10 → 100 → 500 → 5,000。

只读命令：`sha256sum /data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar`、`cat /data/zhengyi/ship-risk-data.pid`、`pgrep -af ship-risk-data`、`ss -lntp | grep -E ':(8090|30008)'`、读取 Run Manifest/checksums。

会修改状态的命令：上传/替换 JAR、编辑配置、执行迁移、stop/start/restart、触发任何 XXL Handler、删除 batch。必须单独授权。本轮未执行这些命令。

仓库配置仅为占位和安全默认值。外部配置优先，密码和 token 通过环境变量或服务器外置配置注入，不要覆盖服务器已有配置。
