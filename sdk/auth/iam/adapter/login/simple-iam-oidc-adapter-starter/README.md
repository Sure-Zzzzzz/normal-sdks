# simple-iam-oidc-adapter-starter

面向业务方的 IAM OIDC 登录适配器 Starter。业务方引入本模块并配置企业 IdP（Keycloak、CAS with OIDC、Authing 等）的端点后，
`simple-iam-server-starter` 的登录页即可提供 `oidc` 跳转型 SSO 登录。

本模块实现 `simple-iam-core` 的 `ExternalBrowserLoginProvider` SPI：授权码模式（response_type=code）完整流程——拼接授权地址、code
换 ID token、JWKS 验签并校验 iss/aud/exp/nonce；协议细节全部封闭在适配器内，server 侧零 OIDC 依赖。

## 依赖

Gradle：

```gradle
dependencies {
    // 登录路由、账号归一、会话管理由 server-starter 提供，需一并引入。
    implementation 'io.github.sure-zzzzzz:simple-iam-server-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-iam-oidc-adapter-starter:1.0.0'
}
```

Maven：

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>simple-iam-oidc-adapter-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

`spring-security-oauth2-jose`（含 Nimbus）与 `spring-web` 已由本模块引入并封闭在适配器内部，业务无需额外声明。不引入
`spring-security-oauth2-client`：其 filter chain 与 IAM 既有的授权服务器 filter chain 叠加易出顺序冲突，本适配器仅按需实现授权码三步。

## 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          iam:
            server:
              external-identity:
                # 反向代理场景必须显式配置，浏览器可访问的 IAM 地址（不含路径）
                callback-base-url: https://iam.example.com
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

引依赖即装配，无 `enable` 开关：宿主不引入本 starter 即不装配（登录页不出现 oidc 方式），引入后装配即生效。仅当宿主同时引入
IAM server 且 SPI 在 classpath 时激活。

| 配置项              | 默认值                 | 说明                                       |
|---------------------|------------------------|--------------------------------------------|
| `issuer`            | 无（必填）             | ID token `iss` 校验基准                    |
| `authorization-uri` | 无（必填）             | 授权端点                                   |
| `token-uri`         | 无（必填）             | 令牌端点                                   |
| `jwk-set-uri`       | 无（必填）             | JWKS 端点，ID token 验签公钥来源           |
| `client-id`         | 无（必填）             | OIDC 客户端 ID，同时作为 `aud` 校验值      |
| `client-secret`     | 无（必填）             | 客户端密钥，经部署环境管理，不能写入版本库 |
| `scopes`            | `openid,profile,email` | 授权 scope                                 |

已引入依赖但任一必填项缺失时应用启动即失败并列出缺失项，不会静默降级。

## 登录行为

登录页 providers 列表出现 `oidc`（type=sso）后，浏览器端到端流程：

1. 前端访问 `GET /iam/web/auth/authorize/oidc`，server 生成 state 存 session，适配器生成 nonce 并拼接授权地址（含
   `redirect_uri` = IAM 回调地址）；
2. 用户在 IdP 完成登录，IdP 携带 `code`、`state` 回调 `GET /iam/web/auth/callback/oidc`；
3. server 校验 state（一次性）后，适配器以 code + `client_secret` Basic 认换取令牌，`NimbusJwtDecoder` 经 JWKS 对 ID token
   验签并校验 `iss`/`aud`/`exp` 与 `nonce`；
4. 认证通过后以 `sub` 为 externalId 交回 server 做账号归一（JIT 开号或预绑定关联，策略见 server-starter
   领域文档《登录认证与会话》的「外部身份归一」一节），建立 IAM 会话并 302 至登录完成目标（发起时暂存的 redirect，缺省门户首页）；失败
   302 回登录页并附 `error` 查询参数（错误码，账号锁定降级为 `login-failed`）。

`preferred_username` 作为本地账号名建议值，`name`/`email` 回填显示名与邮箱；外部账号的本地密码登录始终被 server 拒绝。

## 异常映射

| IdP 侧情形                                                                                                 | 错误码                                    | HTTP                      | 失败计数 |
|------------------------------------------------------------------------------------------------------------|-------------------------------------------|---------------------------|----------|
| 回调缺 code/state、state 不符或已使用、IdP 返回 error、授权码被 4xx 拒绝、ID token 验签/iss/aud/nonce 不符 | `BIZ_004` `EXTERNAL_CALLBACK_INVALID`     | 302 回登录页（`?error=`） | 不计     |
| 令牌端点 5xx、IdP 不可达                                                                                   | `BIZ_003` `EXTERNAL_PROVIDER_UNAVAILABLE` | 302 回登录页（`?error=`） | 不计     |

异常为 `simple-iam-core` 的 `IamProtocolException`；日志与错误响应不输出 token、授权码或 IdP 端点细节。

## 测试

模块测试分五层：

- `SimpleIamOidcAdapterAutoConfigurationTest`：条件装配与配置完整性快速失败，不依赖基础设施；
- `OidcBrowserLoginProviderTest`：MockRestServiceServer + 本地 RSA 真签名 ID token，覆盖授权地址、换取身份、state
  重放、4xx/5xx/不可达、nonce 与签名篡改；
- `RedisPendingStateStoreTest`：pending state 的 Redis 存取（分布式部署下的跨实例 state）；
- `IamOidcLoginEndToEndTest`：真实 Keycloak + MySQL + Redis 的授权码全流程 E2E；
- `IamOidcCrossInstanceStateTest`：双实例下 state 发起 / 回调跨实例完成（Redis 共享 pending state）。

## Spring Boot 兼容性

基于 Spring Boot 2.7.9 / Spring Security 5.8.2 / javax 基线开发，与 `simple-iam-server-starter` 版本矩阵一致。
