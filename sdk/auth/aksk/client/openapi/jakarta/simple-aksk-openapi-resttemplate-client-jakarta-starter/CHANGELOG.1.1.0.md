# 1.1.0

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：1.0.0

## 变更

- 升级 AKSK RestTemplate Jakarta 底座至 1.1.0。
- 新增 `io.github.surezzzzzz.sdk.auth.aksk.openapi.client.aksk-profile`，用于选择管理 OpenAPI 使用的 AKP Profile。
- 空 Profile 或 `default` 继续使用底座默认模板；命名 Profile 由 `AkskRestTemplateResolver` 解析。

## 兼容性

- 20 端点契约、异常映射、默认装配行为不变。
- 契约核心保持 `simple-aksk-openapi-client-core:1.0.2`。
