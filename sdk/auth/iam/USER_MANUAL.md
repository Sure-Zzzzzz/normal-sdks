# Simple IAM 用户手册

> 本手册是 IAM 1.0.0 的**从零部署到验收导引**：按角色（平台部署方 / 前端部署方 / 业务应用方 / 资源服务方）给出接入路径。各模块的精确契约以模块 README 与领域文档为权威，手册每章末尾给出链接；两者不一致时以模块 README 为准。

## 目录

- [1. 概述](#1-概述)
- [2. 架构设计](#2-架构设计)
- [3. 模块总览](#3-模块总览)
- [4. 部署 IAM Server](#4-部署-iam-server)
- [5. 前端部署（门户形态）](#5-前端部署门户形态)
- [6. 登录适配器使用](#6-登录适配器使用)
- [7. 业务应用接入（挂门户）](#7-业务应用接入挂门户)
- [8. 资源服务接入 IAM 身份](#8-资源服务接入-iam-身份)
- [9. 开放 API（AKSK 调 IAM）](#9-开放-apiaksk-调-iam)
- [10. 多实例与运维](#10-多实例与运维)
- [11. 常见问题](#11-常见问题)
- [12. 文档地图](#12-文档地图)

---

## 1. 概述

### 1.1 什么是 Simple IAM？

Simple IAM 是一套统一身份认证与授权服务（IAM Server）：承载本地账号体系、浏览器登录会话、OAuth 2.1 / OIDC 授权协议、RBAC、可信应用、统一应用门户、站内信与审计事件，为业务系统提供"一次登录、处处可用"的身份底座。

它是一个**可独立部署的 Spring Boot 应用模块**（`simple-iam-server-starter`）：宿主引入依赖、配好 MySQL / Redis / 密钥，即得到完整的 IAM 服务。它**不提供浏览器页面**——登录页、授权确认页、门户和管理界面由三个独立 Vue 前端仓库承载，IAM Server 只提供协议端点和 JSON API。

### 1.2 核心特性

- **本地账号密码登录**：委托编码器（BCrypt 优先）、失败计数、渐进人机验证、账号锁定
- **外部身份源登录**：LDAP 凭证型与 OIDC 跳转型两种形态，经 SPI 接入（适配器为独立 starter）
- **首登 / 重置后强制改密**：管理员建号与重置都发临时密码，首登强制改密后才放行
- **OAuth 2.1 授权码 + PKCE + Consent**：公共客户端强制 S256；授权码与 state 一次性消费
- **Token 双模式**：`jwt`（JWS RS256）或 `jwe`（Access Token 加密，ID Token 仍 JWS）
- **Refresh Token 主动防线**：全局一次性使用（必轮换），旧值重放整族吊销
- **RBAC 三层授权投影**：应用权限清单（申报制）→ 角色授权规则 → 用户授权投影（进 Access Token）
- **可信应用与统一应用门户**：业务应用以可信应用身份挂载门户，qiankun 微前端或外链
- **站内信**：管理端发送、Web 端读取、SSE 实时推送
- **审计事件四族**：Token / 认证 / 会话 / 管理面，监听器 SPI 落地
- **多实例**：会话、授权状态、限流、锁、缓存全在共享存储，无需粘性路由

### 1.3 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 2.7.x（验证档 2.7.9） | 基础框架，不支持 Boot 3 / jakarta |
| Spring Authorization Server | 0.4.1 | OAuth2 / OIDC 授权服务器 |
| Spring Security | 5.7.x | 安全框架 |
| Java | 8+（源码兼容） | 运行环境 |
| MySQL | 5.7+ / 8.0+ | 持久化，字符集 `utf8mb4`，26 张表 |
| Redis | 6+ | 会话 / 缓存 / 限流 / 锁 / SSE 广播 / OAuth2 授权缓存共用 |
| RSA + 可选 AES-256 | — | Token 签名密钥对；`jwe` 模式另需加密密钥 |

### 1.4 与 AKSK 的关系

IAM 管人员身份（浏览器登录），AKSK 管服务身份（机器凭证）。两者独立部署、独立使用，不互相嵌入；同一业务 API 需要同时接受两种身份时，在**资源服务**组合两个 Provider——见 [IAM 与 AKSK 协作接入](../README.IAM-AKSK协作.md)。

## 2. 架构设计

### 2.1 整体架构

```text
┌─────────────────────────── 浏览器 ───────────────────────────┐
│  登录页(/)   统一应用门户(/app/)   IAM 管理台(/app/iam/)      │
│  login-web        portal-web(壳)      admin-web(子应用)      │
└──────────────┬──────────────────────────────────────────────┘
               │ 静态资源 + JSON API（/iam/**、/oauth2/**）
        ┌──────▼──────┐
        │   nginx     │  统一入口：静态三区 + 后端反代
        └──────┬──────┘
               │
┌──────────────▼──────────────────────────────────────────────┐
│                      IAM Server（8180）                      │
│  OAuth2/OIDC 端点 │ Web API │ 管理 API │ 开放 API │ 验证端点  │
└───────┬──────────────────────────────────┬──────────────────┘
        │                                  │
   ┌────▼─────┐                      ┌─────▼─────┐    受控验证
   │  MySQL   │                      │   Redis   │◄─────────── 资源服务
   │(26张表)  │                      │ 会话/缓存 │  (持 IAM Token
   └──────────┘                      └───────────┘   调 verify)
```

### 2.2 端点分区

IAM Server 的 HTTP 面按安全链分七区（Order 精确分工，先匹配先赢）：

| 分区 | 认证方式 | 内容 |
|---|---|---|
| `/oauth2/**`、`/userinfo`、`/.well-known/**` | 授权服务器自身 | OAuth 2.1 / OIDC 协议端点 |
| `/iam/web/**` | 匿名七端点 + 其余会话 | 登录、验证码、门户供数、站内信、主题偏好 |
| `/iam/admin/**` | 管理台进门权（`ROLE_iam_admin` 或任一页面权限码） | 用户/组织/角色/可信应用/站内信管理 |
| `/iam/api/**` | AKSK Bearer（公共资源层链接管） | 开放 API：组织与人员同步 |
| `/iam/resource/**` | 验证客户端 Basic | 远程 Token 验证端点 |
| `/iam/**` 其余 | 已会话 | 兜底 |
| 其余全部 | denyAll | 显式拒绝 |

完整端点清单与逐链说明见 [simple-iam-server-starter README](server/simple-iam-server-starter/README.md) 的「HTTP 端点总览」「安全过滤链」。

### 2.3 契约链路三段

| 段 | 角色 | 说明 |
|---|---|---|
| 1 | **IAM Server** | 身份提供方（IdP）：用户库、登录会话、授权码、Token 签发与验证 |
| 2 | **Vue Web** | 登录页 / Consent 页 / 门户 / 管理界面的渲染方；服务端供数，无独立后端 |
| 3 | **业务系统** | 持 Token 访问的资源应用；受控验证或 JWKS 本地验签 |

## 3. 模块总览

IAM 1.0.0 发布矩阵（均已在 Maven Central）：

```text
simple-iam-core                                   1.0.0   协议契约基座（SPI / 路由键 / 错误契约）
simple-iam-server-core                            1.0.0   Server 域契约（事件 / 错误码 / 常量）
simple-iam-server-starter                         1.0.0   IAM Server 应用模块（本手册主角）
simple-iam-ldap-adapter-starter                   1.0.0   LDAP 凭证型登录适配器
simple-iam-oidc-adapter-starter                   1.0.0   OIDC 跳转型登录适配器
simple-iam-captcha-adapter-starter                1.0.0   图片验证码适配器（server-starter 已传递引入）
simple-iam-resource-core                          1.0.0   IAM 人员认证结果模型
simple-iam-resource-server-starter                1.0.0   资源服务 IAM Provider
simple-iam-server-audit-listener-starter          1.0.0   Server 审计事件挂件（可选）
simple-iam-resource-audit-listener-starter        1.0.0   资源访问审计挂件（可选）
```

依赖关系：

```text
simple-iam-core ──► simple-iam-server-core ──► simple-iam-server-starter
      ▲                                              ▲
      │ SPI                                          │ 宿主引入
ldap/oidc/captcha adapter（任选） ────────────────────┘

资源服务侧：simple-resource-server-starter（公共层）
            + simple-iam-resource-server-starter（IAM Provider）
```

选型指引：

- **部署 IAM 平台**：只需 `simple-iam-server-starter`（按需加登录适配器与审计挂件）
- **只订阅 IAM 事件 / 引用错误码**：`simple-iam-server-core`
- **写新登录适配器**：`simple-iam-core` 的 SPI
- **业务资源服务接 IAM 身份**：公共资源层 + IAM Provider（第 8 章）

## 4. 部署 IAM Server

### 4.1 依赖引入

新建一个 Spring Boot 2.7.x 工程（Java 8+ 即可），build.gradle 抄这段——四行缺一不可：

```gradle
dependencies {
    implementation "io.github.sure-zzzzzz:simple-iam-server-starter:1.0.0"   // IAM 本体（连带五件配套组件与 MySQL 驱动）
    implementation "org.springframework.boot:spring-boot-starter-web"
    implementation "org.springframework.boot:spring-boot-starter-security"
    implementation "org.springframework.boot:spring-boot-starter-data-jpa"
}
```

`mysql-connector-java` 已随模块 runtimeOnly 引入，不用自己加；数据源不走 `spring.datasource.*`，由 mysql-route 接管（4.4 ①）。

### 4.2 初始化数据库

在**全新环境**执行 [schema.sql](server/simple-iam-server-starter/docs/schema.sql)（26 张表 = 3 张 SAS 标准表 + 23 张 `iam_*` 业务表）。该脚本包含建表前置清理，**不能直接用于已有数据环境**。

```sql
CREATE DATABASE sure_auth_iam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sure_auth_iam;
SOURCE schema.sql;
```

### 4.3 基础设施清单

| 依赖 | 要求 | 用途 |
|---|---|---|
| MySQL | 5.7+ / 8.0+，`utf8mb4` | 持久化 |
| Redis | 6+ | 会话 / 缓存 / 限流 / 锁 / SSE 广播 / OAuth2 授权缓存 |
| RSA 密钥对 | PEM 格式 | Token 签名；`jwe` 模式另需 AES-256 密钥（Base64 的 32 字节） |

生成密钥对（抄手册前先备好）：

```bash
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
```

`public-key` / `private-key` 配置支持 PEM 文件路径（如 `classpath:keys/public.pem`）、PEM 内容或 Base64 三种形态。mysql-route / redis-route / lock / limiter / cache 五件配套组件由本模块依赖自动引入，配置全在 4.4 一份 yaml 里，无需另查组件文档。

### 4.4 最小运行配置

下面这份配置**可以整抄**：改掉其中的占位符（数据库地址账号密码、密钥、`issuer` 域名）即可启动。缺任何一段启动期安全守门直接失败（`security.require-redis` / `require-cache` / `require-limiter` / `require-lock` 四项默认 `true`，防止"看起来在跑、实际上会话/限流全部旁路"的静默降级）。

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
                url: jdbc:mysql://127.0.0.1:3306/sure_auth_iam?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
                username: ${IAM_DB_USERNAME}
                password: ${IAM_DB_PASSWORD}
        # ② Redis（redis-route 接管；会话/缓存/限流/锁/SSE 共用这一套）
        redis:
          route:
            enable: true
            default-source: default
            sources:
              default:
                mode: standalone            # standalone 单机 / sentinel 哨兵 / cluster 集群
                host: 127.0.0.1
                port: 6379
                database: 0
        # ③ 分布式锁（连上面同一套 Redis，无需额外地址）
        lock:
          redis:
            route:
              enable: true
        # ④ 限流（必须抄入 csrf 与 captcha 两条规则：两个匿名端点，不限流会被刷爆 Redis 会话）
        limiter:
          redis:
            smart:
              enable: true
              me: ${io.github.surezzzzzz.sdk.auth.iam.server.me}
              mode: interceptor
              management:
                enable-default-exception-handler: false   # 429 出口统一归 IAM 异常处理
              interceptor:
                rules:
                  - path-pattern: /iam/web/auth/csrf
                    method: GET
                    resource-code: iam-web-csrf
                    key-strategy: ip
                    algorithm: sliding
                    fallback: allow
                    limits:
                      - count: 60
                        window: 1
                        unit: MINUTES
                  - path-pattern: /iam/web/auth/captcha
                    method: GET
                    resource-code: iam-web-captcha
                    key-strategy: ip
                    algorithm: sliding
                    fallback: allow
                    limits:
                      - count: 30
                        window: 1
                        unit: MINUTES
        # ⑤ 二级缓存（trusted-packages 两行一个都不能少：授权缓存条目是 SAS OAuth2Authorization 对象）
        cache:
          key-prefix: sure-auth-iam
          me: ${io.github.surezzzzzz.sdk.auth.iam.server.me}
          l1:
            enabled: true
            expire-seconds: 10
            max-size: 10000
          l2:
            enabled: true
            expire-seconds: 3600
            key-format: "{keyPrefix}:{me}:{cacheName}::{key}"
          consistency:
            mode: strong
          pubsub:
            mode: routed
          route:
            scan-enabled: false
          serializer:
            trusted-packages:
              - java.lang
              - java.time
              - java.util
              - io.github.surezzzzzz.sdk.auth.iam.server
              - org.springframework.security.oauth2.server.authorization
        # ⑥ IAM 本体
        auth:
          iam:
            server:
              issuer: https://iam.example.com        # Token 签发者，必须改为对外可达地址
              me: my-iam-server                      # Redis 命名空间标识；多套服务共用 Redis 时靠它隔离
              token:
                format: jwt                           # jwt 或 jwe
                key-id: sure-auth-iam-2026
                public-key: ${IAM_TOKEN_PUBLIC_KEY}   # RSA 公钥 PEM
                private-key: ${IAM_TOKEN_PRIVATE_KEY} # RSA 私钥 PEM
                # encryption-key: ${IAM_TOKEN_ENCRYPTION_KEY}  # 仅 format=jwe 时必填
              bootstrap:
                username: admin                       # 启动引导创建的管理员
```

上面只需按环境改这几处：①数据库地址账号密码（3 个占位符）②Redis 地址（`host` / `port`）③`issuer`（对外可达地址）④RSA 密钥对——两种填法任选：PEM 文件（4.3 生成的两个文件放进宿主工程 `src/main/resources/keys/`，照测试工程写 `classpath:keys/public.pem`）或环境变量注入 PEM 内容（保持上面的 `${...}` 写法）；`jwe` 模式再解开 `encryption-key` 注释。四件配套组件的完整参数说明见 [配置与多实例部署](server/simple-iam-server-starter/docs/领域文档/配置与多实例部署.md) 的「配套组件配置」。

### 4.5 首次启动与初始密码

启动后 `BootstrapService` 幂等初始化内置角色 / 权限 / 管理员账号（只增不减，不覆盖既有数据）：

- `bootstrap.username`（默认 `admin`）账号**不存在时**，按当前密码策略生成随机临时密码、**仅在本次启动日志中输出一次**并创建内置管理员；账号已存在时不重置、不再输出
- 引导同时注册内置 IAM 管理台应用与默认根部门，并为 `iam_admin` 角色种入全量授权规则

```text
# 首次启动日志中抓取（只有这一次机会；用 grep 直接过滤）：
#   grep "管理员账号引导完成" <启动日志>
管理员账号引导完成：username=admin, id=xxxx, initialPassword=xxxxx
```

该临时密码带 `must_change_password` 标记，首登强制改密（改密成功踢其他终端）。**初始密码遗失且库不可人工改动时**，用部署级恢复码：配置 `bootstrap.recovery-code` + `bootstrap.recovery-password` 重启，启动期消费恢复码重置管理员密码并全端吊销其会话，不经过任何 HTTP 端点。

### 4.6 冒烟验收

四条命令依次执行，全部按预期返回即部署成功。把 `https://iam.example.com` 换成你的地址、`<临时密码>` 换成 4.5 抓到的密码：

```bash
# 1. 登录方式列表——期望返回 JSON 数组（至少 local-password 一项）
curl -s https://iam.example.com/iam/web/auth/providers

# 2. 取 CSRF 并保存会话 cookie——期望返回 JSON（内含 token 字段）
curl -c cookies.txt -s https://iam.example.com/iam/web/auth/csrf

# 3. 登录——把第 2 步返回里的 token 值填进 X-CSRF-TOKEN
curl -b cookies.txt -H "X-CSRF-TOKEN: <第2步返回的token>" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<临时密码>"}' \
  https://iam.example.com/iam/web/auth/login
# 期望 200 + "mustChangePassword":true（首次登录必然如此，登录页会引导改密）

# 4. OIDC discovery——期望返回 issuer 等端点地址的 JSON
curl -s https://iam.example.com/.well-known/openid-configuration
```

常见翻车对照：第 2 步返回 429 是限流（IP 每分钟 60 次上限，等一分钟）；第 3 步 403 且**空响应体**是 CSRF 没带对（token 必须来自第 2 步、cookie 必须用同一个 `cookies.txt`）；401 带 `captchaRequired:true` 是密码错太多次（补验证码或等锁定过期）。

全量配置参考（token / session / login / captcha / password / cleanup / bootstrap / admin.theme / external-identity 九组）见 [配置与多实例部署](server/simple-iam-server-starter/docs/领域文档/配置与多实例部署.md)。

## 5. 前端部署（门户形态）

> **发布现状**：三个前端仓（login-web / admin-web / portal-web）与 IAM Server 1.0.0 对齐，**以门户仓为发布锚点**（首次发布同版本 Git tag `v1.0.0`）；npm 包形态的接入方式当前不适用，本章按源码仓构建部署。

### 5.1 三个前端仓

| 仓 | 地址 | 承载 | 路由 | 构建产物 |
|---|---|---|---|---|
| simple-iam-login-web | https://github.com/Sure-Zzzzzz/simple-iam-login-web | 登录页 `/login`、授权确认页 `/consent`、强制改密页 `/change-password` | nginx `/` | dist |
| simple-unified-application-portal-web | https://github.com/Sure-Zzzzzz/simple-unified-application-portal-web | 统一应用门户壳：应用入口、主题、站内信、qiankun 微前端挂载 | nginx `/app/` | dist（vite base `/app/`） |
| simple-iam-admin-web | https://github.com/Sure-Zzzzzz/simple-iam-admin-web | IAM 管理台（qiankun 子应用）：用户、组织、角色、可信应用、站内信管理 | nginx `/app/iam/`，由门户挂载 | dist |

主题契约独立成仓：[simple-iam-theme-contract](https://github.com/Sure-Zzzzzz/simple-iam-theme-contract)（npm 包 `@sure-zzzzzz/simple-iam-theme-contract`，已发布 1.0.1）。门户是主题状态的唯一管理方；业务微前端只消费 IAM 传入的只读主题快照，不自行保存主题偏好。

各仓构建（Node.js 22+、pnpm 9.15.4）：

```bash
npx pnpm@9.15.4 install
npx pnpm@9.15.4 run build
```

权威 API 契约由 IAM Server 仓 `sdk/auth/iam/server/contract/` 维护（五份 OpenAPI：login-web / portal-web / admin-web / 开放 API / 资源验证）；各前端 release 必须声明兼容的 Server 与 Contract 版本范围。

### 5.2 nginx 统一入口

三前端静态资源 + 后端反代，一个 server 块四个路由区。整段可抄，改三处：`upstream` 里的后端实例地址、三个 `alias` 的产物目录（5.1 各仓 build 出的 `dist/` 内容分别放到 `/www/login/`、`/www/portal/`、`/www/admin/`）：

```nginx
# http 块内：静态缓存策略（HTML 不缓存、带 hash 的静态资源长缓存）
map $uri $static_cache {
    ~*\.html$ "no-cache";
    ~*\.(js|css|png|svg|ico|woff2?)$ "public, max-age=31536000, immutable";
    default "no-cache";
}

upstream iam_cluster {
    server 10.0.0.11:8180;   # IAM Server 实例 A
    server 10.0.0.12:8180;   # IAM Server 实例 B（多实例时）
}

server {
    listen 80;

    # 后端 API：/iam/** 与 /oauth2/** 反代到 IAM Server
    location /iam/ {
        proxy_pass http://iam_cluster;
        proxy_set_header Host $http_host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;          # SSE 站内信推送禁缓冲
        proxy_cache off;
        proxy_read_timeout 3600s;     # SSE 长连接读超时拉长
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }
    location /oauth2/ {
        proxy_pass http://iam_cluster;
        proxy_set_header Host $http_host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # IAM 管理台（qiankun 子应用）：SPA 回退到门户壳，由门户按 /app/iam 前缀挂载
    location /app/iam/ {
        alias /www/admin/;
        try_files $uri /app/index.html;
        add_header Cache-Control $static_cache;
    }
    # 统一应用门户
    location /app/ {
        alias /www/portal/;
        try_files $uri $uri/ /app/index.html;
        add_header Cache-Control $static_cache;
    }
    # 登录页
    location / {
        alias /www/login/;
        try_files $uri $uri/ /index.html;
        add_header Cache-Control $static_cache;
    }
}
```

三个要点：

- **`/app/iam/` 的 SPA 回退指向门户 `/app/index.html`**，不是 admin 自己的 index.html——子应用路由刷新必须回到门户壳内，否则侧边栏丢失；qiankun 的 entry 因此必须显式配 `/app/iam/index.html`（IAM 引导内置应用已按此配置）
- **`Host` 用 `$http_host` 不用 `$host`**——后者不带端口，Tomcat sendRedirect 按 80 绝对化，OIDC 回跳会丢端口
- **SSE 禁缓冲**——`/iam/` 反代必须 `proxy_buffering off`，否则站内信事件帧被缓冲、跨实例推送假失败

### 5.3 路由边界与登录序

网关 / 反向代理保持的路由边界（多实例部署同一张表）：

| 路径 | 责任方 |
|---|---|
| `/login`、`/consent`、`/change-password` | login-web 静态 |
| `/app/**` | portal-web 静态（含 `/app/iam/` admin-web） |
| `/oauth2/**`、`/.well-known/**`、`/userinfo` | IAM Server |
| `/iam/web/**`、`/iam/admin/**`、`/iam/resource/**`、`/iam/api/**` | IAM Server |

浏览器登录序（前端接入顺序，login-web 视角）：

1. 启动时 `GET /iam/web/branding` 取品牌、`GET /iam/web/auth/csrf` 取 CSRF token
2. `POST /iam/web/auth/login` 登录（响应带 `mustChangePassword`；`true` 时强制跳改密页）
3. `GET /iam/web/auth/me` 恢复当前用户；`POST /iam/web/auth/logout` 登出
4. 未认证访问 `/oauth2/authorize` 时，Server 302 到 `/login?redirect=...`；登录后按 `redirect` 回原授权请求
5. Consent 页 `GET /iam/web/oauth2/consent-info?state=...` 供数渲染，再按 OAuth2 标准提交
6. 门户壳 `GET /iam/web/portal/accessible-applications` 取当前用户可访问应用及菜单

IAM Server 不托管任何前端构建产物。前端本地开发（端口 5174 login / 5175 admin / 5176 portal）与代理说明见各前端仓 README。

## 6. 登录适配器使用

适配器是独立 starter：**引依赖即装配，无 enable 开关**；不引入则该登录方式不出现在登录页。三个适配器都实现 `simple-iam-core` 的 SPI，协议细节全部封闭在适配器内，server 侧零协议依赖。

### 6.1 LDAP 凭证型（simple-iam-ldap-adapter-starter）

用户在 IAM 登录页输域账号密码，密码由上游 LDAP bind 验证；provider 编码 `ldap-password`。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-iam-server-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-iam-ldap-adapter-starter:1.0.0'
}
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          iam:
            adapter:
              login:
                ldap:
                  url: ldap://ldap.example.com/dc=example,dc=com   # base DN 写在 URL 路径
                  manager-dn: cn=readonly,dc=example,dc=com        # 只读检索账号；匿名搜索可留空
                  manager-password: ${LDAP_MANAGER_PASSWORD}
                  user-search-base: ou=people
                  user-search-filter: (uid={0})                     # AD 改 (sAMAccountName={0})
                  username-attribute: uid
                  display-name-attribute: cn
                  email-attribute: mail
                  external-id-attribute:                            # 留空用用户条目 DN
```

登录行为：manager 搜索用户 DN → 用户 DN + 密码 bind → bind 成功读属性构造 `ExternalIdentity` 交回 server 做账号归一。LDAP 密码错误与本地登录失败计数用独立 Redis 键，互不污染；目录不可达返回 503（`EXTERNAL_PROVIDER_UNAVAILABLE`）不计失败。`url` 缺失启动即失败，不静默降级。

### 6.2 OIDC 跳转型（simple-iam-oidc-adapter-starter）

对接企业 IdP（Keycloak、CAS with OIDC、Authing 等）做 SSO；provider 编码 `oidc`。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-iam-server-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-iam-oidc-adapter-starter:1.0.0'
}
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          iam:
            server:
              external-identity:
                callback-base-url: https://iam.example.com   # 反向代理场景必须显式配
            adapter:
              login:
                oidc:
                  issuer: https://sso.example.com
                  authorization-uri: https://sso.example.com/oauth2/authorize
                  token-uri: https://sso.example.com/oauth2/token
                  jwk-set-uri: https://sso.example.com/oauth2/jwks
                  client-id: iam-client
                  client-secret: ${OIDC_CLIENT_SECRET}
                  scopes: openid,profile,email
```

登录行为：`GET /iam/web/auth/authorize/oidc` 拿授权地址 → 上游登录 → 回调 `GET /iam/web/auth/callback/oidc`（state 一次性消费）→ code 换 ID token + JWKS 验签（iss/aud/exp/nonce）→ 以 `sub` 为 externalId 做账号归一。任一必填项缺失启动即失败并列出缺失项。

### 6.3 图片验证码（simple-iam-captcha-adapter-starter）

`simple-iam-server-starter` **已传递引入**，无需单独引入。把通用 `simple-captcha-starter` 桥接为 IAM 的 `CaptchaProvider` SPI；验证码参数（TTL、字符长度）经通用模块配置。

触发策略由 IAM 本体控制（无需引依赖）：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          iam:
            server:
              captcha:
                enabled: true      # 总开关
                threshold: 2       # 登录失败达 2 次后要求验证码；锁定兜底由 login.max-attempts 控制
```

验证码校验先于凭据校验，验证码失败不计密码失败次数。要换滑块等其他形态：直接实现 `simple-iam-core` 的 `CaptchaProvider` 接口并移除本适配器。

### 6.4 外部身份归一（三步判定）

外部认证成功后的账号归一对三种适配器一致：

1. 已绑定该 `identitySource + externalId` → 直接复用本地账号
2. 未绑定但存在**同名**本地账号 → **拒绝，绝不自动并号**（防接管；提示管理员用 `POST /iam/admin/users/{userId}/external-identity` 预绑定）
3. 无同名 → 按 `external-identity.provisioning-mode` 处置：`jit`（首次登录自动创建本地账号，本地密码为随机不可登录值）或 `pre-bound-only`（仅预绑定身份可登录）

机制细节（登录时序图、失败锁定维度、会话生命周期、登出联动吊销）见 [登录认证与会话](server/simple-iam-server-starter/docs/领域文档/登录认证与会话.md)。

## 7. 业务应用接入（挂门户）

业务系统以可信应用身份接入统一应用门户，四步（每步一个端点，全部走管理台或管理 API）：

| # | 步骤 | 端点 | 说明 |
|---|---|---|---|
| 1 | 建可信应用 | `POST /iam/admin/trusted-applications`（必须带初始 OAuth 客户端） | `entry` 与 `routePrefix` 决定门户挂载位置 |
| 2 | 申报权限清单 | `PUT /iam/admin/trusted-applications/{applicationId}/permission-manifest` | 该应用权限码空间的**事实源**；全量替换语义 |
| 3 | 配角色应用授权规则 | `PUT /iam/admin/roles/{roleId}/authorization-rules/{applicationId}` | 角色 × 应用的码集合，投影的**计算源**；变更即触发投影重算 |
| 4 | 给用户准入 | `PUT /iam/admin/users/{userId}/application-authorizations/{applicationId}`（`admitted=true`） | 决定用户 Token 中的投影内容与门户可见性 |

**第 2 步是业务方唯一需要"发明"的东西**——把应用里所有受控权限点一次列全：

```json
{
  "roles":          ["order_manager", "order_viewer"],
  "pagePermissions": ["order:list:page", "order:detail:page"],
  "apiPermissions":  ["order:read:api", "order:write:api"],
  "dataResources": [
    {"resource": "order", "actions": ["read", "write"], "dimensions": ["region"]}
  ]
}
```

第 3、4 步的请求体直接抄（码全部来自第 2 步申报过的，不能出现清单外的码）：

```json
// 第 3 步 PUT /iam/admin/roles/{roleId}/authorization-rules/{applicationId}
{
  "pagePermissions": ["order:list:page", "order:detail:page"],
  "apiPermissions":  ["order:read:api", "order:write:api"],
  "dataGrantTemplate": {                       // 不用 DATA 权限传 null
    "protocol": "simple-data-permission", "version": "1.0",
    "grants": [{"resource": "order", "actions": ["read"], "all": true}]
  }
}

// 第 4 步 PUT /iam/admin/users/{userId}/application-authorizations/{applicationId}
// 四个字段全部必填；权限内容按角色规则自动投影，这里通常传空数组
{
  "admitted": true,
  "roles": [],
  "pagePermissions": [],
  "apiPermissions": []
}
```

易踩的坑：

- **清单 PUT 是全量替换不是增量合并**——每次上报必须携带当前全量码表，漏写的码立刻从码空间消失
- **四字段必填可为空数组**（`null` 拒）；不用 DATA 权限传 `"dataResources": []`
- **码前缀自治但不校验**——IAM 不校验码前缀与 applicationCode 一致，两个应用申报同名码全局命名空间就混了
- **DATA 资源先在清单申报**，角色规则模板里才有得选（grant 的 resource/action/dimension 必须 ⊆ 清单申报，越界即拒）

三层结构与投影语义（清单 → 规则 → 投影、五类重算触发事件、平台管理员特权、手工授权边界）见 [权限与授权投影](server/simple-iam-server-starter/docs/领域文档/权限与授权投影.md)；OAuth 侧接入规则（redirect_uri 精确匹配、scope 逐个注册、refresh 默认不签发）见 [OAuth2与令牌验证](server/simple-iam-server-starter/docs/领域文档/OAuth2与令牌验证.md) 的「OAuth2 / OIDC 接入规则」。

**验证**：用户登录门户核对应用可见与菜单；解 Access Token payload 看 `iam_authorization` 投影，或调 `POST /iam/resource/tokens/verify` 看返回的 `iam_authorization`（同一份投影）。

## 8. 资源服务接入 IAM 身份

业务资源服务要校验 IAM 人员身份（持 IAM Access Token 调 API 的场景）：

### 8.1 组合依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.1.1'      // 公共资源层
    implementation 'io.github.sure-zzzzzz:simple-iam-resource-server-starter:1.0.0'  // IAM Provider
}
```

公共资源层负责统一 Bearer 入口、kid 来源路由、精确 API 权限与 DATA 授权边界；本 Starter 只注册 IAM Provider 适配器。

### 8.2 配置

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
                  - /api/**
          iam:
            resource:
              server:
                verification-endpoint: https://iam.example.com/iam/resource/tokens/verify
                client-id: <资源验证客户端 ID>          # IAM 管理台「验证客户端」一次性取得
                client-secret: ${IAM_RESOURCE_CLIENT_SECRET}
```

验证客户端在 IAM 管理台建：`POST /iam/admin/trusted-applications/{id}/resource-verification-clients`（**原始 secret 仅创建时返回一次**，支持密钥轮换）。

### 8.3 受控验证语义

每个 IAM Bearer 请求：公共层按 token 外层 kid 路由到 IAM Provider → Starter 调 verify 端点（独立 Basic 认证）→ IAM 五重校验（token 存活 / 应用归属 / 会话活跃 / 用户 active / 授权投影有效）→ 公共层再校验应用准入与精确 API 权限。IAM 不可用、协议异常、认证失败**一律 401 不降级**；不缓存验证结果、不接受 Cookie。

### 8.4 业务接口声明权限

```java
@RestController
public class OrderController {

    @GetMapping("/api/orders")
    @RequireApiPermission("order.read")
    public List<OrderResponse> list() {
        return orderApplicationService.list();
    }
}
```

业务接口不读角色、scope 或 URL 决定权限，只声明稳定精确的 API permission；权限码来自第 7 章的应用清单申报。

### 8.5 审计挂件（可选）

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-iam-resource-audit-listener-starter:1.0.0'
}
```

实现 `IamResourceAuditHandler` 接口即接入（监听器自动注册）；订阅公共层 `ResourceAccessEvent`、按 IAM 来源过滤、`@Async` 分发。默认日志 Handler 关闭，开启：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          iam:
            resource:
              listener:
                handler:
                  log:
                    enabled: true
```

细节见 [simple-iam-resource-server-starter](resource/simple-iam-resource-server-starter/README.md) 与 [simple-iam-resource-audit-listener-starter](../../audit/iam/simple-iam-resource-audit-listener-starter/README.md) 各自 README。

## 9. 开放 API（AKSK 调 IAM）

`/iam/api/**` 供外部业务系统用 **AKSK 凭证**做组织与人员同步（用户 / 部门族端点），Bearer 鉴权 + 端点级 API 码 + DATA 范围双闸，**默认失败关闭**（未配置即落会话链拒绝）。

接入共六步，分布在两侧：

| # | 配置点 | 归属侧 | 漏配症状 |
|---|---|---|---|
| 1 | 创建 AKSK 客户端（AKP / AKU） | AKSK | 换不出 token |
| 2 | 应用授权 admitted=true（applicationCode=iam） | AKSK | 换不出 token |
| 3 | API 码 `iam:user:api` / `iam:department:api` | AKSK | 403 |
| 4 | DATA grants `iam:user`（all 或 departmentId 受限） | AKSK | 403（失败关闭） |
| 5 | 资源层 protected-paths 覆盖 `/iam/api/**` | IAM 宿主 | 403 |
| 6 | introspect 回源 AKSK Server + 验证客户端凭据 | IAM 宿主 | 401 |

IAM 宿主侧两步配置：

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
                  - /iam/api/**
          aksk:
            resource:
              server:
                introspect:
                  endpoint: https://aksk.example.com/oauth2/introspect
                  client-id: ${AKSK_INTROSPECT_CLIENT_ID}
                  client-secret: ${AKSK_INTROSPECT_CLIENT_SECRET}
                  local-cache:
                    enabled: true
                    fallback:
                      enabled: false    # 用户与组织管理属高敏：禁 stale fallback
```

注意：凭据走 yml `${ENV:}` 占位符（OS 环境变量 relaxed binding 映射不到 `client-id`）；`surezzzzzz` 前缀 6 个 z（少 z 适配器静默不装配、症状 401）。接入契约见 [开放 API 契约](server/contract/openapi/simple-iam-open-api.openapi.yaml)。

## 10. 多实例与运维

### 10.1 多实例部署

会话（spring-session）、授权状态（MySQL + smart-cache）、授权交易（Redis）、SSE 广播（Pub/Sub）、限流 / 锁 / 缓存全部在共享存储——**无需粘性路由**，任意实例可服务任意请求。

多实例前提：

- 全部实例共享同一 MySQL 与同一 Redis、配置同一 `me`（Redis key 隔离段）
- JWT 签名密钥与 `issuer` 一致（issuer 配 LB 对外地址）
- redis-route 版本 ≥ 1.2.0；OIDC 场景 `callback-base-url` 配负载均衡地址

### 10.2 审计事件流

引入 `simple-iam-server-audit-listener-starter` 订阅四族事件（Token / 认证 / 会话 / 管理面），实现 `IamAuditHandler` SPI 落库或告警；事务提交后投递（`AFTER_COMMIT`），记录不含 token 原文。默认日志 Handler `handler.log.enabled=true` 开着，可关。

### 10.3 过期清理

`cleanup.*` 配置组：每天凌晨 2 点清理 refresh 族表 + 授权表三路过期行（refresh / access / code），分批删除（每批独立事务），多实例分布式锁互斥。

### 10.4 敏感信息边界

私钥 / 加密密钥 / 恢复码 / 数据库与 LDAP 凭据 / OIDC client-secret 一律经环境变量或密钥管理系统注入；日志只出现 key-id 不出现密钥内容；审计记录不含 token 原文。

完整运维口径见 [配置与多实例部署](server/simple-iam-server-starter/docs/领域文档/配置与多实例部署.md) 的「多实例部署」「启动引导与部署级恢复码」「审计事件流」。

## 11. 常见问题

| 症状 | 根因 | 处置 |
|---|---|---|
| `POST /iam/web/auth/login` 403 且**空响应体** | Spring CSRF 拦截（不是密码错） | 先 `GET /iam/web/auth/csrf` 拿 token，带 `X-CSRF-TOKEN` 头 + 同一 cookie jar 重发 |
| 登录 401 带 `captchaRequired=true` | 失败达 `captcha.threshold` 触发人机验证 | 补 `captchaId` / `captchaAnswer`（`GET /iam/web/auth/captcha` 出题） |
| 登录 429 带 `Retry-After` | `/iam/web/auth/csrf` IP 限流触发 | 等窗口；NAT 多人共享出口 IP 时放宽阈值 |
| Web / 管理端点 403 `code=AUTH_009` | 首登 / 被重置后的强制改密拦截期 | 先走 `PUT /iam/web/auth/password` 改密（白名单端点放行） |
| 改密 400 `AUTH_010` | 外部身份源（LDAP / OIDC）用户 | 密码由外部系统管理，本端拒绝改密与重置 |
| 改密 400 `AUTH_011` | 新密码违反策略 | 按默认策略补齐大小写 / 数字 / 特殊字符 / 8-64 位 |
| 撤销角色 / 删用户 409 | 最后管理员保护 | 内置 `iam_admin` 不可删；最后一个可用管理员不可删 / 禁用 / 摘角色 |
| 业务服务 401 且无换 provider 迹象 | `surezzzzzz` 前缀少 z，适配器静默不装配 | 核对配置前缀 6 个 z |
| OIDC 回跳地址丢端口 | 反代 `Host` 头不带端口（`$host`） | nginx 用 `$http_host`；或显式配 `callback-base-url` |
| 站内信 SSE 收不到推送 | 反代缓冲了事件帧 | `proxy_buffering off` + 拉长 `proxy_read_timeout` |
| 资源服务验证 401 | 会话已吊销 / 用户禁用 / 授权投影失效 | IAM 受控验证五重校验任一不过即 401（token 没到期也会拒——`sid` 绑定会话） |
| refresh token 报 invalid_grant | 旧值二次使用（重放），整族已吊销 | 正常防线；客户端重走授权码流程 |
| 1.0.0 有没有 introspection / revocation 端点 | 无（SAS 0.4.1 未提供） | token 失效以会话联动吊销 + 短有效期兜底；资源端用 verify 端点 |

## 12. 文档地图

| 文档 | 内容 |
|---|---|
| [simple-iam-server-starter README](server/simple-iam-server-starter/README.md) | 模块契约权威：特性 / 依赖基线 / **HTTP 端点总览** / 安全过滤链 / 错误处理约定 / 交付边界 |
| [登录认证与会话](server/simple-iam-server-starter/docs/领域文档/登录认证与会话.md) | 本地登录 / 验证码与锁定 / LDAP / OIDC / 身份归一 / 会话生命周期 / 登出吊销 |
| [OAuth2与令牌验证](server/simple-iam-server-starter/docs/领域文档/OAuth2与令牌验证.md) | 授权码 + PKCE + Consent 全流程 / claims 注入 / Refresh 防线 / verify 端点 / 接入规则 |
| [权限与授权投影](server/simple-iam-server-starter/docs/领域文档/权限与授权投影.md) | 清单 → 规则 → 投影三层 / 业务方上报指引 / 平台管理员特权 / DATA 消费 |
| [管理台与站内信](server/simple-iam-server-starter/docs/领域文档/管理台与站内信.md) | 管理面操作模式 / 仪表盘 / SSE 推送 |
| [配置与多实例部署](server/simple-iam-server-starter/docs/领域文档/配置与多实例部署.md) | **全量配置参考** / 配套组件接线 / 多实例 / bootstrap 恢复码 / 审计事件流 |
| [schema.sql](server/simple-iam-server-starter/docs/schema.sql) | 全新环境数据库初始化（26 张表） |
| [contract README](server/contract/README.md) | 五份 OpenAPI 契约入口 |
| [simple-iam-core README](simple-iam-core/README.md) | 协议 SPI：外部身份源 / 人机验证 / 路由键 |
| [IAM 与 AKSK 协作接入](../README.IAM-AKSK协作.md) | 双身份组合边界与操作序 |
| [Simple AKSK 用户手册](../aksk/USER_MANUAL.md) | AKSK 3.x 手册 |
