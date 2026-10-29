# simple-crm-server-starter

将 `simple-crm-server-core:2.1.0` 的商业领域契约实现为可独立运行的 CRM 服务：客户/商机/目录/报价/订单/履约闭环的 REST API、MySQL 持久化、幂等、业务审计与三权执行。被人（浏览器 PKCE 人员令牌，经 `smart-crm-admin-web`）或被机器（openapi + AKSK 客户端五件套）调用；本模块不内置登录、页面或管理台。

聚合部署时宿主组合身份协作底座（公共 Resource Server + 人员/机器两个 Provider）后对外提供服务；散形态不引入 Resource 运行时，由宿主提供认证上下文或覆盖 owner 解析桥。CRM 不提供匿名回退：请求缺少已认证主体时拒绝访问。当前版本 `1.1.0`，领域内核 `simple-crm-server-core:2.1.0`。

## 引入依赖

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-crm-server-starter:1.1.0'
    // 身份协作底座（宿主按运行线自引）：
    implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.1.1'
    implementation 'io.github.sure-zzzzzz:simple-iam-resource-server-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:3.1.0'
    // DATA MVC 强制执行与计划注入（宿主运行义务件）
    implementation 'io.github.sure-zzzzzz:simple-data-permission-spring-mvc-starter:1.1.0'
    // server 随带限流执行组件与远程策略快照客户端，默认均关闭
    implementation 'io.github.sure-zzzzzz:smart-redis-limiter-starter:2.2.1'
    implementation 'io.github.sure-zzzzzz:smart-redis-limiter-management-aksk-resttemplate-client-starter:1.0.0'
    // 运行义务件（starter compileOnly 不传递）：
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.apache.httpcomponents:httpclient:4.5.14'
    runtimeOnly 'com.mysql:mysql-connector-j'
}
```

server starter 已传递固定的数据协议、数据库路由和限流运行组件：`simple-data-permission-core:1.2.0`、`simple-mysql-route-starter:1.1.1`、`smart-redis-limiter-starter:2.2.1` 与 `smart-redis-limiter-management-aksk-resttemplate-client-starter:1.0.0`。由于查询服务编程面直接使用 `DataAccessPlan`，DATA core 作为 `api` 契约暴露；MVC 强制执行层 `simple-data-permission-spring-mvc-starter:1.1.0` 仍是宿主运行义务件，必须由宿主显式引入。限流引擎和远程策略客户端默认关闭，规则、Redis Route、AKSK 出站身份与快照权限由部署宿主配置。

## 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: default
            sources:
              default:
                mode: standalone
                host: ${CRM_REDIS_HOST:localhost}
                port: ${CRM_REDIS_PORT:6379}
                database: 0
        mysql:
          route:
            enable: true
            primary-datasource: crm
            datasources:
              crm:
                url: jdbc:mysql://db.example.test:3306/crm?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai
                username: ${CRM_DB_USER}
                password: ${CRM_DB_PASSWORD}
                driver-class-name: com.mysql.cj.jdbc.Driver
        crm:
          server:
            enable: true
        cache:
          enabled: true
          me: ${CRM_CACHE_ME:crm-server}
```

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `io.github.surezzzzzz.sdk.crm.server.enable` | `false` | 总开关，必须显式开启 |

HTTP 资源路径固定为 `/api/crm`，不提供业务模块级路径配置。CRM starter 传递 `simple-mysql-route-starter:1.1.1`；生产宿主启用 Route 后，由 Route 提供唯一或标记为 `@Primary` 的标准 `NamedParameterJdbcTemplate` 及其默认事务管理器。多数据源或多事务管理器时由宿主确定主候选，CRM 不按配置的 Bean 名另建一套连接或事务。测试宿主可以提供同型模板验证业务，不改变生产 Route 接管契约。

### 持久化与 Bean 装配边界

CRM 使用 JDBC 是有意的运行时选择，不以 IAM 的 JPA 仓储形态作为统一模板：报价、订单和履约写路径需要显式行锁、状态 CAS、同事务派生展示态以及与 MySQL Route 共享事务。IAM 自身也在 OAuth2 和授权投影等路径直接使用 `JdbcTemplate`；CRM 与 KMS 同样消费 Route 提供的标准 JDBC 模板。CRM 不创建自己的 `DataSource`、`JdbcTemplate` 或事务管理器。

