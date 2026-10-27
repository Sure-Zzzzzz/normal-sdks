# 产品反馈-Feedback

统一门户环境下的通用产品反馈服务：任意已登记应用的门户用户提交反馈（满意度打分/文本/选填邮箱/截图），应用管理侧按 DATA 应用范围查看与处理并答复，提交人在"我的反馈"查看回复。REST OpenAPI 1.0.1 同时支持 PKCE 人员令牌和 AKSK 服务令牌，服务端按主体类型归一 `iam:`/`aksk:` owner，并继续执行 API 与 DATA 三权。

## 分层模块表

| 模块 | 坐标 | 版本 | 一句话 |
|---|---|---|---|
| 领域层 | `io.github.sure-zzzzzz:simple-feedback-server-core` | 1.0.1 | 模型/三态状态机/校验/错误码/出站 SPI（纯 Java 8） |
| 装配与 REST | `io.github.sure-zzzzzz:simple-feedback-server-starter` | 1.0.1 | 三表 MySQL+S3 附件+三权协作+幂等快照+双档频控，13 个 REST 操作 |
| IAM 适配件 | `io.github.sure-zzzzzz:simple-feedback-iam-adaptor-starter` | 1.0.1 | hint 聚形态（iam-client 1.0.3 页面准入详情+邮箱预填+提交人展示名快照）与注册表目录核对 |
| 机器调用契约 | `io.github.sure-zzzzzz:simple-feedback-client-core` | 1.0.1 | 公开请求、响应和错误分类，不含 HTTP、认证或 Spring |
| javax 认证传输 | `io.github.sure-zzzzzz:simple-feedback-aksk-resttemplate-client-starter` | 1.0.1 | 复用 AKSK `akskClientRestTemplate` 调用 13 个 REST 操作 |
| Web | `github.com/Sure-Zzzzzz/simple-feedback-web` | 1.0.0 | 独立前端仓（四页面 qiankun 子应用，PKCE+主题契约组件；dist/容器镜像交付） |

模块 README：[server-core](../../sdk/feedback/simple-feedback-server-core/README.md) / [server-starter](../../sdk/feedback/simple-feedback-server-starter/README.md) / [adaptor](../../sdk/feedback/simple-feedback-iam-adaptor-starter/README.md) / [client-core](../../sdk/feedback/client/simple-feedback-client-core/README.md) / [RestTemplate client](../../sdk/feedback/client/simple-feedback-aksk-resttemplate-client-starter/README.md)；REST 契约 [openapi](../../sdk/feedback/contract/openapi/simple-feedback-server.openapi.yaml)。

## 依赖关系

- starter → `simple-feedback-server-core:1.0.1`（api 坐标，固定发布坐标）、`simple-application-authorization-core:1.0.1`+`simple-data-permission-core:1.2.0`（协议层最小集，均 api：宿主注解代码免声明即可编译）+`simple-resource-server-core:1.1.1`（compileOnly，桥在场权归宿主）、`simple-mysql-route-starter:1.1.1`+`s3-client-starter:2.1.0`（部署契约）；数据面 MVC 强制件由宿主自引。
- 协作装配件（`simple-resource-server-starter`+`simple-iam-resource-server-starter`）由宿主自引：引即全链验证 Bearer（聚），不引则宿主自备 `FeedbackPrincipalResolver`（散）；双缺席拒启。
- adaptor → `simple-iam-client-core:1.0.3`（公开契约）；宿主另引 `simple-iam-aksk-resttemplate-client-starter:1.0.2` 作为认证传输（适配器不传递具体传输实现）。宿主持 AKP 出站（`iam:user:api`+`iam:portal:api`+`iam:application:api` 三码，iam:user/iam:application DATA read all，不给 PAGE）。
- 机器调用方 → `simple-feedback-client-core:1.0.1` + `simple-feedback-aksk-resttemplate-client-starter:1.0.1`；传输件只依赖稳定 Feedback 契约和 AKSK 认证传输，不引 server 内部 DTO 或共库。Feign 与 Spring Boot 3/Jakarta 传输件不在 1.0.1 范围内。
- 被依赖：无（叶子业务服务）。

## Gateway 部署边界

Feedback 的运行拓扑是“业务 Nginx + Server”两层：IAM Nginx 负责统一入口，Envoy 负责可信应用的动态路由，Feedback 自有 Nginx 是唯一 `targetOrigin`，Java runtime 只在业务内部网络监听 HTTP 8393。业务 Nginx 同时提供 `/app/feedback/` 静态页面并将 `/api/feedback/` 反代到 runtime，Bearer `Authorization` 原样转发给 Feedback Resource Server。

Gateway 保留 `routePrefix=/app/feedback` 和 `apiBase=/api/feedback` 的公共路径，不把前缀改写后再交给业务服务；不能把 runtime 8393、Envoy 宿主诊断端口或 IAM Nginx 作为 Feedback 的 `targetOrigin`。runtime 的 Compose 只装配 Feedback Server 和业务 Nginx，不启动 Gateway/Envoy，也不发布 Server 宿主端口。

## 核心特性

- **三权**：8 API 码+4 PAGE 码+2 DATA 资源（`feedback:submission`/`feedback:application`，维度 application IN）；注册表创建同样受 DATA 范围约束，读列表范围进 SQL，范围外一律安全 404/空集。
- **PAGE 深链**：Portal 将当前用户裁剪后的 `menuTree` 转成 qiankun `pagePermissions` 传入，Feedback 路由守卫按页面再次校验；撤掉 PAGE 后菜单隐藏且直接访问页面地址进入无权页，API 403 不是唯一防线。
- **幂等**：提交收 `Idempotency-Key`（前端表单会话级）；摘要覆盖主体+contextPath+附件元数据；并发同键唯一键收敛；首成功响应快照随行持久化，处理过后重放仍返回首成功形态。
- **边界**：同 owner 双档计数窗频控由 Feedback 自身数据库查询承载，用于业务防滥用；本版本不对外承诺可替换的动态频控策略。附件开关/数量/单件/总量/类型全部走运行配置；分页上限截断生效。
- **聚散**：见依赖关系——附件、adaptor、协作件均可缺席成合法形态，由依赖表达。
- **数据来源与同源锁**：注册表行级 `source` 徽标（IAM 同源/本表登记/未核对）；同源行展示名登记时自动抄录 IAM 官方名、编辑被拒（409），`syncFromIam=true` 显式重同步；受理门（启用/停用）与处理组始终本表自主。提交人 displayName 随行快照（聚形态经档案 SPI，降级回退裸 subjectId 展示）。

## 版本映射

| server-starter | server-core | adaptor | iam-client | client-core | RestTemplate client | Web | REST OpenAPI |
|---|---|---|---|---|---|---|---|
| 1.0.0 | 1.0.0 | 1.0.0 | 1.0.3 | — | — | 1.0.0 | 1.0.0 |
| 1.0.1 | 1.0.1 | 1.0.1 | 1.0.3 | 1.0.1 | 1.0.1 | 1.0.0 | 1.0.1 |

Feign 和 Spring Boot 3/Jakarta 客户端传输件未纳入上述版本映射。
