# 1.1.1

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：`1.1.0`

## 变更

- `IamOpenRoleRestTemplateClient` 解析目标应用 manifest 的 `dataResources`，映射到 `IamDataResourceDeclaration`。
- 缺失字段保持 `null`；数组元素按原样承载，不猜测资源、动作或维度。

## 兼容性

- AKP Profile、认证头、连接池和错误透传行为不变。
- 依赖 `simple-iam-client-core:1.0.5`；不读取 DATA 声明的调用方无需改代码。

## 验证

- Java 11 全量测试 13 项；0 失败、0 错误、0 跳过。覆盖 manifest DATA 声明、缺失申报保持 `null`、契约与装配链路。