自有仓储、幂等 Port、控制器、异常处理器和审计事件装饰器使用 `@SimpleCrmServerComponent` 配合精准 `ComponentScan` 加载；审计装饰器额外标记 `@Primary`，并直接注入具体审计仓储，避免把装饰器自身注入为委托。控制器同时保留 MVC stereotype 以承载映射语义。命令/查询编排服务、时钟、ID 生成器和 owner 桥直接由 `SimpleCrmServerAutoConfiguration` 以 `@Bean + @ConditionalOnMissingBean` 注册，允许宿主按稳定契约替换这些扩展点。

`SimpleCrmServerResourceOwnerResolverConfiguration` 不是业务 Bean 的第二套默认配置：它由 `spring.factories` 单独注册，仅在 `VerifiedResourceContext` 与 `ResourceAuthenticationFilter` 同时存在且 CRM 已启用时抢先提供 Resource owner 桥；只有 DATA MVC 带入 Resource core 时仍走散形态桥。这样聚合形态和散形态共用同一 owner 类型，不把 Resource 运行时认证链变成 CRM 的必需依赖。

### 限流组件（随 server 发布，默认关闭）

CRM server 已随带 `smart-redis-limiter-starter:2.2.1`。宿主按其契约启用 Redis Route 并配置拦截器规则；规则以 CRM 路径模式、方法和稳定 `resourceCode` 标识接口，使用 `key-strategy: path-pattern` 让同一规则匹配的请求共享额度；不按 IAM 人员或 AKSK 凭据分别计额。引擎默认关闭，阈值和 Redis 故障时的放行/拒绝策略均由宿主配置。

CRM server 已随带 `smart-redis-limiter-management-aksk-resttemplate-client-starter:1.0.0`。需要远程策略时，宿主配置 `remote-policy`、策略客户端和本地规则兜底；认证复用 AKSK 底座的 `akskClientRestTemplate`，不能把固定 token 写入仓库。为运行端 AKP 仅授权 `smartLimiterPolicySnapshot:read` 和 `limiter-policy` 资源的 `serviceCode` DATA 读取范围，不授予写权或 PAGE。请求路径只读取 limiter 的本地快照，远端失败沿用最后一次有效快照；启用远程策略却没有策略客户端 Bean 时，启动失败。具体配置键与规则格式以两个限流组件的 README 为准。

## 建表

starter 不自动建表。首次安装阅读 `docs/README.md` 并手动执行 `docs/schema.sql`（13 张表，含 DROP，生产已有表禁止重复执行）；结构变更走 `docs/migration/` 的追加式脚本。

## REST API

版本化 JSON API，对外契约以 OpenAPI v2.1.0 文档冻结（`sdk/crm/contract/openapi/simple-crm-server.openapi.yaml`）。37 个权限码逐端点执行（`@RequireApiPermission`），主要资源族：

- **客户**：创建/点查/列表（支持 `keyword` 名称/ID 包含匹配）/停用/复启/合并/归属转移；
- **商机**：创建/点查/列表（按阶段、客户和标题/ID 过滤）/阶段推进/输单/归属转移；归属转移以客户为根并同步未完结销售事实；阶段 `won` 只能由关联报价确认成交派生；
- **联系人**：创建/点查/按客户列表；
- **目录**：创建（DRAFT）/上架（PUBLISHED）/下架/查询（列表支持 `keyword`）；
- **报价**：创建草稿/编辑草稿/签发/确认（生成订单+履约项）/撤回/重议派生/查询（列表支持 `keyword` 匹配单号或客户显示名）；可选关联商机，同一商机可有多个报价，确认关联报价时商机同事务进入 `won`；未确认且未过期报价可归属转移，关联未终态商机同步 owner，客户 owner 与已确认历史不追改；
- **履约**：消费者拉取认领/结果回写/人工重置/消费者注册管理；
- **审计**：业务审计查询。

状态变更统一 `PATCH` 资源本体并按查询参数分派动作（逐动作挂接专属权限码）：客户/报价 `?state=inactive|active|merged`/`?state=issued|confirmed|withdrawn` 或 `?owner=目标`、商机 `?stage=new|qualified|proposal|negotiation|lost` 或 `?owner=目标`、目录 `?state=published|disabled`、履约回报 `?sequence=N`、人工重置 `?state=PENDING_DISPATCH`、重议=带 `?sourceQuotationId` 的 `POST /quotations`；请求体为业务载荷，携带同名字段时须与查询参数一致，缺判别参数不匹配路由。商机 `lost` 必须带 `lostReason`，人工不能推进 `won`。

