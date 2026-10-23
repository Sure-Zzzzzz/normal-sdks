# Envoy 数据面设计

Envoy 是开源的七层代理（能够理解 HTTP 路径、方法和上游连接的网络代理）。在 IAM Portal Gateway 中，Envoy 只执行 adaptor 发布的 xDS v3 配置、连接治理和受控健康检查；它不读取 IAM、不保存用户会话、不包含业务代码。

本目录是 Envoy 数据面及部署运行目录的目标语义和安全边界文档。xDS 控制面属于 adaptor starter：ADS/SotW gRPC、资源编码、Envoy ACK/NACK 和快照状态由 adaptor 在进程内提供；运行目录只负责启动 adaptor、Envoy、bootstrap 和网络，不能替代 adaptor 提供 xDS。业务应用连接（Envoy 术语中的 HTTP upstream）只使用 HTTP，不挂载 CA 或证书。

相关文档：[`动态路由.md`](动态路由.md) 描述动态路由和 ACK/NACK；[`HTTP链路.md`](HTTP链路.md) 描述入口后 HTTP 链路；[`TLS入口链路.md`](TLS入口链路.md) 描述外层 TLS 入口及其后的统一链路；[`安全策略.md`](安全策略.md) 描述安全策略。

IAM Nginx 位于 Envoy 之前，是统一入口层。`/`、Portal 壳、`/app/iam/**`、AKSK 固定页面/API、`/iam/**` 和 `/oauth2/**` 等平台保留路径由 IAM Nginx/IAM Server 自闭环；未命中平台保留入口的业务页面/API 请求经通用入口交给 Envoy。IAM Nginx 不逐应用配置路由或业务应用连接；Envoy 只接收已登记且不占用保留前缀的业务可信应用路由。IAM 即使出现在可信应用目录中，其平台 Web 也不进入 Envoy。

## 动态配置

adaptor 通过 xDS（控制面向 Envoy 下发配置的标准接口）v3 ADS/SotW gRPC 流发布四类资源：LDS 监听器 `portal-gateway-listener`、RDS 路由 `portal-gateway-routes`，以及按 `portal-cluster-{HMAC}` 命名的一组 CDS 上游集群和同名 EDS 固定 IP 端点。Envoy 对每类资源返回 ACK（确认）或 NACK（拒绝）；只有同一 ADS stream 对当前版本四类资源全部 ACK 后，该 stream 才算完成应用。Control Plane cache 立即持有最新候选快照，不会因 NACK 自动回滚；readiness 由至少一个 stream 完整确认触发，不代表全部 Envoy 副本都已收敛。

EDS 使用 adaptor 解析并通过网段策略检查的 IP；Envoy 不使用 `STRICT_DNS` 重新解析登记域名。HTTP Host/`:authority` 使用登记主机，外层入口主机只放入受控的 `X-Forwarded-Host`。

## 请求和响应边界

路由公开前缀由 IAM 投影返回的 `routePrefix`（页面/静态资源）与 `apiBase`（API）生成；`entry` 只是 Portal 初始文档路径，必须属于 `routePrefix`，不单独生成路由。每个应用只登记一个业务自有 Nginx `targetOrigin`，两类公共路径都透明转发到该 origin。业务 Nginx 自己负责静态页面、API 入口及内部服务分发；IAM 不保存业务内部拓扑，Envoy 不拆出 Web/API 两个上游。未知路径返回不含上游细节的 404；无有效 xDS 快照、集群不可用或控制面未就绪返回统一 503。Envoy 不主动请求任意业务首页作为健康检查；业务主动健康检查必须由运行目录为明确登记的无副作用路径单独配置，不能改变 IAM readiness。

入口先清除客户端自带的 `X-Forwarded-For`、`X-Forwarded-Proto`、`X-Forwarded-Host` 和 `X-Request-ID`，再由受控入口生成或追加。Envoy 按配置的可信跳数重写这些头，并生成 UUID 格式的 `X-Request-ID`。Authorization 按 IAM 路由投影处理：`REQUEST` 时原样转发浏览器请求头，`NONE` 时移除；IAM 1.3.8 当前固定投影 `REQUEST`，供业务自有 Nginx/API 验证。Cookie 始终移除，当前 starter 对非空 Cookie 白名单拒绝发布；Envoy 不生成、解析或记录凭据。

## 镜像和部署

入口后 HTTP 链路和外层 TLS 卸载使用同一个固定的官方 Envoy 镜像：`envoyproxy/envoy:v1.39.4`。部署运行目录不得使用 `latest`、大版本标签或未经记录的本地重打标签；部署清单按目标架构锁定 digest：Linux `amd64` 为 `sha256:63dd445d23d7d419d1bba8b2b9b1b8fdb80f38ae1516fb5d2dcd8856713b7eac`，Linux `arm64` 为 `sha256:ce82b213b2c9190aa9fa2fc76678743a690681cbd668de32dd749e3e4da52862`。官方源码标签 `v1.39.4` 对应提交 `72315c132d462e5126e492ab2a71057565dbc87a`，xDS v3 的 LDS/RDS/CDS/EDS 能力以该版本为基线验证。

`v1.39.4` 不是当前最新标签，选择它是稳定线取舍，不代表没有安全漏洞。应在 GA/infra 侧按实际 digest 留存扫描器、漏洞库版本、扫描时间和 Critical/High 结果；扫描未完成时只标记扫描状态未完成，不能称为“无漏洞”。按当前没有替代产品的决策，CVE 记录是风险告警和处置跟踪，不自动阻断本版本的受控测试、部署或发版；明确可利用且无法缓解的风险由授权人单独决定是否暂停。自研 Gateway 作为长期替代路线记录，不作为本镜像当前交付的前置条件。

入口侧 TLS 由防火墙、硬负载或 IAM Nginx 管理，Envoy 不持有入口私钥。IAM Nginx 到 Envoy、Envoy 到业务可信应用均为 HTTP；不支持 HTTPS upstream，也不挂载 CA 或应用证书。

Envoy xDS gRPC 端口只绑定网关控制面区并由网络 ACL 限制，不启用 TLS/mTLS。Envoy admin 端口（配置诊断接口）只绑定 loopback 或管理网，禁止入口侧暴露，关闭不需要的 `/config_dump`、`/clusters` 远程访问。

## 无状态要求

Envoy 不保存会话、Token、目录或业务数据。多个 Envoy 副本由外部负载均衡分发请求，每个副本连接任意健康 adaptor xDS 服务；副本之间不共享本地文件状态。每个副本必须使用唯一且稳定的 `node.id`，部署验收应逐实例检查 Envoy readiness；adaptor readiness 不代表所有副本已收敛。仓库外运行目录的 `bootstrap.yaml` 是单 Envoy 示例，生产扩展时需为每个实例配置不同 `node.id`。
