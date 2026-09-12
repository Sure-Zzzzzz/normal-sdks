# simple-iam-server-core 1.1.0 Changelog

## 发布信息

- 版本：`1.1.0`
- 类型：Feature / 向后兼容契约扩展
- 基线版本：`1.0.0`

## 版本定位

`1.1.0` 为可信应用 Portal 菜单能力补齐稳定的错误码、错误文案和内置图标编码。Core 只提供跨模块复用的纯契约，不读取菜单配置、不提供 HTTP 接口，也不引入 Web、数据库或其他运行时依赖。

## 主要变更

### 1. Portal 菜单树错误契约

`ErrorCode` 新增三条可信应用菜单错误码，并在 `ServerErrorMessage` 中提供对应的脱敏文案：

| 错误码 | 适用场景 |
| --- | --- |
| `TRUSTED_APPLICATION_014` | Portal 菜单树结构或字段不满足契约。 |
| `TRUSTED_APPLICATION_015` | 应用已使用层级菜单时，旧平铺 `menus` 更新请求发生冲突。 |
| `TRUSTED_APPLICATION_016` | 页面权限仍被 Portal 菜单引用，不能从应用权限清单移除。 |

Server Starter 使用这些稳定码完成菜单树校验、旧字段兼容冲突和权限引用保护；调用方可以据此区分请求修正与权限清单修正。

### 2. 内置目录图标编码

`TrustedApplicationIcon` 新增 `folder`，供 Portal 将目录类菜单与普通页面入口区分展示。图标编码仍由 Core 集中校验，调用方不能提交任意图标标识。

## 依赖变更

未引入或升级生产依赖。Core 继续只依赖 `simple-iam-core`，其余 Spring 能力保持 `compileOnly`，生产源代码保持 Java 8 兼容目标。

## 新增或扩展测试

- 扩展 Core 契约测试，断言 `folder` 为受支持图标、未知图标被拒绝。
- 断言三条菜单错误码的稳定值，以及两条关键错误文案保留调用方需要识别的语义。

## 验证结果

本版本已完成 Core 模块完整测试：

- 4 个测试。
- 0 skipped、0 failures、0 errors。
- 主验证基线：Spring Boot `2.7.9`、Gradle `8.5`、Java `11`。
- 生产源代码保持 Java 8 兼容目标。

## 向后兼容性

- 不移除或修改既有可信应用错误码、错误文案和内置图标编码。
- `folder` 为新增可选图标编码；既有图标校验结果不变。
- Core 不修改 1.0 的平铺 `menus` 请求契约；层级菜单 `menuTree` 的 HTTP 适配和兼容规则由 Server Starter 提供。

## 模块协作

Server Starter 通过精确坐标 `io.github.sure-zzzzzz:simple-iam-server-core:1.1.0` 使用本版本契约。Core 不反向依赖 Starter，也不承担菜单存储、菜单树组装或 HTTP 错误映射。
