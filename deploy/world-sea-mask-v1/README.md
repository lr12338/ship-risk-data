# WORLD_SEA_MASK V1R1 发布说明

当前授权：`OPERATOR_AUTHORIZED`。项目所有者已通过可审计的 `OPERATOR_OVERRIDE` 允许基于现有严格自动校验结果发布；该状态不伪装为人工 `APPROVED`。

数据语义仅为全球命名海洋水域掩膜 `WORLD_SEA_NAMED_AREA`，不是法律公海、EEZ/领海/主权、合规或风险分类。

## 固定数据身份

- 原始 stage：`dataanalysis.ship_behavior_world_sea_stage_20260806_155556`
- repaired stage：`dataanalysis.ship_behavior_world_sea_stage_20260806_180406`
- 原始文件 SHA-256：`de7d39f8e652721796d64ce8697bb036da30280500940de8ece70ece4914266b`
- 原始 stage 指纹：`81c810a9db7d319fdfbf9a17805a61493d3beda23b09154958ce2a2b3a31fd1e`
- repaired 指纹：`31dd89bd4968f8f436135b57cdb81e870ac7b827d4dc2c086282e7dfe91ab9c6`
- 修复方法：`JTS_GEOMETRY_FIXER_1_18_2`
- 变化 ID：`11,26,85,91,92,99,24556`；其余 95 条 WKB 原样不变

旧 JAR SHA-256 `c0e90d35da9de3c71ef4fd996364de8e6bb72b0293dffe2d9208843350b37002` 状态为 `SUPERSEDED_NOT_FOR_WORLD_SEA_102_V1R1`。

## 授权后数据库发布

安装项目所有者显式授权文件：

```text
/data/zhengyi/import/world-sea-mask-v1r1/release-authorization.json
```

文件必须为 `OPERATOR_OVERRIDE`，授权引用与时间有效，7 个 ID 完整，且指纹严格匹配 repaired stage。原 `repair-approval.json` 人工审核路径仍兼容。随后串行执行，每一步后运行只读 `STATE`：

```bash
stage=ship_behavior_world_sea_stage_20260806_180406
jar=/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.uploading

java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool -cp "$jar" \
  org.springframework.boot.loader.PropertiesLauncher REGISTER_STAGE "$stage"
java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool -cp "$jar" \
  org.springframework.boot.loader.PropertiesLauncher STATE "$stage"
java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool -cp "$jar" \
  org.springframework.boot.loader.PropertiesLauncher PUBLISH_STAGE "$stage"
java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool -cp "$jar" \
  org.springframework.boot.loader.PropertiesLauncher STATE "$stage"
java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool -cp "$jar" \
  org.springframework.boot.loader.PropertiesLauncher ACTIVATE_DATASET "$stage"
java -Dloader.main=com.hifleet.worldsea.WorldSeaImportTool -cp "$jar" \
  org.springframework.boot.loader.PropertiesLauncher STATE "$stage"
```

中断时不得手工重做 RENAME；先 `STATE`，再 `RESUME_RELEASE`。

## 人工部署（数据库 ACTIVE 后）

```bash
scp dist/world-sea-mask-v1r1/ship-risk-data-0.0.1-SNAPSHOT.jar \
  242:/data/zhengyi/ship-risk-data-0.0.1-SNAPSHOT.jar.uploading

ssh 242
cd /data/zhengyi
sha256sum ship-risk-data-0.0.1-SNAPSHOT.jar.uploading
release_stamp=$(date +%Y%m%d-%H%M%S)
mkdir -p "backups/$release_stamp"
cp ship-risk-data-0.0.1-SNAPSHOT.jar application.properties \
   start.sh stop.sh status.sh restart.sh "backups/$release_stamp/"
```

只合并 `application.properties.delta`，设置最终远端 SHA；不得覆盖整份服务器配置。人工替换和重启后，先运行 `shipBehaviorWorldSeaMaskPreflightJobHandler`，PASS 后才可串行执行两个 20 船 dry-run。
