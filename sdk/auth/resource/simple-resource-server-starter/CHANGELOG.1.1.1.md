# simple-resource-server-starter 1.1.1 变更记录

## 公共鉴权链 STATELESS 修复

- 公共 `/api/**` 鉴权链固定 `SessionCreationPolicy.STATELESS`：`ResourceServerSecurityConfigurer` 显式声明，
  不再继承应用级 session 策略。
- 修复前：链按默认策略可创建 session 并下发 `Set-Cookie`（JSESSIONID）——业务方同时挂 session 存储时，
  cookie 与 Bearer 头并存产生凭据载体歧义，出现间歇性 401。
- 修复后：`/api/**` 链零 `Set-Cookie` 下发；业务方无论是否部署 session 存储，Bearer 鉴权行为一致。

## 行为边界

- 唯一 Bearer 入口、载体歧义拒绝、`kid` 来源路由、Provider 认证编排、API/DATA 权限判定、统一 `401/403` 均不变。
- 仅收敛 session 策略这一项公共层行为；不新增 API、不改变依赖坐标（core 保持 `1.1.1`）。
- 依赖本 Starter 的资源服务若（错误地）依赖 `/api` 链上意外获得的 session，须自查改造——公共资源链本就承诺无状态。

## 兼容矩阵

已按完整模块测试验证（core 5 + starter 30 = 35 例 / 档，0 skipped / 0 failures / 0 errors）：

- Spring Boot `2.2.13.RELEASE`（gradle-7.6 + JDK 8）
- Spring Boot `2.3.12.RELEASE`（gradle-7.6 + JDK 8）
- Spring Boot `2.4.5`
- Spring Boot `2.7.9`

外部真实链路验证：

- `simple-aksk-resttemplate-redis-client-starter` 链路四版本矩阵全绿（AKSK 服务身份）。
- IAM × AKSK 协作验收（`simple-iam-aksk-collaboration-demo` collaborationAcceptance 五用例全绿）：
  IAM 人员身份（PKCE 授权码真 Token）与 AKSK 服务身份 Token 调用同一业务接口，
  行级 DATA 隔离、越界拒绝、伪造 `kid` 401、`/api` 链 `Set-Cookie` 零下发均实证。

## 升级说明

坐标升级即可，无配置迁移。建议所有同时使用 session 与本 Starter 的业务方升级，
消除 cookie/Bearer 载体歧义窗口。
