# HTTP 链路

本文描述统一入口之后的 HTTP 链路。统一入口本身可以接收 HTTP，也可以在入口前由防火墙、硬负载或 IAM Nginx 终止 TLS；这不改变入口后的职责边界。IAM 自有路径由 IAM 自闭环，业务可信应用经 Envoy 以 HTTP 连接到应用自有 Nginx。Gateway 只接受 HTTP upstream，不挂载证书或 CA。

```text
浏览器 --HTTPS（可选）--> IAM Nginx（TLS 在此终止）
                              ├-- HTTP --> IAM Web / IAM Server
                              └-- HTTP --> Envoy --HTTP--> 已登记可信应用
                                              ^
                                              | HTTP/2 gRPC（xDS，网关控制面区）
                                            adaptor
```

这条链路是唯一的业务访问链路。网络边界按职责划分为四类：统一入口区承载入口代理或 IAM Nginx；网关数据面区承载 Envoy；网关控制面区承载 adaptor 到 Envoy 的 xDS 连接；业务应用接入区承载各可信应用自己的 Nginx 或服务。统一入口区只连接 IAM 自有服务和网关数据面区，Envoy 连接统一入口区与业务应用接入区，控制面连接只允许 Envoy 访问 adaptor，业务应用不直接加入统一入口区。IAM Nginx 不连接业务应用接入区，业务 Nginx 不连接统一入口区，业务 Server 不直接暴露给统一入口。实现上可以使用不同的网络或等效的 ACL/防火墙边界；运行目录可以自行命名网络和服务，但这些实现名称不构成产品规范。Envoy、adaptor 和业务 Server 的宿主端口不作为访问入口；
本机诊断端口即使保留，也只能绑定 `127.0.0.1`，不能绑定所有宿主地址。

## 配置边界

- Envoy listener、adaptor xDS 和 health endpoint 使用明确的入口后 HTTP 地址；xDS listener 只允许网关控制面区的 Envoy 访问，并由网络 ACL 限制。Compose 服务使用稳定的服务名或内部负载均衡地址，不能依赖固定副本容器名，才能支持无状态扩容。
- IAM Nginx 先处理 `/`、Portal 壳、`/app/iam/**`、`/app/aksk/**`、`/iam/**`、`/oauth2/**`，以及 AKSK 固定 API `/api/client/**`、`/api/me/aksk-clients/**`、`/api/token/**`、`/api/application-authorization/**`；未命中这些保留入口的业务页面/API 请求经通用入口转发给 Envoy。IAM Nginx 不维护逐应用 `routePrefix`、`apiBase` 或业务应用连接。
- IAM 可信应用路由投影中的 `targetOrigin` 必须是 HTTP；公共 `entry`/`apiBase` 只能填写路径，不能填 URL 或业务连接地址。`entry` 必须等于或位于 `routePrefix` 下；`apiBase` 是独立的公共 API 路径。两类路由共用一个 `targetOrigin`，HTTPS upstream 由 adaptor 拒绝。`targetOrigin` 仍通过端口、解析地址和网段策略校验，通过校验的 IP 由 EDS（端点发现服务）固定下发。
- Envoy 不配置 TLS listener、HTTPS upstream、SDS（Secret Discovery Service，动态证书下发）或任何证书/CA 挂载；入口前是否使用 TLS 不属于本链路的配置范围。
- 不因 HTTP-only 关闭 AKP 机器认证、目标校验、请求边界或 Header 白名单。
- Envoy admin 端口只绑定 loopback/管理网，不向统一入口层暴露；adaptor readiness、Envoy data plane 和业务 Server 也不得用宿主全地址映射绕过 IAM Nginx。

## 验收边界

1. adaptor readiness 要求 Portal 机器投影可用，并至少有一个 ADS stream 对最新版本 CDS、EDS、RDS、LDS 全部 ACK；这不代表所有 Envoy 副本都已收敛。多 Envoy 部署需逐实例检查数据面 readiness。
2. Envoy 按投影的 `routePrefix` 匹配页面和静态资源、按 `apiBase` 匹配 API；平台保留路径由 IAM Nginx 直接提供，未知路径为 404，无快照为 503。
3. 页面和 API 保留各自公共路径并到达同一个业务 Nginx `targetOrigin`；业务 Nginx 再自行处理静态文件和 API 内部分发，不拆分页面和 API 上游。
4. 新登记或撤销应用在完整目录同步后写入 Control Plane cache，再由 Envoy 按资源类型分别应用；NACK 不触发 Control Plane 自动回滚，也不保证跨资源类型事务性切换。登记为 HTTPS 的 upstream 不产生有效快照。
5. HTTP 日志、事件和响应不包含令牌、Cookie、密码、私钥、完整 Query、Header 或 Body；客户端提供的转发头先被清理，入口重新生成受控的 `X-Forwarded-*` 和 `X-Request-ID`，Envoy 只向上游发送允许列表中的转发头。
