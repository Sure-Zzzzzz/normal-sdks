# 认证与授权-IAM

统一身份认证与授权服务：本地账号、浏览器登录会话、OAuth 2.1 / OIDC、RBAC 三层授权投影、可信应用与统一应用门户。管**人员身份**，与管服务身份的 [AKSK](认证与授权-AKSK.md) 独立部署、可协作。

从零部署到接入的完整导引见 [IAM 用户手册](../../sdk/auth/iam/USER_MANUAL.md)；各模块 README 是契约权威。

## 分层结构（core → server → adapter → resource → 审计）

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-iam-core](../../sdk/auth/iam/simple-iam-core) | 1.0.0 | — | 协议契约基座（登录 SPI/人机验证 SPI/路由键常量） | [README](../../sdk/auth/iam/simple-iam-core/README.md) |
| [simple-iam-server-core](../../sdk/auth/iam/server/simple-iam-server-core) | 1.3.2 | — | Server 域契约（审计事件/错误码/常量/受委托角色开放 API） | [README](../../sdk/auth/iam/server/simple-iam-server-core/README.md) |
| [simple-iam-server-starter](../../sdk/auth/iam/server/simple-iam-server-starter) | 1.3.2 | — | IAM Server 应用模块（OAuth 2.1+PKCE/会话/RBAC 投影/门户供数/站内信/开放 API/受委托角色） | [README](../../sdk/auth/iam/server/simple-iam-server-starter/README.md) |
| [simple-iam-ldap-adapter-starter](../../sdk/auth/iam/adapter/login/simple-iam-ldap-adapter-starter) | 1.0.0 | — | LDAP 凭证型登录适配器（引依赖即装配） | [README](../../sdk/auth/iam/adapter/login/simple-iam-ldap-adapter-starter/README.md) |
| [simple-iam-oidc-adapter-starter](../../sdk/auth/iam/adapter/login/simple-iam-oidc-adapter-starter) | 1.0.0 | — | OIDC 跳转型登录适配器（企业 IdP 单点登录） | [README](../../sdk/auth/iam/adapter/login/simple-iam-oidc-adapter-starter/README.md) |
| [simple-iam-captcha-adapter-starter](../../sdk/auth/iam/adapter/captcha/simple-iam-captcha-adapter-starter) | 1.0.0 | — | 图片验证码适配器（server-starter 已传递引入） | [README](../../sdk/auth/iam/adapter/captcha/simple-iam-captcha-adapter-starter/README.md) |
| [simple-iam-resource-core](../../sdk/auth/iam/resource/simple-iam-resource-core) | 1.0.0 | — | IAM 人员认证结果模型 | [README](../../sdk/auth/iam/resource/simple-iam-resource-core/README.md) |
| [simple-iam-resource-server-starter](../../sdk/auth/iam/resource/simple-iam-resource-server-starter) | 1.0.0 | 1.0.0 | 资源服务 IAM Provider（受控验证回源，配 verification client） | [README](../../sdk/auth/iam/resource/simple-iam-resource-server-starter/README.md) |
| [simple-iam-server-audit-listener-starter](../../sdk/audit/iam/simple-iam-server-audit-listener-starter) | 1.0.0 | — | Server 审计挂件（Token/认证/会话/管理面四族，可选） | [README](../../sdk/audit/iam/simple-iam-server-audit-listener-starter/README.md) |
| [simple-iam-resource-audit-listener-starter](../../sdk/audit/iam/simple-iam-resource-audit-listener-starter) | 1.0.0 | 1.0.1 | 资源访问审计挂件（IAM 来源过滤，可选） | [README](../../sdk/audit/iam/simple-iam-resource-audit-listener-starter/README.md) |

## 依赖关系

- Server 本体固定 Spring Boot 2.7，仅资源侧/审计挂件/客户端走 jakarta 线
- 资源 Provider 经公共资源层挂接（见 [AKSK 篇·公共资源层](认证与授权-AKSK.md)）
- 前端三仓独立：[登录页](https://github.com/Sure-Zzzzzz/simple-iam-login-web) / [门户壳](https://github.com/Sure-Zzzzzz/simple-unified-application-portal-web) / [管理台](https://github.com/Sure-Zzzzzz/simple-iam-admin-web)；主题契约 [simple-iam-theme-contract](https://github.com/Sure-Zzzzzz/simple-iam-theme-contract)

## 版本映射（唯一事实源）

| server-starter | server-core | 说明 |
|----------------|-------------|------|
| 1.3.2 | 1.3.2 | 受委托角色开放 API（License 等可信应用经 AKP 自助维护角色与固定应用规则） |
| 1.3.1 | 1.3.1 | 条件请求头贯通（IAM → 公共契约 → Portal → HTTP），主题与协作收口 |
| 1.3.0 | 1.3.1 | subjectId 统一、手机号短信登录、Excel 导入、dashboard 门禁、审计事件 subjectId 化 |
| 1.2.x | 1.2.0 | 菜单树契约与门户折叠（2.x Web 兼容线） |
| 1.1.x | 1.1.2 | 首个门户形态 IAM（OAuth 2.1 + 投影 + 站内信） |

## 核心特性

- OAuth 2.1 授权码 + PKCE（公共客户端强制 S256）+ OIDC；Token 双模式（JWS / JWE）
- 本地账号 + LDAP / OIDC 外部身份源（SPI 适配器）；首登强制改密、渐进人机验证、失败锁定
- RBAC 三层授权投影：应用权限清单（申报制）→ 角色授权规则 → 用户授权投影（进 Access Token）
- Refresh Token 主动防线：全局一次性使用（必轮换），旧值重放整族吊销
- 统一应用门户（qiankun 微前端挂载可信应用）+ 站内信（SSE 实时推送）
- 开放 API：`/iam/api/**` 供 AKSK 凭证做组织与人员同步（API 码 + DATA 双闸，失败关闭）
- 多实例：会话/缓存/限流/锁全在共享存储，无需粘性路由
