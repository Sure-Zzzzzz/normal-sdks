# smart-license-core 2.0.0

## 版本定位

协议 2 的不兼容版本，提供离线凭据、混合租约、结构化条件与在线授权请求共用的纯 Java 8 核心契约。

## 主要变更

- 新增协议 2 的严格 Compact JWS、载荷、吊销包与模型快照编解码。
- 新增 ES256/P-256 验签原语，固定使用 64 字节 JOSE `R||S` 签名格式。
- 新增类型化权益、主体绑定、设备绑定、组织资格、额度、租约和撤销政策模型。
- 新增 `license-condition/1` 的结构化规则模型、发布校验与有界求值器。
- 新增离线验证与在线授权共用的 `DefaultLicenseVerifier`、可信主体、信任资料、状态和吊销 SPI。
- 将 Limiter、IAM、CRM、KMS 与传输能力保持为 Core 外部适配端口，不引入 Spring、数据库或 HTTP 依赖。
- 严格拒绝未知字段、重复键、浮点或超出 JSON 安全整数范围的协议数值，并对输入集合与快照执行防御性复制。

## 不兼容说明

- 2.0.0 不兼容 1.x 的模型、JWS 载荷和旧 Core API；不会自动降级或解析旧协议。
- 使用方需要按 README 迁移到 `core.model`、`core.codec`、`core.verify` 和 `core.spi` 中的 2.0.0 契约。
