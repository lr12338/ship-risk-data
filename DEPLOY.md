# ship-risk-data 部署文档

部署目录：**`/data/zhengyi`**  
无需解压，jar 与脚本放同一目录，直接 `./start.sh` 启动。

**配置策略：** 线上以 `/data/zhengyi/application.properties` 为准（外置配置）。  
jar 内 `application-prod.properties` 为空占位，不承载数据源等生产配置。  
`start.sh` 启动时必须存在同目录 `application.properties`，否则拒绝启动。

---

## 1. 服务器目录结构

```
/data/zhengyi/
├── ship-risk-data-0.0.1-SNAPSHOT.jar   # 应用包
├── application.properties              # 线上外置配置（覆盖 jar 内默认值）
├── start.sh                            # 启动
├── stop.sh                             # 停止
├── restart.sh                          # 重启
├── status.sh                           # 状态
├── ship-risk-data.pid                  # 运行后自动生成
└── logs/                               # 运行后自动生成
    ├── startup.log                     # 启动日志
    ├── log.log                         # 应用主日志
    ├── error.log                       # 错误日志
    ├── archive/                        # 滚动归档
    └── job/jobhandler/                 # XXL-Job 任务日志
```

---

## 2. 本地打包

```bash
chmod +x deploy/package.sh
./deploy/package.sh
```

产物在 `dist/` 目录（扁平结构，**不是压缩包**）：

```
dist/
├── ship-risk-data-0.0.1-SNAPSHOT.jar
├── application.properties   # 首次部署模板，仓库内 deploy/application.properties
├── start.sh
├── stop.sh
├── restart.sh
├── status.sh
└── logs/
```

或手动打包：

```bash
mvn clean package -DskipTests
# jar 在 target/ship-risk-data-0.0.1-SNAPSHOT.jar
# 脚本在 deploy/ 目录
```

---

## 3. 上传到服务器（无需解压）

```bash
# 创建目录
ssh user@39.96.38.242 "mkdir -p /data/zhengyi/logs/job/jobhandler /data/zhengyi/logs/archive"

# 首次部署：上传 jar、脚本、application.properties 模板
scp dist/ship-risk-data-0.0.1-SNAPSHOT.jar dist/*.sh dist/application.properties user@39.96.38.242:/data/zhengyi/

# 日常更新：只上传 jar 和脚本，不要覆盖服务器上已维护的 application.properties
# scp dist/ship-risk-data-0.0.1-SNAPSHOT.jar dist/*.sh user@39.96.38.242:/data/zhengyi/
```

---

## 4. 启动 / 停止

```bash
ssh user@39.96.38.242
cd /data/zhengyi
chmod +x *.sh

# 启动（默认 prod 环境）
./start.sh

# 停止
./stop.sh

# 重启
./restart.sh

# 查看状态
./status.sh
```

启动成功：`./start.sh` 无输出；用 `ps -ef | grep ship-risk-data` 或 `./status.sh` 确认进程。

修改配置：只改服务器 `/data/zhengyi/application.properties`，然后 `./stop.sh && ./start.sh`，无需重新打包。

---

## 5. 日志说明

| 文件 | 说明 |
|------|------|
| `logs/startup.log` | 启动脚本输出 |
| `logs/log.log` | 应用主日志（INFO） |
| `logs/error.log` | 仅 ERROR 级别 |
| `logs/archive/*.log.gz` | 自动滚动归档（30 天） |
| `logs/job/jobhandler/` | XXL-Job 任务日志 |

查看日志：

```bash
tail -f /data/zhengyi/logs/log.log
tail -f /data/zhengyi/logs/error.log
tail -f /data/zhengyi/logs/startup.log
```

---

## 6. 服务配置

| 项目 | 值 |
|------|-----|
| 部署目录 | `/data/zhengyi` |
| 公网 IP | 39.96.38.242 |
| 内网 IP | 172.17.137.213 |
| HTTP 端口 | 8090 |
| XXL-Job 端口 | 30008 |
| XXL-Job AppName | ship-risk-data |
| JobHandler | zoneCrossingStatsJobHandler |
| JDK | Java 8 |

安全组放行：**30008**（来源 47.93.16.141，XXL-Job Admin）

---

## 7. 更新版本

```bash
cd /data/zhengyi
./stop.sh

# 本地上传新 jar 覆盖
# scp dist/ship-risk-data-0.0.1-SNAPSHOT.jar user@39.96.38.242:/data/zhengyi/

./start.sh
```

脚本无需重新上传（除非有改动）。

---

## 8. 快速命令速查

```bash
# 本地
./deploy/package.sh
scp dist/ship-risk-data-0.0.1-SNAPSHOT.jar dist/*.sh dist/application.properties user@39.96.38.242:/data/zhengyi/

# 服务器
cd /data/zhengyi && chmod +x *.sh
./start.sh
./stop.sh
./status.sh
tail -f logs/log.log
```
