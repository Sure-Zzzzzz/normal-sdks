# 1.1.3

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：`1.1.2`

## 变更

- `IamPermissionManifestResponse` 新增 `dataResources`，并新增 DATA 声明 wire 模型。

## 兼容性

- Profile 与 1.1.2 的 401 清理/一次重放语义不变；49 个契约方法不变。
- IAM Server 旧响应缺字段时保持 `null`；依赖 `simple-iam-client-core:1.0.5`。

## 验证

- Java 17 全量测试 17 项；0 失败、0 错误、0 跳过。覆盖真实 Feign 契约、Profile 自愈和 manifest wire。
