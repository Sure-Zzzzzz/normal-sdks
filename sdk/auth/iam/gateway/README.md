# IAM Portal Gateway

IAM Portal Gateway 为接入身份协作底座的可信应用提供统一 Portal 入口和动态 HTTP 转发。Adaptor 以 AKP（应用密钥对）调用 IAM 的 Portal 可信应用路由投影，校验目标后向 Envoy 发布 xDS（动态配置协议）v3 ADS/SotW（全量快照）路由；Envoy 只负责数据面转发。

当前 normal-sdks 包含 adaptor 的 Java 8 core、Spring Boot 2.7.9 starter，以及由 starter 在进程内提供的 ADS/SotW gRPC、Envoy ACK/NACK 闸门和 xDS 资源编码。Gateway 只接受 HTTP 业务应用连接（Envoy upstream），不挂载 CA 或业务证书。仓库外的独立部署运行目录（下文简称“运行目录”）负责装配 starter、启动 Envoy 数据面和提供连接配置；它不实现或替代 xDS。真实业务转发和入口侧协议仍需在运行目录中单独验收。

## 目录

| 目录 | 内容 | 交付形态 |
| --- | --- | --- |
| `adaptor/` | Java 8 core 契约和 Spring Boot 2.7.9 starter | 发布到 Maven |
| `envoy/` | Envoy 动态路由、安全边界、HTTP 链路和 TLS 入口链路手册 | 文档 |
| 仓库外运行目录 | adaptor starter 运行宿主、Envoy Compose、镜像、环境和 bootstrap | 独立部署目录，不进入 normal-sdks；只装配 adaptor，不实现 xDS 控制面 |

## 文档导航

| 文档 | 说明 |
| --- | --- |
| [`envoy/README.md`](envoy/README.md) | Envoy 数据面、xDS、镜像基线和无状态要求 |
| [`envoy/动态路由.md`](envoy/动态路由.md) | 动态路由字段、路径规则和 ACK/NACK 语义 |
| [`envoy/HTTP链路.md`](envoy/HTTP链路.md) | 入口后 HTTP 链路的边界和验收 |
| [`envoy/TLS入口链路.md`](envoy/TLS入口链路.md) | 外层 TLS 入口与入口后链路的边界和验收 |
| [`envoy/安全策略.md`](envoy/安全策略.md) | 控制面、目标地址、请求头和凭据边界 |
| [`envoy/adaptor/README.md`](envoy/adaptor/README.md) | adaptor SDK 模块职责、依赖和运行边界 |

## Portal 可信应用路由投影契约

IAM 现有 `GET /iam/api/applications` 由 `IamApplicationClient#listApplications` 提供，返回 `applicationCode`、`applicationName`、`status` 三字段；既有目录消费者继续使用该契约，Gateway 不改变它。

Gateway 使用 Portal 契约版 `simple-iam-client-core:1.0.4` 中独立的 `IamPortalApplicationClient`，读取 `GET /iam/api/portal/applications`；现有三字段目录基线仍为 core 1.0.3。Portal 路由投影要求 IAM Server `1.3.8` 或更新版本。响应携带全局 `catalogRevision`、稳定分页和每个应用的 `status`、`portalEnabled`、`routePrefix`、公共 `entry`、公共 `apiBase`、业务 Nginx 的 `targetOrigin`、`configVersion`、`forwardAuthorization`、`forwardCookieNames`。页面和 API 共用一个业务 Nginx origin；只返回 wire `status=1`（启用）且 `portalEnabled=true` 的应用；禁用或删除递增 revision 并从下一份目录中消失。运行时必须使用兼容的 core 1.0.4。

这里不新建独立的 Portal client 模块：`IamPortalApplicationClient` 放入现有 `simple-iam-client-core:1.0.4`，SB2/Jakarta RestTemplate 与 Feign 实现分别放入现有四个 transport starter `1.0.3`，继续复用现有 AKSK 传输底座和错误映射。不复制 HTTP 模型或认证逻辑。

IAM Server 1.3.8 已在 `simple-iam-open-api.openapi.yaml` 提供并实现 `GET /iam/api/portal/applications`。该无用户会话的机器接口返回经过 API + DATA 授权的 Portal 路由投影，并以 `catalogRevision` 保证分页一致性；旧 `GET /iam/api/applications` 三字段目录契约保持不变。Adaptor 通过现有 client core 与传输 starter 消费该契约，不访问管理面接口或数据库。

调用 AKP 必须是已验证的 `SERVICE` 主体，并具备应用准入、`iam:portal-application:read:api` 和 `iam:portal-application/read` DATA 范围；`HUMAN` 主体即使有同名权限也拒绝。该投影不能用用户 Cookie、用户态 Portal 接口、数据库旁路或静态业务配置替代；投影缺失时 adaptor 未就绪。

IAM 宿主必须按 `/iam/api/**` 的既有 Resource Server 形态装配 AKSK 验证和三权校验；Gateway adaptor 不直接承担 IAM 的 Token introspection，也不把 Resource starter 作为业务实现复制进来。

## 入口与路由层次

```text
浏览器 -> IAM Nginx
           -> IAM 平台保留路径（IAM Web、Portal 壳、OAuth）
           -> 业务可信应用公共路径 -> Envoy -> 业务自己的 Nginx
```

IAM Nginx 是外部统一入口，负责 TLS 卸载、登录、Portal 壳、IAM Web 和 OAuth。`/`、Portal 壳、`/app/iam/**`、`/iam/**` 和 `/oauth2/**` 由 IAM Nginx/IAM Server 自闭环；只有已登记且不占用这些平台保留前缀的业务可信应用页面和 API 才转发到 Envoy。IAM 可以出现在可信应用目录中，但其平台 Web 不进入 Envoy。

`routePrefix` 由 IAM 返回并原样使用，普通应用通常是 `/app/{code}`，历史内置项可能是 `/micro/{code}`。`entry` 和 `apiBase` 都是统一入口下的公共路径，`targetOrigin` 是业务自有 Nginx 的无路径 HTTP origin；页面和 API 均透明转发到同一个 `targetOrigin`。目标 origin 必须经过端口、DNS 和网段策略，解析通过的 IP 以 EDS 固定下发；HTTPS upstream 会被拒绝。

入口侧 TLS 只在防火墙、硬负载或 IAM Nginx 终止；IAM Nginx 向 Envoy、Envoy 向可信应用均使用 HTTP，Gateway 不挂 CA、不处理 HTTPS upstream，也不持有入口私钥。动态路由按 IAM 投影的 `forwardAuthorization` 原样转发或移除入站 Authorization；IAM 1.3.8 当前固定投影 `REQUEST`，使业务自有 Nginx/API 能验证调用方 Bearer。Gateway 不生成、验证或记录该凭据。Cookie 始终移除，业务应用自行完成认证和 PAGE/API/DATA 授权。

Adaptor 保留内存中的最后有效快照，依赖故障只在 `maxStale` 窗口内继续服务；超过窗口未就绪。真实部署由 adaptor starter 在进程内提供受控 xDS gRPC 控制面，运行目录只负责把该端口和 Envoy 连接起来；入口后 HTTP 与 xDS 端口仅能在受控网络访问并由网络 ACL 限制。

Envoy 镜像版本、架构摘要、CVE 记录和部署安全边界统一维护在 [`envoy/README.md`](envoy/README.md)，本总览不重复维护同一份基线。
