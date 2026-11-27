# smart-license-core 2.1.0

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：`2.0.0`

## 变更

- `RoleProjectionPort.Manifest` 新增 `dataResources`，声明应用可授权的 DATA 资源、动作与约束维度。
- 保留 `2.0.0` 的 PAGE/API 清单构造器；未携带 DATA 申报时由消费实现按失败关闭处理，Core 不猜测默认资源。

## 兼容性

- Java 8、纯契约、无 Spring/HTTP 运行时依赖边界不变。
- 已实现 `Manifest` 的宿主如直接重写全部字段，需要同步消费新 `dataResources`；仅调用 PAGE/API 构造器的既有代码继续编译。

## 验证

- Java 8 全量测试 105 项；0 失败、0 错误、0 跳过。
