# smart-redis-limiter-management-starter

将限流策略持久化到 MySQL，提供策略查询、创建、窗口整体替换、启停、删除和服务级快照。支持两类规则形态：三元组精确规则（`/v1/policy/**`）与多维度类型化规则（`/v2/policy/**`，维度为资源/IP/人员/服务主体/凭据/客户/自定义，含默认额度与精确对象覆盖、目录声明与类型化快照）。运行端通过 HTTP 拉取快照，不需要把管理模块装入业务应用，也不需要共享数据库。

| Management | Core | 管理宿主 | Java | Portal Web |
| --- | --- | --- | --- | --- |
| 2.1.0 | 2.3.0 | Spring Boot 2.7.9 | 8 | 1.0.0 |
| 2.0.0 | 2.2.0 | Spring Boot 2.7.9 | 8 | 1.0.0 |
| 1.0.0 | 2.1.0 | Spring Boot 2.7.9 | 8 | 内嵌 Console |

## 选择形态

`console` 保留内嵌页面、本地管理员会话及旧 scope API。默认仍为 console，仅关闭 UI 不会切成 Portal。

`portal` 由独立 Web 接入统一应用门户。关闭内嵌页面、管理员 Session API 和固定 token 兜底；人员及机器均使用 Bearer（HTTP 认证令牌）调用同一业务 API，完整执行应用准入、API 操作权限与 DATA 数据范围。PAGE 页面权限只控制门户页面，不作为机器 API 的额外门槛，包括主体类型为 HUMAN 的所属人继承凭据。

本模块不提供可执行应用、连接池或自动建表，不依赖限流运行端、Redis、IAM 或 AKSK 的实现。宿主提供 Spring Web、Security、JDBC、数据源与事务管理器；需要 Console 页面时提供 Thymeleaf。资源认证组件由宿主显式组合。

## 添加依赖

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-management-starter:2.1.0'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-jdbc'
runtimeOnly 'mysql:mysql-connector-java'
```

Portal 双来源宿主另外引入：

```gradle
implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.1.1'
implementation 'io.github.sure-zzzzzz:simple-iam-resource-server-starter:1.0.0'
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:3.1.0'
implementation 'io.github.sure-zzzzzz:simple-data-permission-spring-mvc-starter:1.0.1'
```

Provider（身份来源适配器）的验证客户端、目标应用和密钥按各自接入契约配置，不能把浏览器的公共 PKCE 客户端当成资源验证客户端。两来源必须在公共 Resource 中分别注册；本模块不识别来源品牌、不访问身份库。只引依赖不等于完成授权配置。

## Portal 最小配置

```yaml
io.github.surezzzzzz.sdk.limiter.redis.smart.management:
  enable: true
  mode: portal
  api:
    enable: true
    base-path: /api
  ui:
    enable: false
  page:
    default-size: 20
    max-size: 100

