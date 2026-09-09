# simple-iam-ldap-adapter-starter

面向业务方的 IAM LDAP 登录适配器 Starter。业务方引入本模块并配置 LDAP 目录地址后，`simple-iam-server-starter` 的登录路由即可接受 `provider=ldap-password` 的凭证登录，登录页 `providers` 列表自动出现 LDAP 登录方式。

本模块实现 `simple-iam-core` 的 `ExternalCredentialAuthenticator` SPI：以 manager 账号搜索用户 DN，再以用户 DN + 密码执行 bind 验证；协议细节全部封闭在适配器内，server 侧零 LDAP 依赖。

## 依赖

Gradle：

```gradle
dependencies {
    // 登录路由、账号归一、会话管理由 server-starter 提供，需一并引入。
    implementation 'io.github.sure-zzzzzz:simple-iam-server-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-iam-ldap-adapter-starter:1.0.0'
}
```

Maven：

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>simple-iam-ldap-adapter-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

`spring-security-ldap` 与 `spring-ldap-core` 已由本模块引入并封闭在适配器内部，业务无需额外声明。

## 配置

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
                  url: ldap://ldap.example.com/dc=example,dc=com
                  manager-dn: cn=readonly,dc=example,dc=com
                  manager-password: ${LDAP_MANAGER_PASSWORD}
                  user-search-base: ou=people
                  user-search-filter: (uid={0})
                  username-attribute: uid
                  display-name-attribute: cn
                  email-attribute: mail
                  external-id-attribute: # 留空使用用户 DN
```

引依赖即装配，无 `enable` 开关：宿主不引入本 starter 即不装配（登录页不出现 LDAP 方式），引入后装配即生效。仅当宿主同时引入 IAM server 且 SPI 在 classpath 时激活。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `url` | 无（必填） | LDAP 地址，base DN 直接写在 URL 路径中 |
| `manager-dn` / `manager-password` | 空 | 匿名搜索不可用的目录需配置只读检索账号；留空则匿名搜索 |
| `user-search-base` | 空 | 相对 URL base DN 的搜索起点，如 `ou=people` |
| `user-search-filter` | `(uid={0})` | `{0}` 为登录用户名；Active Directory 改 `(sAMAccountName={0})` |
| `username-attribute` | `uid` | 归一映射到本地账号名的建议值；留空直接用登录名 |
| `display-name-attribute` | `cn` | JIT 开号时的显示名来源 |
| `email-attribute` | `mail` | JIT 开号时的邮箱来源 |
| `external-id-attribute` | 空 | 外部稳定 ID 来源；留空使用用户 DN |

账号、密码应通过部署环境管理，不能写入版本库。

已引入依赖但 `url` 缺失时应用启动即失败并指明配置前缀，不会静默降级。

## 登录行为

登录请求带 `provider: ldap-password` 时，server 将凭证交给本适配器：

1. 以 manager（或匿名）在 `user-search-base` 下按 `user-search-filter` 搜索用户 DN；
2. 以用户 DN + 登录密码执行 bind；bind 失败即凭据错误；
3. bind 成功后读取属性，构造 `ExternalIdentity` 交回 server 做账号归一（JIT 开号或预绑定关联，策略见 server-starter 领域文档《登录认证与会话》的「外部身份归一」一节）。

外部账号的本地密码登录始终被 server 拒绝；LDAP 密码错误与本地登录失败计数使用相互独立的 Redis 键，互不污染。

## 异常映射

| 目录侧情形 | 错误码 | HTTP | 失败计数 |
| --- | --- | --- | --- |
| 用户不存在 / bind 凭据错误 | `BIZ_002` `EXTERNAL_BAD_CREDENTIALS` | 401 | 计入 |
| 目录不可达 / 通信异常 | `BIZ_003` `EXTERNAL_PROVIDER_UNAVAILABLE` | 503 | 不计 |

异常为 `simple-iam-core` 的 `IamProtocolException`，响应体不含 LDAP 地址、DN 或底层异常细节。

## 测试

模块测试分两层：

- `SimpleIamLdapAdapterAutoConfigurationTest`：条件装配与异常映射，不依赖 LDAP 基础设施。
- `IamLdapLoginEndToEndTest`：真实 openldap + MySQL + Redis 的登录 E2E，复用 `smart-middleware-ops-server-starter` 的固定 LDAP 容器。

## Spring Boot 兼容性

基于 Spring Boot 2.7.9 / Spring Security 5.8.2 / javax 基线开发，与 `simple-iam-server-starter` 版本矩阵一致。
