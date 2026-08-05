# 船型策略

主船默认 `MERCHANT_STRICT`，未知或非商船 fail-closed。目标船支持 `STRICT_SAME_AS_PRIMARY`、`COMMERCIAL_EXTENDED`、`ALL_KNOWN_SHIPS`、`CUSTOM_ALLOWLIST`。

未知目标船动作：`REJECT`、`ALLOW_LOW_CONFIDENCE_SHADOW_ONLY`、`REVIEW_REQUIRED`。未知类型默认不具备正式写入资格。输出包含标准船型、来源、置信度、角色、风险上下文、置信调整、正常作业可能性和复核状态。
