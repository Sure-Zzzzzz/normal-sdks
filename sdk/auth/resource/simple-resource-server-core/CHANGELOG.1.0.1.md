# simple-resource-server-core 1.0.1 变更记录

## 新增公共 Provider 凭据契约

- 新增 `BearerResourceCredential`，表达公共 Resource Starter 已完成 `kid` 来源路由后传给 Provider 的短生命周期 Bearer 凭据。
- 该类型只包含来源和待验证凭据，固定 `toString()` 为脱敏文本；禁止将其写入日志、事件、已验证上下文或业务响应。
- 类型不依赖 Spring、Servlet、HTTP Header、JOSE 解析、Provider 注册或 Security Chain，可由 IAM、AKSK 及未来 Provider 共同使用。

## 兼容性

这是向后兼容的维护版本。既有 `ResourceCredential`、Provider SPI、认证结果、已验证主体和请求上下文契约不变。
