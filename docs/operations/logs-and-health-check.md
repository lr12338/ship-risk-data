# 日志与健康检查

进程与端口是存活证据，不等同业务健康。依次检查 PID 文件、Java 进程、8090、30008、启动日志中的 Handler 注册、Profile 数、Scene preflight 和缺失配置错误。若 HTTP health endpoint 未暴露，必须明确记录“不可用”，不能宣称健康通过。

日志不得输出密码、token、Access Key 或完整外部配置。任务证据以 Run Manifest、NDJSON、checksums 和数据库只读统计为主，XXL 日志不是唯一证据。
