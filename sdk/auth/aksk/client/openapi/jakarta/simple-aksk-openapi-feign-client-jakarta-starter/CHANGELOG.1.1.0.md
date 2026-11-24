# 1.1.0

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：1.0.0

## 变更

- 升级 AKSK Feign Jakarta 底座至 1.1.0。
- 新增 `io.github.surezzzzzz.sdk.auth.aksk.openapi.client.aksk-profile`，用于选择管理 OpenAPI 使用的 AKP Profile。
- 空 Profile 或 `default` 继续使用默认拦截器；命名 Profile 只注入一个认证拦截器。

## 兼容性

- 20 端点契约、Feign 异常口径和默认装配行为不变。
- 契约核心保持 `simple-aksk-openapi-client-core:1.0.2`。