写请求必带 `Idempotency-Key` 头：同键同参数不重复执行业务或写审计，同键不同参数 409。创建命令复用首个顶级资源 ID，并在当前授权范围内回读该资源；报价创建与重议的重放展示当前版本状态，不固定为草稿。已知目标的状态、治理和履约命令持久化首次成功结果，后续状态变化后重放仍返回首次结果。重放不重算订单状态，当前状态通过查询端点读取。金额与数量十进制字符串、时间 ISO-8601、状态稳定 code；数据范围按主体双轨（人员=owner 维度，机器消费者=派发归属）。

报价的同一商品允许出现多个业务行；创建和编辑时保留每行独立的数量、价格、授权对象与履约范围。服务只加载一次同商品的目录事实，再按原始业务行分别计算和冻结，不合并报价行。

## 分页

客户、联系人、目录、商机、报价、订单、履约项和操作记录共用两种互斥的分页模式；过滤字段在两种模式下相同，API 与 DATA 授权也相同。

| 调用方式 | 请求参数 | 响应字段 |
|---|---|---|
| 管理台页码查询 | `page`、`pageSize` | `items`、`page`、`pageSize`、`total` |
| 机器游标续读 | `cursor`、`limit` | `items`、`nextCursor` |

管理台传 `page` 或 `pageSize` 即选择页码模式，省略项分别使用 1 和 20；每页条数范围为 1～100，非法值返回 400，不静默截断。`total` 是相同过滤和授权范围内的真实总数，与当页查询来自同一事务读取快照，不能按已加载行数估算。超出末页返回空 `items`，保留请求页码和真实总数。页码模式不返回 `nextCursor`。

机器续读首页省略 `cursor`，`limit` 默认为 20、范围为 1～100；下一次原样传回 `nextCursor`，其为 `null` 时结束。游标绑定列表族、调用主体、过滤条件和归属授权范围；这些条件变化后须重新查首页，旧查询游标返回 400。服务端实际探测到更多记录才返回续读游标，恰好满页的末页无需再发一次空页请求。游标模式不返回页码或总数。

同一请求混用 `page/pageSize` 和 `cursor/limit` 返回 400，出现分页参数但值为空同样返回 400。消费者列表只提供页码参数；不带分页参数时保留完整 `items` 的机器客户端兼容响应，不返回 `nextCursor`，管理台使用页码参数。消费者请求出现 `cursor` 或 `limit` 即返回 400。

```http
GET /api/crm/orders?page=1&pageSize=20
```

```json
{ "items": [], "page": 1, "pageSize": 20, "total": 0 }
```

Core 的 `CrmQueryFacade` 声明列表查询语义，starter 的 HTTP 查询服务使用 Core 的列表模型，并在入口结合真实 DATA 计划执行查询；starter 不自动装配 `CrmQueryFacade` Bean。Java 机器客户端继续使用游标方法，管理台直接消费 HTTP 页码响应。

## 权限模型

- **PAGE**：页面准入（`crmWeb:page`），部署时在 IAM 登记，本模块无页面；
- **API**：37 个 `crm.*` 精确码，控制器逐端点执行；商机新增 `crm.opportunity.create/read/list/stage/transfer`；
- **DATA**：资源 `crm-customer` 维度 `owner`——人员仅本人事实，机器按派发归属；治理动作（合并/转移/审计）由对应角色的授权范围控制。

IAM 侧登记值与配置顺序见 `smart-crm-admin-web` 仓 `docs/TRUSTED_APPLICATION_ONBOARDING.md`（唯一权威手册）。

## 事件与消息

零消息中间件：不依赖 Kafka、不发跨服务事件，业务消费（履约协调）由消费者经 HTTP 客户端拉取回写完成（消费范式）。进程内存在一类审计事件：写事务提交后发布 `CrmAuditEvent`（回滚不发、幂等重放不重发），由 `simple-crm-audit-listener-starter` 扇出给宿主审计处理器（Handler SPI，首发仅日志处理器）；本地 `crm_audit` 表仍是审计查询端点的唯一权威数据源。

## 运行环境

Spring Boot 2.7.9 / Java 8，MySQL 5.7+。机器消费者接入用客户端五件套（`simple-crm-aksk-{resttemplate,feign}-client-starter` 及 jakarta 变体），方法面见 `simple-crm-client-core` README。
