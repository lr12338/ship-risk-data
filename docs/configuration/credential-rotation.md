# 历史凭据轮换清单

以下键曾在 Git 跟踪配置中出现非占位值，当前工作树已改为环境变量占位。请由凭据所有者确认 Git 历史暴露范围并人工轮换；本轮未轮换、未验证任何真实值：

- `spring.datasource.ais41.password`
- `spring.datasource.ais41Readonly.password`
- `spring.datasource.dataanalysis.password`
- `spring.datasource.newdataanalysis.password`
- `spring.datasource.db3newais41.password`
- `spring.datasource.db3newalertrs.password`
- `spring.datasource.commodity.password`
- `spring.datasource.wgq111.pg.password`
- `xxljob.accessToken`

轮换后只更新环境变量或服务器外置配置，不提交新值。日志、截图和回传报告不得包含秘密。
