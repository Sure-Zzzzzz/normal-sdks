# CHANGELOG 2.0.0

类型：Major Dependency Alignment

## 变更内容

- 与 `prometheus-client-starter:2.0.0` 的 Route 原生化重构同步发布，形成完整的 2.0 依赖链。
- `Remote`、`Types` 等 Protobuf 生成协议对象和其序列化语义保持不变。
- 不新增 HTTP、认证、路由、连接池、重试或 metrics 相关能力。

## 1.x 封版

`1.0.0` 是 1.x 的最终版本，冻结使用说明见 [README.1.x.md](README.1.x.md)。

## 向后兼容性

协议 API 未变。使用者升级时将 Maven/Gradle 坐标升级至 `2.0.0`，与 `prometheus-client-starter:2.0.0` 保持同一版本线即可。
