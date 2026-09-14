# simple-iam-server-starter 1.1.1 Changelog

## 发布信息

- 版本：`1.1.1`
- 类型：Patch / 向后兼容能力扩展
- 基线版本：`1.1.0`

## 版本定位

`1.1.1` 在 `1.1.0` 的可配置菜单树和默认入口之上，补齐 Portal 可信应用根节点的显式全局排序，并完成 Server Starter 内部领域包整理。Portal 客户端始终消费服务端顺序，避免因名称或编码变化造成入口顺序漂移。

## 主要变更

### 1. Portal 可信应用根节点排序

- 新增管理端 `GET/PUT /iam/admin/portal/application-order`，读取和更新所有 Portal 集成的完整排序快照；写入携带配置版本，冲突时返回稳定错误码而不覆盖其他管理员的调整。
- 停用的 Portal 集成仍保留排序位置；重新启用后回到原位。当前用户无权访问的应用在 Portal 响应中被裁剪，但剩余应用的相对顺序不变。
- 可信应用创建时分配稳定排序值；排序交换先写入临时负值再回填目标顺序，避免唯一索引的中间态冲突。
- Portal 不再按应用名称或编码二次排序；根节点顺序只以服务端快照为准。

### 2. 领域包与组件命名整理

- entity、repository、service 按 user、authorization、portal、oauth2 等领域归类；内部 IAM 实现类型统一使用 `Iam` 前缀，避免通用名称与宿主应用 Bean 混淆。
- JWT RSA 密钥加载组件调整为 `token.IamJwtKeyProvider`；`support` 仅保留无状态的 `*Helper`，组件扫描、启动期配置校验和失败语义不变。
- 审计监听器、OIDC 登录适配器与 LDAP 登录适配器的集成测试同步使用新的领域 FQCN，保持原有真实基础设施场景和断言。

### 3. 数据库与升级

- 全量 `docs/schema.sql` 包含 `iam_trusted_application_portal.sort_order` 的 `BIGINT` 排序字段、唯一索引与查询索引，完成标识更新为 `1.1.1`。
- 提供从 `1.0.0` 直接升级至 `1.1.1` 的单一脚本，以及从 `1.1.0` 升级至 `1.1.1` 的增量脚本；升级路径、前检、后检和回退原则见 `docs/migration/README.md`。

## 依赖变更

| 依赖 | `1.1.0` | `1.1.1` |
| --- | --- | --- |
| `simple-iam-server-core` | `1.1.1` | `1.1.2` |

Starter 使用精确 Maven 坐标 `io.github.sure-zzzzzz:simple-iam-server-core:1.1.2`；Core 错误码和枚举通过 `api` 保持在 Starter 的对外编译链中。

## 新增或扩展测试

- 覆盖 Portal 根节点排序读取、完整快照校验、并发版本冲突、停用应用保序、权限裁剪保序及唯一索引安全换序。
- 覆盖排序端点的未登录、普通用户和缺失 CSRF 拒绝路径，以及排序写入的管理审计事件与脱敏日志。
- 覆盖领域包迁移后 Server Starter、审计监听器、OIDC 适配器和 LDAP 适配器的编译与原有端到端测试链路。

## 验证结果

在 Spring Boot `2.7.9`、Gradle `8.5`、Java `11` 基线完成完整模块验证：

- `simple-iam-server-starter:test`：62 个测试套件、316 个用例，0 skipped、0 failure、0 error；
- `simple-iam-oidc-adapter-starter:test`：5 个测试套件、27 个用例，0 skipped、0 failure、0 error；
- `simple-iam-ldap-adapter-starter:test`：2 个测试套件、8 个用例，0 skipped、0 failure、0 error；
- `simple-iam-server-audit-listener-starter:test`：4 个测试套件、6 个用例，0 skipped、0 failure、0 error。

## 向后兼容性

- 既有 `menus`、`menuTree`、默认入口和页面展示模式契约保持不变。
- 未调用排序管理端点的实例使用数据库回填的稳定顺序；前端不需要按名称或编码实现兼容排序。
- 领域 FQCN 调整面向 Starter 内部实现；使用 Server Starter 管理 HTTP API 的调用方不受影响。直接编译依赖旧内部类型的扩展需要迁移至对应领域包和 `Iam` 前缀类名。

## 升级指南

1. 新安装执行 `docs/schema.sql`；从 `1.0.0` 升级执行 `docs/migration/V1.0.0__to__V1.1.1__portal_menu_tree_and_application_order.sql`；从 `1.1.0` 升级执行 `docs/migration/V1.1.0__to__V1.1.1__portal_application_order.sql`。
2. 按 `docs/migration/README.md` 完成前检、后检和功能验收；任一升级脚本只能执行一次。
3. 前端读取 Portal 应用列表后直接按服务端返回顺序渲染；管理员调整排序时提交完整应用 ID 快照和读取时的版本号。
