# simple-iam-server-core 1.1.1 Changelog

## 发布信息

- 版本：`1.1.1`
- 类型：Patch / 向后兼容契约扩展
- 基线版本：`1.1.0`

## 版本定位

`1.1.1` 为 IAM Server 1.1.0 的 Portal 默认入口与全局登录首页能力补齐稳定错误契约。Core 仍只提供跨模块复用的常量和脱敏错误文案，不读取数据库、不提供 HTTP 接口、不引入运行时依赖。

## 主要变更

`ErrorCode` 与 `ServerErrorMessage` 新增：

| 错误码 | 适用场景 |
| --- | --- |
| `TRUSTED_APPLICATION_017` | 应用默认入口不是本应用 PAGE、入口路径越出 PAGE 路由或包含危险路径内容。 |
| `TRUSTED_APPLICATION_018` | Portal 配置版本不匹配，拒绝覆盖其他管理员的菜单或默认入口更新。 |
| `TRUSTED_APPLICATION_019` | Portal 全局登录首页引用的应用不可用、未启用 Portal 或不存在可用默认入口。 |

## 兼容性

- 未修改或移除任何既有错误码、错误文案、事件、常量或异常类型。
- Starter 可按其自身发布节奏升级到本 Core 坐标；Core 不反向依赖 Starter。
- 生产源代码继续保持 Java 8 兼容，Spring 依赖仍为 `compileOnly`。
