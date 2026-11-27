# 1.1.3

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：`1.1.2`

## 变更

- `IamPermissionManifestResponse` 新增 `dataResources`。
- 新增 `IamDataResourceDeclarationResponse`，承载 DATA 资源、动作和约束维度；字段名逐字对位 IAM Server。

## 兼容性

- 49 个契约方法、路径、If-Match、Profile 1.1.2 的 401 清理重放语义不变。
- IAM Server 旧响应缺字段时保持 `null`，消费方不得当作空申报。

## 验证

- Java 11 全量测试 17 项；0 失败、0 错误、0 跳过。覆盖真实 Feign 契约、Profile 顺序、401 自愈和 manifest wire。
