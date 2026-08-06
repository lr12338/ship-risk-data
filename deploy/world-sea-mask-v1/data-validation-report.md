# world_sea.sql 数据核验报告

- SHA-256：`de7d39f8e652721796d64ce8697bb036da30280500940de8ece70ece4914266b`
- 要素：102（ID 唯一、Geometry 非空）
- 类型：2 Polygon、100 MultiPolygon
- 顶点：892,538
- 坐标范围：经度 `[-180.0, 180.0]`，纬度 `[-85.47029103625539, 90.0]`
- 数据库几何指纹：`81c810a9db7d319fdfbf9a17805a61493d3beda23b09154958ce2a2b3a31fd1e`
- 内部 SRID：全部为 101；仅记录，不解释为已确认的权威坐标系
- 坐标假设：`LON_LAT_WGS84_LIKE_UNVERIFIED`

受控导入在 stage 校验时失败关闭。以下 7 个源 WKB 与数据库 stage WKB 指纹一致，证明不是转换损坏；JTS 报告拓扑非法：

- ID 11：Ring Self-intersection，WKB SHA-256 `0d249572b01e30faeefa3390be899ce4162639bfcf7b96d15c8004b379705d56`
- ID 26：Ring Self-intersection，WKB SHA-256 `b30be6f20ed6c412b4977b9c47258bf901a3583fdf64a851fecb42c2b402d1bd`
- ID 85：Ring Self-intersection，WKB SHA-256 `dfc42f13e61a4f48ec56f9e5b7e646e72a7e2df4b02c5670f74422f09afb2085`
- ID 91：Ring Self-intersection，WKB SHA-256 `b036425210ae610006fa8ae91f7ab3100b7a2c818ffbc15686e5767e5dea280e`
- ID 92：Ring Self-intersection，WKB SHA-256 `e443c24fb4432332c7b25cc147ef8f61bb35e69d7a5b32b8641c221a0dc9e729`
- ID 99：Ring Self-intersection，WKB SHA-256 `107293278efa782941e93169e177edeeb484d6bf4901421d29c632a8ef1b4e21`
- ID 24556：Self-intersection，WKB SHA-256 `721a73e1b68f4685e471f80614a9013e5b0fff5fe777b13a42589841d8ed636a`

服务器状态复核：stage 存在且为 102 条，`max_allowed_packet=1073741824`，正式表、元数据表和审计表均不存在。未使用 `geometry.buffer(0)` 或数据库修复函数。正式表、ACTIVE 元数据和审计表均不得在这批源数据获批准修复或替换前发布。
