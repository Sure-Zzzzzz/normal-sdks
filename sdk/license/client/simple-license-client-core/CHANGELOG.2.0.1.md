# 2.0.1（2026-10-30）

## 类型

Patch / 契约校验与值对象安全修复

## 修复

- 信任资料响应改为固定八字段 exact-key 校验，未知字段不再被静默接受。
- 吊销分片数量严格限制为 `1..256`，与 License Server OpenAPI 和协议 2 一致。
- `AuthorizationRequest.getFacts()` 对嵌套 Map/List 做递归防御复制，避免构造后的外部修改改变请求语义。

## 兼容性

- 保持 Java 8+、纯契约层和既有公开方法签名不变。
- 传输 starter 必须同步使用 `simple-license-client-core:2.0.1`；本版本不引入 Spring、HTTP 或认证依赖。

## 验证

- `:sdk:license:client:simple-license-client-core:cleanTest :sdk:license:client:simple-license-client-core:test` 全量通过。
- 覆盖未知信任资料字段、空/257 个吊销分片和嵌套 facts 变更隔离。
