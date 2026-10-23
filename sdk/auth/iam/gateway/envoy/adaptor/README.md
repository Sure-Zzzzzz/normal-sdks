# IAM Portal Gateway Adaptor

Adaptor 是 Gateway 的控制面组件。它以 AKP（应用密钥对）调用 IAM 的 Portal 可信应用路由投影，校验业务应用连接（HTTP upstream）和请求边界，生成 xDS（动态配置协议）资源并在 starter 进程内提供 ADS/SotW（全量快照）gRPC，接收 Envoy ACK/NACK；Envoy 才负责实际 HTTP 转发。仓库外运行目录只负责启动 adaptor、Envoy 和连接配置，不承接 xDS 控制面。真实 Envoy 业务转发仍需单独联调验收。

Adaptor 不代理业务请求，不访问 IAM 数据库，不保存用户会话或 Token，不包含具体业务判断，也不替代 Portal 的人员 PAGE 准入和业务 Resource 的 API/DATA 授权。

IAM Nginx 是外部统一入口，先自闭环 `/`、Portal 壳、`/app/iam/**`、`/app/aksk/**`、`/iam/**`、`/oauth2/**`，以及 AKSK 固定 API `/api/client/**`、`/api/me/aksk-clients/**`、`/api/token/**`、`/api/application-authorization/**`；Adaptor/Envoy 只处理已登记且不占用平台保留前缀的业务可信应用路由。IAM 与 AKSK 可出现在可信应用目录中，但平台自有 Web 不进入 xDS。

## 模块

| 模块 | 责任 | 依赖边界 |
| --- | --- | --- |
| `simple-iam-portal-gateway-adaptor-core` | 路由模型、地址策略、解析结果、快照和事件契约 | Java 8 纯 Java，不依赖 Spring、IAM Server 或 Envoy |
| `simple-iam-portal-gateway-adaptor-starter` | IAM 目录同步、快照协调、xDS 端口、健康检查和装配 | Spring Boot 2.7.9 / Java 8；依赖 core 和 IAM RestTemplate 传输线 |

## IAM Client 边界

IAM 已有的 `IamApplicationClient#listApplications`（`GET /iam/api/applications`）是既有目录消费者使用的三字段目录，返回 `applicationCode`、`applicationName`、`status`，其模型、分页和权限保持不变。

Gateway 使用 Portal 契约版 `simple-iam-client-core:1.0.4` 中独立的 `IamPortalApplicationClient`，读取 `GET /iam/api/portal/applications`；该路由投影要求 IAM Server `1.3.8` 或更新版本。现有三字段目录基线仍为 core 1.0.3。四个 SB2/Jakarta RestTemplate 与 Feign transport starter 使用 `1.0.3`，只负责传输实现并复用该 core 契约。投影包含全局 `catalogRevision`、稳定分页，以及 `status`、`portalEnabled`、`routePrefix`、公共 `entry`、公共 `apiBase`、业务 Nginx 的 `targetOrigin`、`configVersion`、`forwardAuthorization` 和 `forwardCookieNames`。页面和 API 共用一个 `targetOrigin`，不拆分业务 Web/Server 上游。调用 AKP 必须是已验证的 `SERVICE` 主体，并同时满足应用准入、`iam:portal-application:read:api` 和 `iam:portal-application/read` DATA 范围；`HUMAN` 主体即使有同名权限也拒绝。不能用用户 Cookie、用户态可见应用接口、数据库旁路或三字段目录补齐路由。运行时使用兼容的 core 1.0.4 与 transport 1.0.3；任一坐标不可用时 adaptor 保持未就绪。

该 Portal 投影必须与 IAM Server OpenAPI、API/DATA 权限、client-core 和 RestTemplate 实现同一契约批次提供；投影缺失或权限失败时 adaptor 保持未就绪，不伪造自动发现。IAM 宿主按 `/iam/api/**` 既有 Resource Server 形态提供 AKSK 验证和三权校验；adaptor 不复制 Token introspection 或 Resource 业务实现。

## 运行原则

- 只接受投影返回的 `routePrefix`，不按 applicationCode 重算；页面公开前缀使用 `routePrefix`，API 公开前缀使用 `apiBase`，`entry` 是 Portal 打开的初始文档路径并须位于 `routePrefix` 下；机器目标统一使用同一个 `targetOrigin`。
- 设计目标：通过策略的解析 IP 以 EDS 固定下发，Envoy 不使用 `STRICT_DNS`；HTTP Host/`:authority` 保留目标主机名，不生成 SNI 或 TLS upstream 配置。
- 当前实现：`XdsSnapshotService` 在 starter 内监听 ADS/SotW，编码 CDS/EDS/RDS/LDS，并按同一 stream、资源类型和版本关联 ACK/NACK；单个 stream 完整 ACK 最新版本才满足 adaptor readiness，不代表其他 Envoy 副本已收敛。NACK 不回滚 Control Plane cache。
- 真实 Envoy 业务转发仍须由部署运行目录验收，不能以 starter 测试代替。
- IAM 明确撤销时立即撤路；依赖短暂不可用只在 `maxStale` 窗口内保留旧快照，过期后未就绪。
- adaptor 副本只保留内存快照，重启后重新读取；不使用本地数据库、Leader Election 或业务消息队列。
- 日志、事件和摘要只使用脱敏值。IAM Server 1.3.8 固定投影 `forwardAuthorization=REQUEST` 和空 Cookie 白名单；starter 按策略原样转发 Authorization，使业务 Nginx/API 可验证调用方凭据，但不会生成、解析或记录凭据值。通用策略 `NONE` 会移除 Authorization。Cookie 始终移除，非空 Cookie 白名单会拒绝发布快照。

Gateway 部署运行目录独立消费已发布 adaptor 制品和固定的 `envoyproxy/envoy:v1.39.4` 镜像（部署清单另锁 digest），负责进程编排、Envoy bootstrap 和网络；业务应用连接只支持 HTTP，不挂载 CA 或证书。它不实现 xDS，也不把 IAM Server、业务容器、源码组合或凭据加入 SDK 发布物。
