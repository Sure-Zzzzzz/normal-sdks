# Simple AKSK 用户手册

> **2.x 已封版**：Simple AKSK 2.x 的最终已发布坐标、接入方式和运行边界已固化在 [USER_MANUAL.2.x.md](USER_MANUAL.2.x.md)。2.x 不再接受功能、缺陷修复或常规维护发布。
>
> 使用 1.x 的系统请查看 [USER_MANUAL.1.x.md](USER_MANUAL.1.x.md)。

## 文档入口

| 版本线 | 文档 | 状态 |
|--------|------|------|
| 3.x（当前） | 本文档 | 持续维护 |
| 1.x | [USER_MANUAL.1.x.md](USER_MANUAL.1.x.md) | 历史冻结快照 |
| 2.x | [USER_MANUAL.2.x.md](USER_MANUAL.2.x.md) | 最终冻结快照 |

---

# 3.x 用户手册

## 目录

- [1. 概述](#1-概述)
- [2. 架构设计](#2-架构设计)
- [3. 模块总览](#3-模块总览)
- [4. 部署 AKSK Server](#4-部署-aksk-server)
- [5. 客户端接入](#5-客户端接入)
- [6. 资源服务接入](#6-资源服务接入)
- [7. 审计挂件](#7-审计挂件)
- [8. 与 IAM 协作](#8-与-iam-协作)
- [9. 运维](#9-运维)
- [10. 常见问题](#10-常见问题)
- [11. 3.x 版本历史](#11-3x-版本历史)

## 1. 概述

### 1.1 什么是 Simple AKSK 3.x？

Simple AKSK 是基于 OAuth2 Client Credentials 的服务身份认证体系，类似 AWS / 阿里云的 AKSK（Access Key / Secret Key）模式：服务、脚本、第三方系统以 AK/SK 换取 JWE 加密 Access Token 调用 OpenAPI。

3.x 相对 1.x / 2.x 的核心变化：

| 维度 | 3.x |
|---|---|
| Token 形态 | JWE（A256GCMKW）加密，payload 密文不可读；`scope` 不再是授权载体 |
| 授权模型 | **应用授权自闭环**：`aksk_authorization` 快照（角色 / 页面权限 / 精确 API permission / `DataGrantDocument`）是唯一可信授权来源；未准入的 Client 拒绝签发 |
| Redis | **必需**（Token 缓存、撤销同步、L1 失效广播、OAuth2 端点限流） |
| `/api` 管理面 | 3.1.0 起移交公共资源层鉴权（精确 API permission + DATA 双闸，零 Set-Cookie） |
| 双身份 | 公共资源层 + Provider 组合，同一业务 API 可同时接受 AKSK 服务身份与 IAM 人员身份 |

### 1.2 核心特性

- **双层级 AKSK**：平台级（AKP，服务间调用）与用户级（AKU，用户 API / 第三方集成）
- **OAuth2 标准**：基于 Spring Authorization Server 0.4.1
- **即时撤销**：`/oauth2/revoke` 后 introspect 立即 `active=false`，L1+L2 缓存同步清除
- **L1+L2 两级缓存**：Caffeine 本地 + Redis 分布式，introspect 热路径命中 L1 无 Redis IO
- **多实例强一致**：Redis Pub/Sub 广播缓存失效
- **审计事件**：颁发 / 撤销 / 删除 / introspect 全生命周期；可选审计 listener 事务提交后投递
- **过期 Token 自动清理**（3.1.1）：定时任务 + 分布式锁互斥 + 分批删除

### 1.3 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 2.7.x（验证档 2.7.9） | 基础框架 |
| Spring Authorization Server | 0.4.1 | OAuth2 授权服务器 |
| Java | 11（运行）/ 8+（客户端源码兼容） | 服务端建议 Java 11 |
| MySQL | 5.7+ / 8.0+ | Client / Token / 授权投影持久化 |
| Redis | 必需 | 缓存 / 撤销同步 / 限流 |
| RSA + AES-256 | — | JWE 签名 + 加密材料 |

## 2. 架构设计

### 2.1 整体架构

```text
┌──────────────── 调用方（服务 / 脚本 / 第三方） ─────────────────┐
│  feign-redis-client-starter / resttemplate-redis-client-starter │
│  （TokenManager 自动取token + 挂 Authorization 头）             │
└───────────────┬────────────────────────────────────────────────┘
                │ POST /oauth2/token（Basic: AK/SK）
┌───────────────▼────────────────────────────────────────────────┐
│                      AKSK Server                                │
│  /oauth2/token /oauth2/revoke /oauth2/introspect /oauth2/jwks  │
│  /admin（管理台）/ /api/**（管理 REST，公共资源层鉴权）         │
│  应用授权投影 → aksk_authorization 快照进 Token                 │
└──────┬──────────────────────────┬──────────────────────────────┘
       │                          │
  ┌────▼────┐                ┌────▼────┐  introspect 回源
  │  MySQL  │                │  Redis  │◄──────────── 业务资源服务
  └─────────┘                └─────────┘  (公共资源层 + AKSK Provider)
```

### 2.2 3.0 权限模型：三个载体各司其职

| 载体 | 作用 | 来源 | 是否可信授权来源 |
|------|------|------|------------------|
| `scope` | OAuth2 请求范围（只能在 Client 注册范围内缩小） | OAuth Client 配置与 token 请求 | 否 |
| `security_context` | 业务运行时上下文（租户、请求来源等） | 每次 token 请求由调用方传入 | 否（不可信输入） |
| `aksk_authorization` | 应用授权快照（角色 / 页面 / API permission / DATA 文档） | 服务端应用授权管理 | **是** |

Client 创建只注册 OAuth Client，**不自动创建应用授权**。接入顺序必须是：建 Client → 配完整应用授权 → 显式准入 → 才能换 Token。资源侧 API 与 DATA 校验只看 `aksk_authorization`。

### 2.3 AKSK 类型

| 类型 | 前缀 | 用途 |
|------|------|------|
| 平台级（AKP） | `AKP` | 服务间调用、后台任务、系统级操作 |
| 用户级（AKU） | `AKU` | 用户 API 调用、移动端、第三方集成 |
| Secret Key | `SK` | 与 AK 配对，BCrypt 加密存储，仅创建时返回明文 |

## 3. 模块总览

3.x 家族四组（均已发布，以 Maven Central 实证为准）：

**Server 组**（部署认证服务器）：

```text
simple-aksk-server-starter                        3.1.1
simple-aksk-server-core                           3.0.2
simple-aksk-core                                  3.0.0
```

**Client 组**（调用方自动管理 Token）：

```text
simple-aksk-feign-redis-client-starter            3.0.1
simple-aksk-resttemplate-redis-client-starter     3.0.1
simple-aksk-redis-token-manager                   3.0.1
simple-aksk-client-core                           3.0.0
```

**Resource 组**（业务资源服务校验 AKSK Token）：

```text
simple-resource-server-starter                    1.1.1   （公共资源层）
simple-resource-server-core                       1.1.1   （公共资源层）
simple-aksk-resource-server-starter               3.0.1   （AKSK Provider）
simple-aksk-resource-core                         3.0.0
simple-data-permission-spring-mvc-starter         1.0.1   （DATA 评估，通用，不限 AKSK）
```

**审计组**（可选挂件）：

```text
simple-aksk-server-audit-listener-starter         3.0.0   （Server 侧 Token 生命周期事件）
simple-aksk-resource-audit-listener-starter       3.0.0   （资源侧访问事件，按来源过滤）
```

版本兼容矩阵见主仓 [README](../../../README.md) 的「Resource 版本兼容」表。

## 4. 部署 AKSK Server

### 4.1 基础设施清单

- Spring Boot 2.7.x 应用工程（服务端用 Java 11）
- MySQL 5.7+ / 8.0+（AKSK 专用库）
- Redis（**必需**，3.0.0 起不支持无 Redis 运行）
- 独立保存的管理员口令；密钥材料不进仓库不进日志

密钥材料共两样，先在部署机生成好（4.4 配置里要用）：

```bash
# ① RSA 密钥对（Token 签名）
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem

# ② JWE 加密密钥（Base64 的 32 字节 AES-256，输出即最终值，抄走保存）
openssl rand -base64 32
```

### 4.2 依赖引入

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:3.1.1'

    // 必需：宿主自备（starter 以 compileOnly 口径声明）
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // Admin 管理页面模板引擎（admin.enabled 默认开；显式关闭后可省略）
    runtimeOnly 'org.springframework.boot:spring-boot-starter-thymeleaf'

    runtimeOnly 'mysql:mysql-connector-java:8.0.33'
}
```

### 4.3 初始化数据库

先建库（schema 脚本只建表不建库）：

```sql
CREATE DATABASE IF NOT EXISTS sure_auth_aksk
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
```

新部署执行完整初始化（**含 DROP TABLE，不得用于存量环境**）；从 2.x 升级走另一条路：

```bash
# 新部署或允许重建
mysql -u <user> -p sure_auth_aksk < server/simple-aksk-server-starter/docs/01_schema_3.0.0.sql

# 从 2.x 升级：先备份并停写，仅执行一次
mysql -u <user> -p sure_auth_aksk < server/simple-aksk-server-starter/docs/02_upgrade_3.0.0.sql
```

执行完核对三张表存在：`oauth2_registered_client`、`oauth2_authorization`、`aksk_application_authorization`。

2.x 升级脚本保留 Client 与 Token 数据、新建授权投影表，但**不从旧 Scope 自动推导或自动准入**；每个 Client 首次 3.0 准入前须先处理其 2.x 存量活跃 Token。存量环境补 3.1.1 清理索引用 `docs/03_upgrade_3.1.1.sql`。

### 4.4 配置

下面这份可以整抄，改掉占位符（数据库地址账号密码、Redis 地址、三个密钥值）即可启动。完整逐键注释版见 [server README](server/simple-aksk-server-starter/README.md) 的「配置应用」：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        # ① 数据源（mysql-route 接管，不配 spring.datasource.*）
        mysql:
          route:
            enable: true
            primary-datasource: default
            datasources:
              default:
                url: jdbc:mysql://127.0.0.1:3306/sure_auth_aksk?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
                username: ${AKSK_DB_USERNAME}
                password: ${AKSK_DB_PASSWORD}
        # ② Redis（redis-route 接管）
        redis:
          route:
            enable: true
            sources:
              default:
                host: 127.0.0.1
                port: 6379
                database: 0
        auth:
          aksk:
            server:
              jwt:
                key-id: sure-auth-aksk-2026        # 不得带 aksk/ 路由前缀（启动校验拦截）
                expires-in: 3600
                public-key: classpath:keys/public.pem    # 4.1 生成的公钥（放 src/main/resources/keys/）
                private-key: classpath:keys/private.pem  # 4.1 生成的私钥（同上，勿提交仓库）
                encryption-key: ${AKSK_JWE_KEY}    # 4.1 生成的 openssl rand -base64 32 输出，必填
              redis:
                token:
                  me: my-aksk-server               # 集群标识①
              admin:
                username: admin
                password: ${AKSK_ADMIN_PASSWORD}   # 必填
          # ③ 限流——me 必须与上面集群标识①同值（抄下面这行引用即可，不会错）
          limiter:
            redis:
              smart:
                enable: true
                me: ${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me}  # 集群标识②
        # ④ 缓存——me 同样引用集群标识①
        cache:
          key-prefix: sure-auth-aksk
          me: ${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me}        # 集群标识③
```

三条硬规矩：

- **三个 `me` 必须同值**（`redis.token.me` / `limiter.redis.smart.me` / `cache.me`）——照抄上面的 `${...}` 引用写法即可天然保证一致；手写三个不同值会导致缓存、失效广播、限流落到不同命名空间
- **启动 fail-fast 三项**：`jwt.key-id` 带路由前缀或非法字符；`auth.resource.server.enabled=false`；protected-paths 摘掉 `/api/**`——任一命中直接拒绝启动
- 凭据一律 `${ENV:}` 注入，不写明文

### 4.5 建立第一个可签发 Client

五步顺序不能颠倒——建了 Client 不配授权就换不出 Token（这是 3.x 的设计，不是故障）：

1. 启动后浏览器访问 `http://<部署地址>/admin`，用 4.4 配置的 `admin.username` / `admin.password` 登录
2. 点「创建平台级 Client」（服务间调用）或「创建用户级 Client」（用户 API），**立刻保存弹出的 Client ID 和 Client Secret——Secret 只显示这一次**
3. 回到 Client 列表，点该 Client 的**「详情」**，再点**「管理应用授权」**，完整配置：应用编码、角色、页面权限、精确 API permission、`DataGrantDocument`（DATA 授权文档）
4. 勾选**「允许签发应用授权快照」开关**，点**「保存完整授权」**——这就是"准入"；不勾的话状态一直是"草稿"（页面右侧"授权生命周期"栏可核对当前状态：未配置→草稿→已准入），换 Token 会被拒
5. 验证：拿 AK/SK 换 Token，再调 introspect 确认 `active=true`：

```bash
# 换 Token（Basic 是 Client ID:Client Secret）
curl -s -u "<AK>:<SK>" -d "grant_type=client_credentials" \
  https://aksk.example.com/oauth2/token
# 期望返回 {"access_token":"...","expires_in":3600,...}，抄下 access_token

# 验证 Token 活跃
curl -s -u "<AK>:<SK>" -d "token=<上一步的access_token>" \
  https://aksk.example.com/oauth2/introspect
# 期望返回 {"active":true,...}
```

没有「已启用 + 已准入 + 未撤销」的应用授权投影时，Server 拒绝签发有效 Token（换 Token 响应是错误而非 token）。

### 4.6 新装验收

4.5 的两条 curl 已经通了主链路，再核对三条行为符合预期即可：

- [ ] 未准入的新 Client 换 Token 被拒（3.x 设计：无授权投影不签发）
- [ ] Token 撤销后 introspect 返回 `active=false`：

```bash
curl -s -u "<AK>:<SK>" -d "token=<access_token>" \
  https://aksk.example.com/oauth2/revoke
# 再跑一次 4.5 第 5 步的 introspect，期望 {"active":false}
```

- [ ] 同一 Client 重新准入后换新 Token 正常，旧 Token 仍 inactive

完整发布验收矩阵见 [发布验收清单](server/simple-aksk-server-starter/docs/06_release_acceptance_3.0.0.md)，接入业务资源服务前至少过完上面三条。

## 5. 客户端接入

调用 AKSK 保护的 OpenAPI 的服务，用客户端 Starter 自动管理 Token（取 token、缓存、预刷新、挂 Authorization 头）。

### 5.1 Feign（推荐）

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:3.0.1'
    // 使用方自备（starter compileOnly 不传递）
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign:3.1.8'   // SB 2.7.x 档
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'io.github.openfeign:feign-httpclient:11.10'
}
```

```java
@SpringBootApplication
@EnableFeignClients
public class MyApplication { ... }

@AkskClientFeignClient(name = "order-service", url = "${order.base-url}")
public interface OrderClient {
    @GetMapping("/api/orders")
    List<Order> list();
}
```

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
              default: { mode: standalone, host: localhost, port: 6379, database: 0 }
        auth:
          aksk:
            client:
              client-id: ${AKSK_CLIENT_ID}
              client-secret: ${AKSK_CLIENT_SECRET}
              server-url: https://aksk.example.com
        cache:
          enabled: true
          key-prefix: my-app-aksk
          me: my-app            # 同一应用多实例必须一致
```

### 5.2 RestTemplate

同形态坐标换 `simple-aksk-resttemplate-redis-client-starter:3.0.1`，拦截器自动挂头。两客户端的版本对应表、Token 缓存调优（L1 TTL 建议 2~5 秒）、`security_context` 传参细节见各自 README。

## 6. 资源服务接入

业务资源服务校验 AKSK Token：**公共资源层**（唯一 Bearer 入口 + kid 路由 + API/DATA 校验）+ **AKSK Provider**（introspect 回源）。

### 6.1 组合依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.1.1'
    implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:3.0.1'
}
```

### 6.2 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          resource:
            server:
              security:
                protected-paths:
                  - /api/**                 # 你的业务 API 前缀，按实际改
          aksk:
            resource:
              server:
                enabled: true
                introspect:
                  endpoint: https://aksk.example.com/oauth2/introspect
                  client-id: ${AKSK_INTROSPECT_CLIENT_ID}       # 内省客户端（AKSK 侧明确授权）
                  client-secret: ${AKSK_INTROSPECT_CLIENT_SECRET}
                  local-cache:
                    enabled: false          # 保持默认关；开启后撤销延迟=缓存 TTL 窗口
                    fallback:
                      enabled: false        # 保持默认关（stale 降级放行旧凭据）
```

**AKSK 3.0 不支持匿名内省**——introspect 必须用已认证客户端。`endpoint` / `client-id` / `client-secret` 缺任一项自动配置阶段直接抛错（启动失败），不会静默运行。

### 6.3 认证与授权顺序

```text
Bearer / Provider 认证（kid 前缀 aksk/ 路由；401）
→ 应用准入与精确 API permission（403）
→ DATA 访问计划评估与完整范围执行（403）
→ 业务领域约束
```

业务接口声明精确 API permission（码来自服务端应用授权配置）：

```java
@GetMapping("/api/orders")
@RequireApiPermission("order.read")
public List<Order> list() { ... }
```

scope、角色、PAGE 权限、URL、HTTP method、`security_context` 都不授予 API 或 DATA 权限——权限只来自服务端「应用授权管理」里配置的 API 码与 DATA 文档，接口上的 `@RequireApiPermission("...")` 必须与那边配的码逐字一致。DATA 消费用 `simple-data-permission-spring-mvc-starter`（`@DataPermissionOperation` / `@CurrentDataAccessPlan`），同一 grant 内约束 AND、grant 间 OR，无法完整执行时必须拒绝。

边界细节（Provider 校验清单、fallback 语义）见 [simple-aksk-resource-server-starter README](resource/simple-aksk-resource-server-starter/README.md)。

## 7. 审计挂件

两个可选挂件，独立按需引入：

**Server 侧**（Token 生命周期事件：颁发 / 撤销 / 删除 / introspect，含授权替换撤销的 cause）：

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-audit-listener-starter:3.0.0'
```

实现 `AkskAuditHandler` SPI；事务提交后投递，记录不含 Token 原文。

**资源侧**（访问事件，订阅公共层 `ResourceAccessEvent`、按 `aksk` 来源过滤、`@Async` 分发）：

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-audit-listener-starter:3.0.0'
```

实现 `AkskAuditHandler` 接口即接入。两者详见各自 README。

## 8. 与 IAM 协作

AKSK 独立部署独立使用。同一业务 API 需要同时接受**人员身份**（IAM）与**服务身份**（AKSK）时，在资源服务组合公共资源层 + 两个 Provider——认证源 OR 不回退、不共享数据库、不合并权限。

另一形态：AKSK Server 自身的 `/api/**` 管理面默认只认自家 JWE（本地解密验证，无需外插 aksk-resource）；需要让门户以 IAM 人员身份调管理 API 时，部署工程外插 `simple-iam-resource-server-starter` 即可，零代码改动。

完整边界与操作序见 [IAM 与 AKSK 协作接入](../README.IAM-AKSK协作.md)；Server 侧双身份说明见 [server README](server/simple-aksk-server-starter/README.md) 的「与 IAM 协作」。

## 9. 运维

- **应用授权运维**：授权完整替换或撤销会**事务性撤销该 Client 全部活跃 Token**（每个失效 Token 产生 `REVOKED` 事件，cause 为 `APPLICATION_AUTHORIZATION_REPLACED` 或 `APPLICATION_AUTHORIZATION_REVOKED`）；历史 Token 不因投影更新获得新授权
- **过期清理**（3.1.1）：默认每天凌晨 2 点，`cleanup.cron` 可覆盖；分布式锁多实例互斥；分批删除（默认 2000 行/批）防长事务；依赖 `access_token_expires_at` 索引
- **缓存一致性**：`consistency.mode=strong` 时 revoke 经 Pub/Sub 广播，其他实例 L1 立即清除
- **安全日志**：不输出 Authorization、Cookie、Token、client secret、完整 introspection 响应

常见处置与运行边界详见 [运维手册](server/simple-aksk-server-starter/docs/05_operations_3.0.0.md)。

## 10. 常见问题

| 问题 | 答案 / 处置 |
|---|---|
| 如何生成 RSA 密钥对？ | `openssl genrsa -out private.pem 2048` + `openssl rsa -in private.pem -pubout -out public.pem` |
| 如何生成 AES-256 密钥？ | `openssl rand -base64 32` |
| Redis 必需吗？ | 是。3.0.0 起 Redis 为必需基础设施 |
| 如何禁用 Admin 管理页面？ | `io.github.surezzzzzz.sdk.auth.aksk.server.admin.enabled: false`（门户形态，`/admin` 整链不装配） |
| 撤销后 introspect 仍 `active=true`？ | 查三件事：三个 `me` 是否同值；`consistency.mode` 是否 `strong`；启动日志有无 `Cache invalidation listener initialized` |
| JWE Token 能客户端解析吗？ | 不能。5 段结构 payload 加密；调试走 introspect |
| APISIX 怎么接？ | 推荐 introspect 验证（`jwt-auth` 插件不能验 JWE）；见 server README「APISIX 集成建议」 |
| 换了 token 还 403？ | 3.0 起授权只看 `aksk_authorization` 快照：核对应用授权已配置且**已显式准入**，API 码与 DATA 文档齐全 |
| 启动出现 `CasJackson2Module` 警告 | 无害；`logging.level.org.springframework.security.jackson2: ERROR` 屏蔽 |
| Token 结构里 scope 变了没生效？ | scope 只能在 Client 注册范围内**缩小**，且不参与 API/DATA 授权——权限调整走应用授权管理，不走 scope |

## 11. 3.x 版本历史

| server-starter | server-core | core | 要点 |
|---|---|---|---|
| 3.1.1 | 3.0.2 | 3.0.0 | 过期 Token 定时清理（分布式锁多实例互斥 + 分批删除），`oauth2_authorization.access_token_expires_at` 索引补齐 |
| 3.1.0 | 3.0.1 | 3.0.0 | `/api` 管理端点鉴权移交公共资源层 1.1.1（跨资源双层鉴权 + 主体一致性校验，管理 API 零 Set-Cookie）；启动期 fail-fast 校验；MySQL 切 mysql-route 接管 |
| 3.0.1 | 3.0.1 | 3.0.0 | 内省时间 claim 整秒截断（修签发后立即访问的间歇 403）、授权时效 2 秒时钟容差；Token 事件新增 `TokenEventCause` 审计原因契约 |
| 3.0.0 | 3.0.0 | 3.0.0 | 应用授权自闭环；Token 按已准入授权快照签发与内省；管理 REST 使用精确 API permission + DataAccessPlan |

> 逐版细节见 [server README](server/simple-aksk-server-starter/README.md) 的「版本历史」与各 CHANGELOG。`simple-aksk-server-audit-listener-starter:2.0.1` 是独立历史审计扩展，不纳入 3.x 发布组合。

---

## 附：2.x 最终已发布坐标（冻结）

```text
simple-aksk-core                                  2.0.0
simple-aksk-server-core                           2.0.3
simple-aksk-server-starter                        2.0.3
simple-aksk-client-core                           2.0.0
simple-aksk-redis-token-manager                   2.0.1
simple-aksk-feign-redis-client-starter            2.0.1
simple-aksk-resttemplate-redis-client-starter     2.0.1
simple-aksk-resource-core                         2.0.0
simple-aksk-resource-server-starter               2.0.1
simple-aksk-resource-audit-listener-starter       2.0.0
simple-aksk-server-audit-listener-starter         2.0.1
```

HTTP Session Client、`simple-aksk-security-context-starter`、metrics starter 的未发布设计稿及 Client Demo 不属于 AKSK 2.x 对外发布矩阵，也不纳入 3.x 发布矩阵；仅保留历史版本或测试用途，不生成 3.x 发布物。
