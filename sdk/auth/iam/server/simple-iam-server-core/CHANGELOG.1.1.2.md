# simple-iam-server-core 1.1.2 Changelog

## 发布信息

- 版本：`1.1.2`
- 类型：Patch / 向后兼容契约扩展
- 基线版本：`1.1.1`

## 版本定位

`1.1.2` 为 IAM Server Portal 应用根节点全局排序补齐稳定错误契约，并将菜单节点类型和页面展示模式下沉为跨模块共享枚举。Core 仍只提供常量、枚举和脱敏错误文案，不读取数据库、不提供 HTTP 接口、不引入运行时依赖。

## 主要变更

### 1. Portal 应用根节点排序错误契约

`ErrorCode` 与 `ServerErrorMessage` 新增：

| 错误码 | 适用场景 |
| --- | --- |
| `TRUSTED_APPLICATION_020` | Portal 应用根节点排序请求未提交完整快照、包含重复项，或混入当前不存在的 Portal 集成。 |
| `TRUSTED_APPLICATION_021` | Portal 应用根节点排序版本过期，拒绝覆盖其他管理员已保存的排序。 |

### 2. Portal 菜单枚举

- `PortalMenuNodeType`：`GROUP` 表示目录分组，`PAGE` 表示可路由页面；
- `PortalPresentationMode`：`STANDARD` 使用标准 Portal 壳，`IMMERSIVE` 移除 Portal 顶栏与侧栏，但不改变会话、路由或页面权限边界。

枚举 FQCN 保持 `io.github.surezzzzzz.sdk.auth.iam.server.constant` 不变；Server Starter 通过 `api` 依赖传递提供，调用方无需额外引入其他模块。

## 依赖变更

未引入或升级生产依赖。Core 继续仅依赖 `simple-iam-core`，其余 Spring 能力保持 `compileOnly`，生产源代码保持 Java 8 兼容目标。

## 新增或扩展测试

- 扩展 Core 契约测试，锁定 `TRUSTED_APPLICATION_020`、`TRUSTED_APPLICATION_021` 的稳定值及可识别错误文案；
- 断言 Portal 菜单节点类型与展示模式的完整枚举集合。

## 向后兼容性

- 不移除或修改既有错误码、错误文案、事件、常量、枚举值或异常类型；
- 两条错误码和两个枚举均为新增契约；
- Core 不承担应用排序存储、并发控制或 HTTP 错误映射，这些行为由 Server Starter 提供。
