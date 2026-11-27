# CHANGELOG - simple-iam-client-core 1.0.5

## 发布信息

- 版本：`1.0.5`
- 类型：Feature / 契约对齐（目标应用 manifest DATA 声明）
- 基线版本：`1.0.4`

## 变更明细

- `IamPermissionManifest` 新增 `dataResources`。
- 新增 `IamDataResourceDeclaration`，承载 DATA 资源、可授权动作和约束维度。
- 既有方法签名、路径、异常边界、Java 8 字节码和零运行时依赖不变。

## 兼容性

- 旧 Server 响应没有 `dataResources` 时字段为 `null`；消费方不得把缺失当作空申报。
- 需要 DATA 模板前置校验的宿主升级到 Server 返回该数组并使用本版本解析。

## 验证

- Java 11 契约与模型测试 3 项；0 失败、0 错误、0 跳过。