io.github.surezzzzzz.sdk.auth.resource.server:
  enabled: true
  security:
    protected-paths:
      - /api/v1/policy/**
      - /api/v2/policy/**
```

类型化目录随宿主部署声明（协议模式、资源与维度、命名空间、自定义类型、对象目录与策略代次）：

```yaml
io.github.surezzzzzz.sdk.limiter.redis.smart.management:
  typed:
    services:
      - service-code: order-service          # TYPED_V2 服务：使用类型化规则与快照
        display-name: 订单服务
        resources:
          create-order: [RESOURCE, IP, USER, CUSTOMER]
        namespaces: { RESOURCE: shared, IP: entry, USER: user, CUSTOMER: customer }
        objects:
          - { dimension: CUSTOMER, id: cust-0001, name: 示例客户 }
      - service-code: legacy-batch            # LEGACY_V1 服务：保留三元组规则与旧快照
        control-mode: LEGACY_V1
```

所有上述业务路径必须由公共 Resource 安全链保护，并启用精确 API 权限的 MVC 校验。该资源链的 `permit-all-paths` 必须为空，公开入口由宿主另配独立安全链。缺认证链、MVC 校验、路径覆盖或双来源适配器时启动失败。启动门禁仅检查本地装配，远端准入与授权在每次请求中验证。

独立前端仓为 `smart-redis-limiter-management-web`。人员可信应用注册、门户菜单与三权模板统一在该仓 `docs/TRUSTED_APPLICATION_ONBOARDING.md` 维护。前端网关可将 `/api/limiter/**` 映射到宿主 `/api/**`；不要让 API 落入 SPA 页面兜底。浏览器请求必须不带 Cookie，403 不重新授权。

## 权限与 HTTP API

Portal 权限码与身份系统中申报、授予的码必须完全一致：

| 类别 | 码值 | 用途 |
| --- | --- | --- |
| PAGE | `smartLimiterPolicy:page` | 策略列表、新建、详情页面 |
| API | `smartLimiterPolicy:read` | 分页、详情、能力查询 |
| API | `smartLimiterPolicy:write` | 创建、整体更新、启停、删除 |
| API | `smartLimiterPolicySnapshot:read` | 运行端快照 |
| DATA | `limiter-policy`，动作 `read` / `write` | 服务数据范围；维度 `serviceCode`，操作符 `IN` |

写权限不自动授予读权限。列表与 count 用同一 DATA 谓词，详情及写入按主键和范围联合定位；创建校验目标服务，更新采用记录的持久化服务编码。完整授权项内部条件按 AND、不同授权项按 OR；未知维度、不能表达的约束、缺计划失败关闭。范围外的记录返回 404，不泄露是否存在；目标服务快照超范围返回 403。

以下路径以默认 `/api` 为根；完整调用契约见 `docs/openapi.yaml`。

| 方法与路径 | 成功响应 |
| --- | --- |
| GET `/v1/policy` | 200，`items/page/size/totalElements/totalPages` |
| GET `/v1/policy/{id}` | 200，完整策略 |
| GET `/v1/policy/capabilities?serviceCode=...` | 200，`pageAllowed/canWrite`，禁止缓存 |
| POST `/v1/policy` | 201，变更结果及 Location |
| PUT `/v1/policy/{id}` | 200，窗口整体替换结果 |
| PATCH `/v1/policy/{id}` | 200，启停变更结果 |
| DELETE `/v1/policy/{id}?expectedRowVersion=...` | 200，删除结果及服务 revision |
| GET `/v1/policy/snapshot?serviceCode=...` | 200 完整快照或带 ETag 的 304 |
| GET `/v2/policy/services` `/v2/policy/services/{code}/declarations` `/v2/policy/services/{code}/objects` | 授权目录内类型化服务、资源/维度声明、对象目录（DATA 过滤，no-store） |
| GET/POST/PUT/PATCH/DELETE `/v2/policy/rule(s)` | 类型化规则 CRUD（命名空间以目录声明为准；身份七字段唯一；冲突 409） |
| GET `/v2/policy/snapshot?serviceCode=...` | 200 类型化快照（schemaVersion=2，policyEpoch+revision）或 304；LEGACY 服务 409 |

能力接口仅对已准入且有查询 API 权限的 HUMAN 返回展示结论，SERVICE 返回 403。无 PAGE 时两个字段都为 false。无服务参数时 canWrite 表示至少可写一个合法服务；带参数时针对该服务判断，非法编码 400。它不能替代业务请求自身的授权。

创建示例：

```json
{
  "key": {"serviceCode": "mock-service", "resourceCode": "mock-resource", "subject": "*"},
  "enabled": true,
  "limits": [{"count": 100, "window": 1, "unit": "SECONDS"}]
}
```

类型化规则创建示例（命名空间由目录声明对齐，无需客户端传值）：

```json
{
  "serviceCode": "order-service",
  "resourceCode": "create-order",
  "dimension": "CUSTOMER",
  "selector": "EXACT",
  "objectId": "cust-0001",
  "enabled": true,
  "limits": [{"count": 100, "window": 1, "unit": "MINUTES"}]
}
```

规则身份七字段不可原地改写；维度取值 RESOURCE/IP/USER/SERVICE/CREDENTIAL/CUSTOMER/CUSTOM，选择器 DEFAULT（各对象默认额度，不填 objectId）或 EXACT（精确对象覆盖），CUSTOM 维度须携带目录声明的 customType。对象筛选按字面前缀匹配，通配符 `%` 与 `_` 不生效。

策略身份三元组不可更新。PUT 必须携带 `expectedRowVersion` 和完整 `limits`；PATCH 携带行版本和 enabled；DELETE 携带行版本。成功变更递增行版本和服务 revision，no-op 不递增。并发冲突为 409，调用方重新读取后再决定是否重试，不覆盖旧编辑内容。

未认证 401、未授权 403、格式错误 400、记录不存在 404、冲突 409、媒体类型不支持 415。错误不伪装为 200，不返回异常链、SQL 或授权集合。快照在判断 ETag（条件缓存标识）与 304 前执行所有授权检查。

## Console 接入

```gradle
implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
// 使用旧 scope 认证时由宿主显式提供旧 Provider：
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:2.0.1'
implementation 'org.springframework.boot:spring-boot-starter-aop'
implementation 'org.springframework.security:spring-security-oauth2-resource-server'
implementation 'org.springframework.security:spring-security-oauth2-jose'
```

```yaml
io.github.surezzzzzz.sdk.limiter.redis.smart.management:
  enable: true
  mode: console
  api: { enable: true, base-path: /api }
  ui: { enable: true, base-path: /admin }
  admin:
    username: ${LIMITER_ADMIN_USERNAME}
    password: ${LIMITER_ADMIN_PASSWORD}
```

Console Session 管理 API 位于 `/api/admin/**`，保留 CSRF（跨站请求伪造）保护。旧对外 API 位于 `/api/v1/policy/**`，快照要求 `smart-redis-limiter:policy:read`，CRUD 沿用 `smart-redis-limiter:policy:write`。显式关闭旧 AKSK Resource 时，必须配置独立 `rest.policy-token`，通过 `X-Smart-Redis-Limiter-Policy-Token` 请求头使用。该兜底仅存在于 Console，不进入 Portal。

## 数据库与升级

首次部署执行 `docs/mysql-schema.sql`，建立策略、窗口、服务 revision 三表与类型化规则两表（七字段身份唯一 + 窗口表）。从 1.0.0 升级到 2.0.0 的既有部署需补执行同一 DDL 增建类型化规则两表；既有三元组表结构不变。Starter 不执行 DDL。2.0.0 无结构迁移；既有三元组和快照索引以 service_code 开头，可执行 DATA 过滤。既有策略数据不被自动改写。2.1.0 无结构迁移，依赖 Core 升至 2.3.0；直接 import 目录三件旧包（`...management.directory`）的自定义代码把 import 改到 `...smart.directory` 即可，配置项与 HTTP 契约不变。

从 1.0.0 升级先补齐宿主 Web/Security/Thymeleaf 与认证依赖，再选模式。切 Portal 前配置完整 PAGE/API/DATA 与双来源验证，停止旧入口后再启动新入口；不要让旧 Console 和新 Portal 两套独立部署并行写同一策略库。回滚时停止新入口，恢复 Console 配置和原认证依赖；数据库未发生版本结构变更。

## 扩展、事件与诊断

Repository、管理服务、快照服务、操作人 Provider、事件发布器与目录 Provider（`SmartRedisLimiterDirectoryProvider`）可由宿主 Bean 替换。目录 SPI 自 2.1.0 起由 `smart-redis-limiter-core:2.3.0` 持有（包 `io.github.surezzzzzz.sdk.limiter.redis.smart.directory`），实现方只需依赖 core；本模块继续提供配置式默认实现（`management.typed.services` 部署声明：服务协议模式 TYPED_V2/LEGACY_V1、资源与维度、命名空间、自定义类型、静态对象目录与策略代次 policyEpoch），宿主自带动态目录（如客户事实或 IAM 用户目录适配件）时以自有 Bean 覆盖，无自动注册与心跳。2.0.0 的 Repository/Service 扩展必须实现显式 DATA 范围重载，不能把范围参数忽略。默认 Portal 服务拒绝无范围的 Console 方法。

策略真实变更在事务提交后发布 Core 管理事件；失败、回滚和 no-op 不发布，监听异常不反写已提交事务。需要把策略变更写入审计日志或外部系统时，与 `smart-redis-limiter-audit-listener-starter`（2.2.0+）同进程部署即可，执行/类型化/三元组三条事件链的受控审计开箱即用。三元组规则沿用 Core 2.1.0 的策略事件载荷；类型化规则使用 Core 2.2.0 的类型化事件载荷（含计数对象摘要与操作人摘要）。两种载荷都应只交给受控消费者，不能未经脱敏转发到公开日志。普通 Spring Event 不承诺可靠持久投递。

按 `io.github.surezzzzzz.sdk.limiter.redis.smart.management` 开启 DEBUG 可定位 API 判定、DATA 编译、变更结果、快照生成和提交后发布阶段；不记录 Token、Cookie、完整授权文档或原始限流 subject。运行端读取失败时保留 last-known-good（上次有效策略），不因权限拒绝覆盖有效快照。
