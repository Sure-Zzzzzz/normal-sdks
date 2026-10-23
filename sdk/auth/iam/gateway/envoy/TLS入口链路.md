# TLS 入口链路

本文描述入口侧使用 TLS 时的协议边界。TLS 只保护客户端到最外层统一入口这一段，可在防火墙、硬负载或 IAM Nginx 终止；终止之后沿用统一的入口后 HTTP 链路。IAM 自有请求进入 IAM Web/Server，业务可信应用经 Envoy 以 HTTP 连接到应用自有 Nginx。Gateway 不支持 HTTPS upstream，也不需要 CA 文件。

```text
HTTPS 客户端
    -> IAM Nginx（证书和 TLS 在此终止）
       ├-> HTTP -> IAM Web / IAM Server（平台路径自闭环）
       └-> HTTP -> Envoy -> HTTP -> 已登记可信应用
```

入口侧 TLS 只改变客户端到统一入口的一段协议，不改变后面的职责边界。IAM Nginx
不能直接连接业务 Nginx；业务流量必须经过 Envoy。统一入口区、网关数据面区、网关控制面区
和业务应用接入区分别按职责隔离；业务 Nginx 只接受来自 Envoy 的受控连接，不能通过宿主端口或
统一入口区绕过 Envoy。可以用独立网络实现隔离，也可以用等效的 ACL/防火墙策略实现，不把
具体网络名称当作部署协议的一部分。

## 外层入口要求

- 入口先删除客户端自带的 `X-Forwarded-Proto`、`X-Forwarded-Host`、`X-Forwarded-For` 和 `X-Request-ID`，再写入真实值；Envoy 只信任明确配置的入口跳数，并拒绝未列入转发允许列表的内部 `X-*` 头。
- IAM Nginx 直接闭环 `/`、Portal 壳、`/app/iam/**`、`/app/aksk/**`、`/iam/**`、`/oauth2/**`，以及 AKSK 固定 API `/api/client/**`、`/api/me/aksk-clients/**`、`/api/token/**`、`/api/application-authorization/**`；只有已登记且不占用平台保留前缀的业务可信应用路径进入 Envoy。
- Envoy 向 HTTP upstream 使用登记目标的 Host/`:authority`，使用统一入口生成的 `X-Forwarded-Host` 表示入口主机名；上游应用不能把客户端自带转发头当作可信事实。
- OAuth（授权协议）issuer 和 redirect URI 使用调用方可访问的 HTTPS 地址，不能把入口后的 HTTP 地址登记为回调地址。
- 统一入口层只提供通用 Gateway 转发，不写业务名称、业务应用连接或业务凭据；adaptor 与 IAM 的机器调用使用独立受控配置。业务 Server 不发布宿主端口，健康检查不能从统一入口外绕过 Envoy。
- xDS gRPC 控制面只在网关控制面区开放，并由网络 ACL 限制 Envoy 访问；控制面不启用 TLS/mTLS。Envoy admin 端口只绑定 loopback/管理网。

业务可信应用必须提供可被 Envoy 访问的 HTTP Nginx origin。登记的 `targetOrigin` 使用 HTTPS 时，adaptor 拒绝发布路由快照；不会把 HTTPS 地址改写成 HTTP，也不会提供证书例外配置。`entry` 和 `apiBase` 只描述统一入口的公共页面/API 路径。

## 验收边界

验证入口侧 TLS 下的 IAM 自闭环路径、业务页面与 API 的入口后 HTTP 转发、转发头清理与重写、OAuth 可访问回调地址、网络隔离和 Envoy 无重启路由更新。入口私钥属于统一入口层，不属于 Gateway 或部署运行目录；Nginx 到 Envoy 及 Envoy 到应用均为 HTTP。不得用 Envoy admin 200、adaptor live 200 或业务容器内部 401 代替统一入口的真实页面/API 验收。
