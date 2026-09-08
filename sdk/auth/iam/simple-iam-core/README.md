# Simple IAM Core

IAM 协议核心：定义外部身份源接入 SPI、人机验证 SPI、IAM 命名空间路由键与协议错误契约，供 IAM Server 与各类适配器共用。

## 这个模块解决什么问题

IAM 登录链路要接两类外部能力——外部身份源（LDAP、OIDC 等）和人机验证（图片码、滑块等）。如果 Server 直接依赖具体实现，每接入一种登录方式就要改一次 Server。本模块把这些能力抽象为稳定 SPI 契约：适配器只面向 SPI 编程，Server 只面向 SPI 装配，两侧互不感知具体实现。

此外，IAM 签发的 JOSE 外层路由键需要稳定命名空间，供路由层识别归属。`IamRouteKeyHelper` 统一负责构造与校验。

## 核心概念

| 类型 | 含义 |
| --- | --- |
| `ExternalCredentialAuthenticator` | 凭证校验型登录 SPI（LDAP bind、远程密码源）：前端提交用户名与凭证，适配器到外部源校验。 |
| `ExternalBrowserLoginProvider` | 跳转型登录 SPI（OIDC、CAS 等）：浏览器跳外部 IdP 再回调 IAM；授权发起与回调均为 front-channel GET。 |
| `CaptchaProvider` | 人机验证 SPI：`generate` 出题、`verify` 一次性消费验题；何时要求验证码（失败计数、阈值判定）归 IAM 登录策略。 |
| `ExternalIdentity` | 认证成功返回的身份对象；`externalId` 必须是外部体系内稳定唯一标识（DN、uid、sub），不得使用显示名、邮箱等可变值。 |
| `CaptchaChallenge` | 一次验证挑战：`captchaId` + `type` + 前端可直接渲染的 `content`（image 时为 PNG data URI）。 |
| `IamRouteKeyHelper` | IAM 命名空间路由键的构造、提取与归属判定。 |
| `IamProtocolException` | 协议异常统一载体，携带 `ErrorCode`。 |

## 实现契约要点

- `providerCode` 全局唯一，两个适配器注册相同编码会导致装配失败。
- `verify` 必须一次性消费：同一挑战无论校验成败即失效，防试错爆破；挑战 TTL 归实现配置。
- 外部源不可达或故障时抛 `EXTERNAL_PROVIDER_UNAVAILABLE`，不得将不可用伪装为凭据错误；凭据错误抛 `EXTERNAL_BAD_CREDENTIALS`。
- 跳转型回调参数缺失、state/nonce 不符或换取身份失败时抛 `EXTERNAL_CALLBACK_INVALID`。
- 仅支持 back-channel 或 POST 绑定的协议（如 SAML Artifact / HTTP-POST binding）需先演进本 SPI 的回调入口形态，当前契约无法直接承载。
- keyId 校验：非空、无首尾空白、码点数不超上限、仅允许白名单字符（见 `SimpleIamCoreConstant`）。

## 谁实现、谁引用

- 适配器模块实现 SPI（如 `simple-iam-ldap-adapter-starter`、`simple-iam-oidc-adapter-starter`、`simple-iam-captcha-adapter-starter`），**不得依赖 IAM Server**。
- IAM Server 面向 SPI 装配，不感知具体实现。
- 默认图片验证码由 captcha 适配器桥接通用 captcha 模块提供；业务方需要滑块 / 行为码 / 云验证码时直接实现 `CaptchaProvider` 并移除 captcha adapter 依赖——不同实现不同引用，无让位场景。

## 依赖边界

零运行时第三方依赖（Spring 相关仅 compileOnly）；依赖边界由测试期 `IamCoreDependencyBoundaryTest` 守卫。

## 许可证

Apache License 2.0
