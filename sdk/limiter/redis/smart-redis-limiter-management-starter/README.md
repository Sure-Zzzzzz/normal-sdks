# smart-redis-limiter-management-starter

将 SmartRedisLimiter 动态策略管理能力接入专用 Spring Boot management 应用的 Starter。策略持久化在 MySQL，提供管理员页面、管理员 API，以及供 limiter 和第三方系统调用的策略快照与 CRUD API。

它不是可独立执行的应用，不提供应用启动入口、MySQL、Redis、连接池或自动建表；宿主应用负责提供 Web 启动入口、`DataSource` 和事务管理器。

## 运行要求

- management：1.0.0
- core：2.1.0
- Java：8
- Spring Boot：2.7.x
- MySQL：5.7.9+ / 8.0

模块不依赖 limiter starter、Redis 或 redis-route。它直接依赖 `simple-aksk-resource-server-starter`，默认使用 AKSK 保护对外策略 API。

## 添加依赖

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-management-starter:1.0.0'
runtimeOnly 'mysql:mysql-connector-java'
```

若宿主应用已提供 MySQL JDBC 驱动，无需重复声明 `runtimeOnly`。

## 建表

启动应用前执行模块内的建表脚本：

```text
docs/mysql-schema.sql
```

脚本创建策略、策略窗口和服务 revision 三张表。Starter 不执行 DDL，也不创建或配置数据库连接池。

## 最小配置

下面示例同时启用 API 与管理页面。数据库和管理员凭据必须通过环境变量或 secret manager 注入。

```yaml
io.github.surezzzzzz.sdk.limiter.redis.smart.management:
  enable: true
  api:
    enable: true
    base-path: /api
  ui:
    enable: true
    base-path: /admin
  admin:
    username: ${SMART_LIMITER_ADMIN_USERNAME}
    password: ${SMART_LIMITER_ADMIN_PASSWORD}
  page:
    default-size: 20
    max-size: 100

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/limiter_management?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=UTC
    username: ${SMART_LIMITER_DB_USERNAME}
    password: ${SMART_LIMITER_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

配置默认值与约束：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `management.enable` | `false` | 总开关。启用后至少还要开启 API 或 UI。 |
| `management.api.enable` | `false` | 对外策略 API 开关。 |
| `management.ui.enable` | `false` | 管理页面开关；开启时必须同时开启 API。 |
| `management.api.base-path` | `/api` | API 根路径。 |
| `management.ui.base-path` | `/admin` | 管理页面根路径。 |
| `management.page.default-size` | `20` | 默认分页大小。 |
| `management.page.max-size` | `100` | 最大分页大小。 |

`api.base-path` 和 `ui.base-path` 必须以 `/` 开头，不能以 `/` 结尾（根路径除外），不能包含 `*` 或 `?`，且两者不能重叠。开启 UI 时，`admin.username` 和 `admin.password` 均为必填；`admin.password` 配置原始口令，由应用的 `PasswordEncoder` 在内存中编码，不要填写 `{noop}` 或预编码值。

下文的 `management` 均指配置前缀 `io.github.surezzzzzz.sdk.limiter.redis.smart.management`。

## 路径与安全边界

下表以默认根路径为例；修改 `api.base-path` 或 `ui.base-path` 后，所有对应路径随之变化。

| 用途 | 默认路径 | 认证与会话 |
| --- | --- | --- |
| 管理员登录 | `/admin/login` | 配置的管理员账号；表单登录、会话与 CSRF。 |
| 管理页面 | `/admin/policies` | 已登录管理员；登录成功后跳转至此。 |
| 管理员 API | `/api/admin/v1/policy` | 已登录管理员会话；保留 CSRF；匿名请求返回 `401`，不跳转登录页。 |
| 对外策略 API | `/api/v1/policy/**` | 默认 AKSK；仅显式关闭 AKSK 时使用固定 header token。 |

UI 安全链只覆盖 `<ui-base-path>/**`，管理员 API 安全链只覆盖 `<api-base-path>/admin/**`。对外策略 API 不使用 UI 会话，也不使用 UI 的 CSRF 模式。

## 对外策略 API 认证

### 默认模式：AKSK resource-server

resource-server 默认开启并接管 `<api-base-path>/v1/policy/**`。接入方需要完成 AKSK introspection 配置，并确保 resource-server 的 `protected-paths` 覆盖该路径；默认 API 根路径为 `/api` 时，默认 `/api/**` 可以覆盖。若改为自定义 `api.base-path`，必须同步配置匹配的 `protected-paths`。

权限要求：

| 接口 | 所需 scope |
| --- | --- |
| `GET <api-base-path>/v1/policy/snapshot?serviceCode=...` | `smart-redis-limiter:policy:read` |
| 对外 CRUD，包括 `GET` 详情和分页查询 | `smart-redis-limiter:policy:write` |

AKSK 主体会作为 operator 记录到策略管理事件。

### 临时模式：固定 header token

仅在临时、受控环境中显式关闭 resource-server 后，Starter 才启用固定 token：

```yaml
io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled: false

io.github.surezzzzzz.sdk.limiter.redis.smart.management:
  rest:
    policy-token: ${SMART_LIMITER_POLICY_TOKEN}
```

API 已启用而 token 缺失或空白时，应用以 `CONFIG_002` 启动失败。调用方使用唯一的非空 header：

```http
X-Smart-Redis-Limiter-Policy-Token: <shared-secret>
```

缺失、空白、重复或错误 token 均返回 `401`。成功认证的请求无状态、不创建会话，且仅能访问 `<api-base-path>/v1/policy/**`；它不认证 UI 和管理员 API，也不替代 UI 的会话与 CSRF。

固定 token 是临时共享密钥：持有者可访问快照与全部 CRUD，不区分 read/write 权限；它没有调用方身份、调用方级授权或重叠轮换能力。生产环境应优先使用 AKSK；如确需固定 token，必须通过 TLS 与私网或受控网关传输，并通过环境变量或 secret manager 注入。不得将 token 放入源码、URL、user-info、查询参数、事件或普通访问日志。

## 管理策略

一条策略的唯一身份为：

```text
serviceCode + resourceCode + subject
```

策略包含 `enabled` 状态和完整的 `limits` 列表；一个策略可以有多个限额窗口。它不保存 HTTP path、method、算法、fallback、key strategy、Redis route 或 datasource。

对外 API 与管理员 API 都支持创建、分页查询、详情查询、整体更新、启停和删除：

| 操作 | 路径（对外 API） | 并发约束 |
| --- | --- | --- |
| 创建 | `POST <api-base-path>/v1/policy` | - |
| 查询 | `GET <api-base-path>/v1/policy` | - |
| 详情 | `GET <api-base-path>/v1/policy/{id}` | - |
| 更新全部窗口 | `PUT <api-base-path>/v1/policy/{id}` | `expectedRowVersion` |
| 更新启停状态 | `PATCH <api-base-path>/v1/policy/{id}` | `expectedRowVersion` |
| 删除 | `DELETE <api-base-path>/v1/policy/{id}?expectedRowVersion=...` | `expectedRowVersion` |

`PUT` 总是整体替换 `limits`，不做窗口级合并。并发修改使用 `rowVersion` 控制；身份冲突或过期版本返回 `409`。

一个多窗口策略的创建请求示例：

```json
{
  "key": {
    "serviceCode": "demo-service",
    "resourceCode": "demo-resource",
    "subject": "anonymous"
  },
  "limits": [
    {"count": 10, "window": 1, "unit": "SECONDS"},
    {"count": 100, "window": 1, "unit": "MINUTES"}
  ],
  "enabled": true
}
```

API 仅以 HTTP status 表达机器可读结果：参数或协议校验失败为 `400`，不存在为 `404`，身份或版本冲突为 `409`，服务端异常为 `5xx`。错误 body 仅包含 `message` 和 `timestamp`，不包含业务 code。

## 获取服务策略快照

```http
GET <api-base-path>/v1/policy/snapshot?serviceCode=<service-code>
If-None-Match: "<etag>"
```

快照仅包含该服务所有已启用的策略及其完整窗口列表。

- 有更新时返回 `200`、完整 `SmartRedisLimiterPolicySnapshot`、`ETag` 与 `Cache-Control: no-cache`。
- `If-None-Match` 匹配时返回 `304`，不含 body。
- 未知服务返回 revision 为 `0` 的有效空快照。
- 停用或删除某服务最后一个启用策略后，返回更高 revision 的有效空快照。

limiter 默认策略客户端以自身 `smart.me` 作为快照请求的 `serviceCode`，不需要重复配置服务编码。

## revision、事件与扩展

每个服务有独立 revision。有效的创建、更新、启用、停用和删除各使对应服务 revision 增加一次；校验失败、冲突、no-op 或事务回滚不会增加 revision。

策略变更成功提交后发布 `SmartRedisLimiterManagementEvent`。普通 Spring Event 为 best-effort：监听器异常不会回滚已提交策略，也不会让保存请求变为失败。需要可靠审计投递时，应单独设计事务 Outbox。

以下默认实现都可通过自定义 Bean 替换：

- `SmartRedisLimiterPolicyRepository`
- `SmartRedisLimiterPolicyManagementService`
- `SmartRedisLimiterPolicySnapshotService`
- `SmartRedisLimiterManagementOperatorProvider`
- `SmartRedisLimiterManagementEventPublisher`

默认实现使用 `@ConditionalOnMissingBean` 注册。

## 不提供的能力

- 可执行应用、容器镜像或自动数据库迁移；
- Redis 连接、Redis route 或 limiter 侧轮询配置；
- 策略继承、套餐/等级、审批流、定时生效或 HTTP 路由定义；
- 窗口级部分合并更新；
- 固定 token 模式下的调用方身份、细粒度授权或平滑轮换。
